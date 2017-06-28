/*
 * Copyright 2014-2017 Netflix, Inc.
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
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Statistic;
import com.netflix.spectator.api.Timer;
import com.netflix.spectator.impl.StepDouble;
import com.netflix.spectator.impl.StepLong;
import com.netflix.spectator.impl.StepValue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Timer that reports four measurements to Atlas:
 *
 * <ul>
 *   <li><b>count:</b> counter incremented each time record is called</li>
 *   <li><b>totalTime:</b> counter incremented by the recorded amount</li>
 *   <li><b>totalOfSquares:</b> counter incremented by the recorded amount<sup>2</sup></li>
 *   <li><b>max:</b> maximum recorded amount</li>
 * </ul>
 *
 * <p>Having an explicit {@code totalTime} and {@code count} on the backend
 * can be used to calculate an accurate average for an arbitrary grouping. The
 * {@code totalOfSquares} is used for computing a standard deviation.</p>
 *
 * <p>Note that the {@link #count()} and {@link #totalTime()} will report
 * the values since the last complete interval rather than the total for the
 * life of the process.</p>
 */
class AtlasTimer extends AtlasMeter implements Timer {

  private final StepLong count;
  private final StepLong total;
  private final StepDouble totalOfSquares;
  private final StepLong max;

  private final Id[] stats;

  /** Create a new instance. */
  AtlasTimer(Id id, Clock clock, long ttl, long step) {
    super(id, clock, ttl);
    this.count = new StepLong(0L, clock, step);
    this.total = new StepLong(0L, clock, step);
    this.totalOfSquares = new StepDouble(0.0, clock, step);
    this.max = new StepLong(0L, clock, step);
    this.stats = new Id[] {
        id.withTags(DsType.rate,  Statistic.count),
        id.withTags(DsType.rate,  Statistic.totalTime),
        id.withTags(DsType.rate,  Statistic.totalOfSquares),
        id.withTags(DsType.gauge, Statistic.max)
    };
  }

  @Override public Iterable<Measurement> measure() {
    List<Measurement> ms = new ArrayList<>(4);
    ms.add(newMeasurement(stats[0], count, 1.0));
    ms.add(newMeasurement(stats[1], total, 1e-9));
    ms.add(newMeasurement(stats[2], totalOfSquares, 1e-18));
    ms.add(newMaxMeasurement(stats[3], max));
    return ms;
  }

  private Measurement newMeasurement(Id mid, StepValue v, double f) {
    // poll needs to be called before accessing the timestamp to ensure
    // the counters have been rotated if there was no activity in the
    // current interval.
    double rate = v.pollAsRate() * f;
    long timestamp = v.timestamp();
    return new Measurement(mid, timestamp, rate);
  }

  private Measurement newMaxMeasurement(Id mid, StepLong v) {
    // poll needs to be called before accessing the timestamp to ensure
    // the counters have been rotated if there was no activity in the
    // current interval.
    double maxValue = v.poll() / 1e9;
    long timestamp = v.timestamp();
    return new Measurement(mid, timestamp, maxValue);
  }

  @Override public void record(long amount, TimeUnit unit) {
    count.getCurrent().incrementAndGet();
    if (amount > 0) {
      final long nanos = unit.toNanos(amount);
      total.getCurrent().addAndGet(nanos);
      totalOfSquares.getCurrent().addAndGet((double) nanos * nanos);
      updateMax(max.getCurrent(), nanos);
    }
    updateLastModTime();
  }

  private void updateMax(AtomicLong maxValue, long v) {
    long p = maxValue.get();
    while (v > p && !maxValue.compareAndSet(p, v)) {
      p = maxValue.get();
    }
  }

  @Override public <T> T record(Callable<T> f) throws Exception {
    final long start = clock.monotonicTime();
    try {
      return f.call();
    } finally {
      record(clock.monotonicTime() - start, TimeUnit.NANOSECONDS);
    }
  }

  @Override public void record(Runnable f) {
    final long start = clock.monotonicTime();
    try {
      f.run();
    } finally {
      record(clock.monotonicTime() - start, TimeUnit.NANOSECONDS);
    }
  }

  @Override public long count() {
    return count.poll();
  }

  @Override public long totalTime() {
    return total.poll();
  }
}
