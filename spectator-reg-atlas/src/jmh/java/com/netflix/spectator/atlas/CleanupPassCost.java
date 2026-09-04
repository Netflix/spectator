/*
 * Copyright 2014-2026 Netflix, Inc.
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

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.ManualClock;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Measures what a cleanup pass costs the held references that survive it.
 *
 * <p>{@code CounterIncrement} never starts the registry and never removes a meter, so it only
 * measures the steady state where every update takes the cheap path. The question this one asks
 * is what a pass that removes <i>other</i> meters costs the references that survive it. A design
 * that signals removal globally makes every outstanding {@code SwapMeter} re-resolve, so that
 * cost scales with {@code removed x held}; one that signals it per meter should leave the
 * survivors alone.</p>
 *
 * <p>Reading the three arms: {@code sweepOnly} is the pass by itself, which is O(removed + held)
 * regardless of the design, and {@code updateHeldReferencesOnly} is the updates by themselves.
 * Neither alone answers the question, because the pass gets more expensive with {@code removed}
 * either way. What isolates the design is
 * {@code sweepThenUpdateHeldReferences - sweepOnly - updateHeldReferencesOnly}: the work the
 * updates do only because a pass ran in front of them. That difference should stay flat as
 * {@code removed} grows, and grow with it if the signal is global.</p>
 *
 * <p>{@code removed} is how many meters the pass expires and {@code held} is how many references
 * the application keeps updating across it. Note that {@code updateHeldReferencesOnly} does not
 * depend on {@code removed}, so its four parameter combinations collapse to two distinct
 * configurations.</p>
 *
 * <p>Run as single shot: the per-invocation setup builds a whole registry, and only
 * {@code SingleShotTime} keeps that out of the measured window.</p>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class CleanupPassCost {

  private static final long TTL = 15 * 60_000L;

  /** Meters that go idle and are removed by the pass. */
  @Param({"100", "1000"})
  public int removed;

  /** References the application holds and keeps updating across the pass. */
  @Param({"100", "1000"})
  public int held;

  private ManualClock clock;
  private AtlasRegistry registry;
  private List<Counter> heldCounters;

  @Setup(Level.Invocation)
  public void setup() {
    clock = new ManualClock();
    registry = new AtlasRegistry(clock, System::getProperty);

    // Meters that will be idle long enough to expire during the pass below.
    for (int i = 0; i < removed; ++i) {
      registry.counter("test.idle." + i).increment();
    }

    // Push everything created so far past the TTL.
    clock.setWallTime(TTL + 1);

    // References the application holds. Created after the clock move so they stay live.
    heldCounters = new ArrayList<>(held);
    for (int i = 0; i < held; ++i) {
      Counter c = registry.counter("test.held." + i);
      c.increment();
      heldCounters.add(c);
    }
  }

  /**
   * One cleanup pass, then one update through every held reference. The updates are the part that
   * changes: a held reference re-resolves only if the pass invalidated the meter it is holding.
   */
  @Benchmark
  public void sweepThenUpdateHeldReferences(Blackhole bh) {
    registry.removeExpiredMeters();
    for (int i = 0; i < heldCounters.size(); ++i) {
      heldCounters.get(i).increment();
    }
    bh.consume(registry);
  }

  /**
   * The pass alone, with no updates after it. Subtracting this and {@link
   * #updateHeldReferencesOnly} from {@link #sweepThenUpdateHeldReferences} leaves the cost the
   * pass imposes on the survivors, which is the number the design changes.
   */
  @Benchmark
  public void sweepOnly(Blackhole bh) {
    registry.removeExpiredMeters();
    bh.consume(registry);
  }

  /**
   * The updates alone, with no pass in front of them.
   */
  @Benchmark
  public void updateHeldReferencesOnly(Blackhole bh) {
    for (int i = 0; i < heldCounters.size(); ++i) {
      heldCounters.get(i).increment();
    }
    bh.consume(registry);
  }
}
