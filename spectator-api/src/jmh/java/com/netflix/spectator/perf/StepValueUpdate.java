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
package com.netflix.spectator.perf;

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.impl.StepDouble;
import com.netflix.spectator.impl.StepLong;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * Measures the update path of {@link StepLong} and {@link StepDouble}, which every counter,
 * timer, gauge and distribution summary in a step based registry goes through.
 *
 * <p>The timestamp is supplied by the benchmark rather than read from a clock so that the
 * measurement is the step bookkeeping plus the atomic update and not a {@code currentTimeMillis}
 * call, which is more expensive than either and would hide them.</p>
 *
 * <p>Read the {@code WithClock} variants as the headline numbers. The others deliberately leave
 * the clock read out to isolate the bookkeeping, and that makes them easy to misread: the division
 * being removed here is long-latency work on a separate execution port, so on {@code main} it
 * masks whatever else the loop is doing. Remove it and that other work stops hiding, which can
 * make a synthetic loop measure slower even though strictly less work is being done. Measured on
 * {@code main} this benchmark is flat at roughly 490-510M ops/s whether the timestamp is a
 * constant, a counter increment or an array load; with the division gone the same three land at
 * 495M, 471M and 453M, tracking the cost of the timestamp source itself. A real caller reads a
 * clock, which is far more expensive than anything the division could mask, so the effect does
 * not survive contact with the production path.</p>
 *
 * <p>{@code poll} isolates the bookkeeping on its own: it is the step handling followed by a
 * volatile read, with no atomic update. {@code addAndGet} adds the atomic update that a real
 * counter pays. The {@code rolling} variants use a 1ms step with a timestamp that advances on
 * every call, so every single call rolls over to a new interval; that is the worst case for any
 * scheme that tries to make the common in-interval update cheaper.</p>
 */
public class StepValueUpdate {

  /** Step of 5s, matching the default publish interval, with a fixed timestamp. */
  @State(Scope.Benchmark)
  public static class InInterval {
    StepLong stepLong;
    StepDouble stepDouble;
    long now;

    @Setup
    public void setup() {
      now = Clock.SYSTEM.wallTime();
      stepLong = new StepLong(0L, Clock.SYSTEM, 5000L);
      stepDouble = new StepDouble(0.0, Clock.SYSTEM, 5000L);
    }
  }

  /**
   * Step of 1ms with an advancing timestamp, so every call rolls over. Per thread rather than
   * shared, because the advancing timestamp is a plain field and threads sharing it would race
   * on it rather than measuring the rollover.
   */
  @State(Scope.Thread)
  public static class Rolling {
    StepLong stepLong;
    StepDouble stepDouble;
    long now;

    @Setup
    public void setup() {
      now = Clock.SYSTEM.wallTime();
      stepLong = new StepLong(0L, Clock.SYSTEM, 1L);
      stepDouble = new StepDouble(0.0, Clock.SYSTEM, 1L);
    }
  }

  /** Step bookkeeping plus a volatile read, no atomic update. */
  @Benchmark
  public long stepLongPoll(InInterval state) {
    return state.stepLong.poll(state.now);
  }

  /** Step bookkeeping plus a volatile read, no atomic update. */
  @Benchmark
  public double stepDoublePoll(InInterval state) {
    return state.stepDouble.poll(state.now);
  }

  /** Step bookkeeping plus the atomic add a counter pays. */
  @Benchmark
  public long stepLongAddAndGet(InInterval state) {
    return state.stepLong.addAndGet(state.now, 1L);
  }

  /** Step bookkeeping plus the CAS loop a double valued counter pays. */
  @Benchmark
  public double stepDoubleAddAndGet(InInterval state) {
    return state.stepDouble.addAndGet(state.now, 1.0);
  }

  /** Worst case: a rollover on every call. See {@link Rolling}. */
  @Benchmark
  public long rollingStepLong(Rolling state) {
    return state.stepLong.addAndGet(state.now++, 1L);
  }

  /** Worst case: a rollover on every call. See {@link Rolling}. */
  @Benchmark
  public double rollingStepDouble(Rolling state) {
    return state.stepDouble.addAndGet(state.now++, 1.0);
  }

  /**
   * The shape a real update has: a wall clock read feeding the timestamp, as
   * {@code AtlasCounter.add} does. The clock read is the dominant cost here, so this is the
   * benchmark that says what fraction of a real update the step bookkeeping actually is.
   */
  @Benchmark
  public long stepLongAddAndGetWithClock(InInterval state) {
    return state.stepLong.addAndGet(Clock.SYSTEM.wallTime(), 1L);
  }

  /** See {@link #stepLongAddAndGetWithClock}. */
  @Benchmark
  public double stepDoubleAddAndGetWithClock(InInterval state) {
    return state.stepDouble.addAndGet(Clock.SYSTEM.wallTime(), 1.0);
  }

  /** A wall clock read on its own, for scale against the two benchmarks above. */
  @Benchmark
  public long wallTime() {
    return Clock.SYSTEM.wallTime();
  }

  /**
   * A timestamp that changes on every call but stays inside the current interval, so the step
   * bookkeeping cannot be hoisted out of the measurement loop and no rollover is triggered.
   * Separates "the division is gone" from "the JIT hoisted the division" without paying for a
   * clock read.
   */
  @State(Scope.Thread)
  public static class Varying {
    StepLong stepLong;
    long base;
    int i;

    @Setup
    public void setup() {
      // Align to the start of the current interval so base + 0..255 stays inside it.
      base = Clock.SYSTEM.wallTime() / 5000L * 5000L;
      stepLong = new StepLong(0L, Clock.SYSTEM, 5000L);
    }

    long next() {
      return base + (i++ & 0xFF);
    }
  }

  /** See {@link Varying}. */
  @Benchmark
  public long varyingStepLongPoll(Varying state) {
    return state.stepLong.poll(state.next());
  }

  /** See {@link Varying}. */
  @Benchmark
  public long varyingStepLongAddAndGet(Varying state) {
    return state.stepLong.addAndGet(state.next(), 1L);
  }
}
