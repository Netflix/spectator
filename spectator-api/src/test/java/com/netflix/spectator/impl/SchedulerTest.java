/*
 * Copyright 2014-2024 Netflix, Inc.
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

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.ManualClock;
import com.netflix.spectator.api.Registry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOError;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class SchedulerTest {

  @Test
  public void updateNextFixedDelay() {
    ManualClock clock = new ManualClock();
    Registry registry = new DefaultRegistry(clock);
    Counter skipped = registry.counter("skipped");

    Scheduler.Options options = new Scheduler.Options()
        .withFrequency(Scheduler.Policy.FIXED_DELAY, Duration.ofSeconds(10));

    clock.setWallTime(5437L);
    Scheduler.DelayedTask task = new Scheduler.DelayedTask(clock, options, () -> {});
    Assertions.assertEquals(5437L, task.getNextExecutionTime());
    Assertions.assertEquals(0L, skipped.count());

    clock.setWallTime(12123L);
    task.updateNextExecutionTime(skipped);
    Assertions.assertEquals(22123L, task.getNextExecutionTime());
    Assertions.assertEquals(0L, skipped.count());

    clock.setWallTime(27000L);
    task.updateNextExecutionTime(skipped);
    Assertions.assertEquals(37000L, task.getNextExecutionTime());
    Assertions.assertEquals(0L, skipped.count());
  }

  @Test
  public void updateNextFixedRateSkip() {
    ManualClock clock = new ManualClock();
    Registry registry = new DefaultRegistry(clock);
    Counter skipped = registry.counter("skipped");

    Scheduler.Options options = new Scheduler.Options()
        .withFrequency(Scheduler.Policy.FIXED_RATE_SKIP_IF_LONG, Duration.ofSeconds(10));

    clock.setWallTime(5437L);
    Scheduler.DelayedTask task = new Scheduler.DelayedTask(clock, options, () -> {});
    Assertions.assertEquals(5437L, task.getNextExecutionTime());
    Assertions.assertEquals(0L, skipped.count());

    clock.setWallTime(12123L);
    task.updateNextExecutionTime(skipped);
    Assertions.assertEquals(15437L, task.getNextExecutionTime());
    Assertions.assertEquals(0L, skipped.count());

    clock.setWallTime(27000L);
    task.updateNextExecutionTime(skipped);
    Assertions.assertEquals(35437L, task.getNextExecutionTime());
    Assertions.assertEquals(1L, skipped.count());

    clock.setWallTime(57000L);
    task.updateNextExecutionTime(skipped);
    Assertions.assertEquals(65437L, task.getNextExecutionTime());
    Assertions.assertEquals(3L, skipped.count());
  }

  private long numberOfThreads(String id) {
    return Thread.getAllStackTraces()
        .keySet()
        .stream()
        .filter(t -> t.getName().startsWith("spectator-" + id))
        .count();
  }

  @Test
  public void shutdownStopsThreads() throws Exception {
    Scheduler s = new Scheduler(new DefaultRegistry(), "shutdown", 1);

    // Schedule something to force it to start the threads
    Scheduler.Options opts = new Scheduler.Options()
        .withFrequency(Scheduler.Policy.FIXED_RATE_SKIP_IF_LONG, Duration.ofMillis(10))
        .withStopOnFailure(false);
    ScheduledFuture<?> f = s.schedule(opts, () -> {});
    Assertions.assertEquals(1L, numberOfThreads("shutdown"));

    // Shutdown and wait a bit, this gives the thread a chance to restart
    s.shutdown();
    Thread.sleep(300);
    Assertions.assertEquals(0L, numberOfThreads("shutdown"));
  }

  @Test
  public void stopOnFailureFalseThrowable() throws Exception {
    Scheduler s = new Scheduler(new DefaultRegistry(), "test", 1);

    Scheduler.Options opts = new Scheduler.Options()
        .withFrequency(Scheduler.Policy.FIXED_RATE_SKIP_IF_LONG, Duration.ofMillis(10))
        .withStopOnFailure(false);

    final CountDownLatch latch = new CountDownLatch(5);
    ScheduledFuture<?> f = s.schedule(opts, () -> {
      latch.countDown();
      throw new IOError(new RuntimeException("stop"));
    });

    Assertions.assertTrue(latch.await(60, TimeUnit.SECONDS));
    Assertions.assertFalse(f.isDone());
    s.shutdown();
  }

  @Test
  public void stopOnFailureFalse() throws Exception {
    Scheduler s = new Scheduler(new DefaultRegistry(), "test", 2);

    Scheduler.Options opts = new Scheduler.Options()
        .withFrequency(Scheduler.Policy.FIXED_DELAY, Duration.ofMillis(10))
        .withStopOnFailure(false);

    final CountDownLatch latch = new CountDownLatch(5);
    ScheduledFuture<?> f = s.schedule(opts, () -> {
      latch.countDown();
      throw new RuntimeException("stop");
    });

    Assertions.assertTrue(latch.await(60, TimeUnit.SECONDS));
    Assertions.assertFalse(f.isDone());
    s.shutdown();
  }

  @Test
  public void stopOnFailureTrue() throws Exception {
    Scheduler s = new Scheduler(new DefaultRegistry(), "test", 2);

    Scheduler.Options opts = new Scheduler.Options()
        .withFrequency(Scheduler.Policy.FIXED_DELAY, Duration.ofMillis(10))
        .withStopOnFailure(true);

    final CountDownLatch latch = new CountDownLatch(1);
    ScheduledFuture<?> f = s.schedule(opts, () -> {
      latch.countDown();
      throw new RuntimeException("stop");
    });

    Assertions.assertTrue(latch.await(60, TimeUnit.SECONDS));
    while (!f.isDone()); // This will be an endless loop if broken
    s.shutdown();
  }

  @Test
  public void cancel() throws Exception {
    Scheduler s = new Scheduler(new DefaultRegistry(), "test", 2);

    Scheduler.Options opts = new Scheduler.Options()
        .withFrequency(Scheduler.Policy.FIXED_DELAY, Duration.ofMillis(10))
        .withStopOnFailure(false);

    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<ScheduledFuture<?>> ref = new AtomicReference<>();
    ref.set(s.schedule(opts, () -> {
      try {
        while (ref.get() == null);
        ref.get().cancel(true);
        Thread.sleep(600000L);
      } catch (InterruptedException e) {
        latch.countDown();
      }
    }));

    Assertions.assertTrue(latch.await(60, TimeUnit.SECONDS));
    Assertions.assertTrue(ref.get().isDone());
    s.shutdown();
  }

  @Test
  public void threadsAreReplaced() throws Exception {
    Scheduler s = new Scheduler(new DefaultRegistry(), "test", 1);

    Scheduler.Options opts = new Scheduler.Options()
        .withFrequency(Scheduler.Policy.FIXED_DELAY, Duration.ofMillis(10))
        .withStopOnFailure(false);

    final CountDownLatch latch = new CountDownLatch(10);
    s.schedule(opts, () -> {
      latch.countDown();
      Thread.currentThread().interrupt();
    });

    Assertions.assertTrue(latch.await(60, TimeUnit.SECONDS));
    s.shutdown();
  }

}
