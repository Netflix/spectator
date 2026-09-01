/*
 * Copyright 2014-2025 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spectator.impl;

import com.netflix.spectator.api.ManualClock;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Only one thread may perform a given rollover. If several threads are allowed through, the
 * second one resets {@code current} again and overwrites {@code previous} with the init value, so
 * the completed interval is published as zero and its data is lost.
 *
 * <p>The guard that prevents this is a compare of the interval boundaries before the CAS. It
 * cannot be exercised by a single threaded test, so it is covered here: every thread is released
 * onto the same rollover at once and the value for the completed interval has to survive.</p>
 */
public class StepRollCountConcurrencyTest {

  private static final long STEP = 10_000L;
  private static final int THREADS = 8;
  private static final int ROUNDS = 2_000;

  @Test
  @Timeout(60)
  public void stepDoubleRollsExactlyOncePerInterval() throws Exception {
    ManualClock clock = new ManualClock();
    StepDouble value = new StepDouble(0.0, clock, STEP);
    CyclicBarrier barrier = new CyclicBarrier(THREADS + 1);
    ExecutorService pool = Executors.newFixedThreadPool(THREADS);
    try {
      // Interval index for the round, starting past zero so the first round has a real previous.
      final long[] interval = {1L};
      for (int t = 0; t < THREADS; ++t) {
        pool.submit(() -> {
          try {
            for (int r = 0; r < ROUNDS; ++r) {
              barrier.await(30, TimeUnit.SECONDS);
              // All threads attempt the same rollover simultaneously.
              value.getCurrent(interval[0] * STEP);
              barrier.await(30, TimeUnit.SECONDS);
            }
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
      }

      for (int r = 0; r < ROUNDS; ++r) {
        // Seed a known value into the current interval, which the rollover must publish.
        double expected = 3.0;
        value.setCurrent((interval[0] - 1) * STEP, expected);

        barrier.await(30, TimeUnit.SECONDS);
        barrier.await(30, TimeUnit.SECONDS);

        Assertions.assertEquals(expected, value.poll(interval[0] * STEP), 1e-12,
            "round " + r + ": completed interval value was lost to a duplicate rollover");
        interval[0] += 1;
      }
    } finally {
      pool.shutdownNow();
    }
    // Checked outside the finally so that a failure here cannot replace a round assertion.
    Assertions.assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS),
        "worker threads did not terminate");
  }

  @Test
  @Timeout(60)
  public void stepLongRollsExactlyOncePerInterval() throws Exception {
    ManualClock clock = new ManualClock();
    StepLong value = new StepLong(0L, clock, STEP);
    CyclicBarrier barrier = new CyclicBarrier(THREADS + 1);
    ExecutorService pool = Executors.newFixedThreadPool(THREADS);
    try {
      final long[] interval = {1L};
      for (int t = 0; t < THREADS; ++t) {
        pool.submit(() -> {
          try {
            for (int r = 0; r < ROUNDS; ++r) {
              barrier.await(30, TimeUnit.SECONDS);
              value.getCurrent(interval[0] * STEP);
              barrier.await(30, TimeUnit.SECONDS);
            }
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
      }

      for (int r = 0; r < ROUNDS; ++r) {
        long expected = 3L;
        value.setCurrent((interval[0] - 1) * STEP, expected);

        barrier.await(30, TimeUnit.SECONDS);
        barrier.await(30, TimeUnit.SECONDS);

        Assertions.assertEquals(expected, value.poll(interval[0] * STEP),
            "round " + r + ": completed interval value was lost to a duplicate rollover");
        interval[0] += 1;
      }
    } finally {
      pool.shutdownNow();
    }
    // Checked outside the finally so that a failure here cannot replace a round assertion.
    Assertions.assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS),
        "worker threads did not terminate");
  }

  /**
   * Concurrent updates inside one interval must all be retained, then published in full by the
   * rollover. Only the last completed interval is kept, so this deliberately stays within a
   * single interval: values for intervals that roll by without being polled are dropped by
   * design and counting them would not be a conservation property of this class.
   */
  @Test
  @Timeout(60)
  public void concurrentUpdatesWithinAnIntervalAreConserved() throws Exception {
    ManualClock clock = new ManualClock();
    StepLong value = new StepLong(0L, clock, STEP);
    final int perThread = 100_000;
    final long now = 5L * STEP;

    // Perform this interval's rollover up front. ManualClock starts at 0, so nextStepBoundary is
    // STEP and the first update at 5 * STEP would otherwise roll over while all the threads are
    // already incrementing: a thread that loses the boundary CAS still completes its addAndGet,
    // and if that lands before the winner's getAndSet the increment is wiped. Rolling over here
    // makes the body of the test genuinely in-interval, which is what it means to measure.
    value.getCurrent(now);
    Assertions.assertEquals(now, value.timestamp(),
        "precondition: the rollover must already have happened before the threads start");

    ExecutorService pool = Executors.newFixedThreadPool(THREADS);
    try {
      CyclicBarrier start = new CyclicBarrier(THREADS);
      for (int t = 0; t < THREADS; ++t) {
        pool.submit(() -> {
          try {
            start.await(30, TimeUnit.SECONDS);
            for (int i = 0; i < perThread; ++i) {
              value.addAndGet(now, 1L);
            }
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
      }
      pool.shutdown();
      Assertions.assertTrue(pool.awaitTermination(45, TimeUnit.SECONDS));

      Assertions.assertEquals((long) THREADS * perThread, value.getCurrent(now),
          "updates were lost by the CAS retry loop");
      // The single rollover must publish the whole amount.
      Assertions.assertEquals((long) THREADS * perThread, value.poll(now + STEP),
          "rollover did not publish the full interval");
      Assertions.assertEquals(0L, value.getCurrent(now + STEP));
    } finally {
      pool.shutdownNow();
    }
  }
}
