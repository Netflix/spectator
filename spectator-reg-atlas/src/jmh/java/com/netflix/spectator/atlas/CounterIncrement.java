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
package com.netflix.spectator.atlas;

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Timer;
import com.netflix.spectator.impl.StepDouble;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Measures the {@code Counter.increment()} hot path for the Atlas registry. The layers are
 * benchmarked separately so that the cost of the clock reads, the {@code rollCount} division
 * and the CAS can be attributed rather than inferred from a single aggregate number.
 *
 * <p>Run the shared-counter benchmarks at {@code -t 1} and again at {@code -t N} to see whether
 * the single-field CAS in {@link StepDouble} is actually contended. {@code perThread} keeps the
 * code path identical but gives every thread its own counter, so it isolates path cost from
 * cache-line contention.</p>
 */
public class CounterIncrement {

  /** Shared state: one counter that every benchmark thread increments. */
  @State(Scope.Benchmark)
  public static class Shared {
    AtlasRegistry registry;
    Clock clock;

    /** The counter a user would hold: a SwapCounter wrapping an AtlasCounter. */
    Counter swapCounter;

    /** The same meter with the SwapMeter layer stripped off. */
    Counter atlasCounter;

    /** The step value underneath the meter. */
    StepDouble stepDouble;

    /**
     * A timer drives four step values per record (count, total, totalOfSquares, max), so it pays
     * the rollCount cost four times where a counter pays it once. Measured separately because the
     * counter path alone understates the change for timers.
     */
    Timer timer;

    /** Id used to measure the cost of resolving a meter from the registry. */
    Id lookupId;

    /** Fixed timestamp so stepDouble benchmarks measure rollCount + CAS with no clock read. */
    long now;

    /**
     * Step of 1ms driven by a timestamp that advances every call, so essentially every update
     * crosses a boundary. This is the adversarial case for caching the boundary: the fast path
     * never hits, and the slow path pays for the cached read on top of the division it still has
     * to do. Included to bound the downside rather than only measuring the favourable case.
     */
    StepDouble rolling;
    long rollingNow;

    @Setup
    public void setup() {
      // Not started: the publishing scheduler is irrelevant to the write path and would
      // otherwise add background work that shows up as benchmark noise.
      registry = new AtlasRegistry(Clock.SYSTEM, System::getProperty);
      clock = registry.clock();
      swapCounter = registry.counter("test.counter");
      atlasCounter = (Counter) registry.get(registry.createId("test.counter"));
      stepDouble = new StepDouble(0.0, Clock.SYSTEM, 5000L);
      now = Clock.SYSTEM.wallTime();
      rolling = new StepDouble(0.0, Clock.SYSTEM, 1L);
      rollingNow = Clock.SYSTEM.wallTime();
      timer = registry.timer("test.timer");
      lookupId = registry.createId("test.counter");
    }
  }

  /** Per-thread state: each thread gets its own counter, so the CAS is uncontended. */
  @State(Scope.Thread)
  public static class PerThread {
    private static final AtomicInteger NEXT = new AtomicInteger();

    Counter counter;

    @Setup
    public void setup(Shared shared) {
      counter = shared.registry.counter("test.perThread." + NEXT.getAndIncrement());
    }
  }

  /** The production path: increment through a held reference. */
  @Benchmark
  public void swapCounter(Shared shared) {
    shared.swapCounter.increment();
  }

  /** Same path without the SwapMeter indirection and its expiry check. */
  @Benchmark
  public void atlasCounter(Shared shared) {
    shared.atlasCounter.increment();
  }

  /** rollCount + CAS only, with the clock read hoisted out. */
  @Benchmark
  public double stepDouble(Shared shared) {
    return shared.stepDouble.addAndGet(shared.now, 1.0);
  }

  /**
   * Worst case for the boundary cache: a rollover on essentially every update, so the division
   * is still paid and the cached read is pure overhead. Single threaded only; this is about the
   * instruction cost of the slow path, not contention.
   */
  @Benchmark
  public double rollingStepDouble(Shared shared) {
    return shared.rolling.addAndGet(shared.rollingNow++, 1.0);
  }

  /**
   * Timer record through a held reference. Four step values per call, so four rollCounts.
   */
  @Benchmark
  public void timerRecord(Shared shared) {
    shared.timer.record(42L, TimeUnit.NANOSECONDS);
  }

  /**
   * Cost of resolving a meter from the registry. This is what a held reference pays once per
   * cleanup pass when the removal counter moves, so it sizes the re-resolution cost that the
   * version based approach trades the per update clock read for.
   */
  @Benchmark
  public Counter lookupCost(Shared shared) {
    return shared.registry.counter(shared.lookupId);
  }

  /** Cost of a single wall clock read, for scale. */
  @Benchmark
  public long wallTime(Shared shared) {
    return shared.clock.wallTime();
  }

  /** Production path, but with no cache line sharing between threads. */
  @Benchmark
  public void perThread(PerThread state) {
    state.counter.increment();
  }

  /**
   * Per-thread batch updater feeding the shared counter. This is the mitigation the library
   * already provides for a single very hot counter: it amortises the CAS over the batch instead
   * of striping every meter in the registry.
   */
  @State(Scope.Thread)
  public static class Batched {
    Counter.BatchUpdater updater;

    @Setup
    public void setup(Shared shared) {
      updater = shared.swapCounter.batchUpdater(1000);
    }

    @TearDown
    public void tearDown() throws Exception {
      updater.close();
    }
  }

  /** Shared counter, but updates are batched per thread. */
  @Benchmark
  public void batched(Batched state) {
    state.updater.increment();
  }
}
