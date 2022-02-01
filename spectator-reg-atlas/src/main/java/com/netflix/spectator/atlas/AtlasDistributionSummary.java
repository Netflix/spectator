/*
 * Copyright 2014-2019 Netflix, Inc.
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
import com.netflix.spectator.api.DistributionSummary;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Statistic;
import com.netflix.spectator.impl.StepDouble;
import com.netflix.spectator.impl.StepLong;
import com.netflix.spectator.impl.StepValue;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Distribution summary that reports four measurements to Atlas:
 *
 * <ul>
 *   <li><b>count:</b> counter incremented each time record is called</li>
 *   <li><b>totalAmount:</b> counter incremented by the recorded amount</li>
 *   <li><b>totalOfSquares:</b> counter incremented by the recorded amount<sup>2</sup></li>
 *   <li><b>max:</b> maximum recorded amount</li>
 * </ul>
 *
 * <p>Having an explicit {@code totalAmount} and {@code count} on the backend
 * can be used to calculate an accurate average for an arbitrary grouping. The
 * {@code totalOfSquares} is used for computing a standard deviation.</p>
 *
 * <p>Note that the {@link #count()} and {@link #totalAmount()} will report
 * the values since the last complete interval rather than the total for the
 * life of the process.</p>
 */
class AtlasDistributionSummary extends AtlasMeter implements DistributionSummary {

  private final StepLong count;
  private final StepLong total;
  private final StepDouble totalOfSquares;
  private final StepLong max;

  private final Id[] stats;

  /** Create a new instance. */
  AtlasDistributionSummary(Id id, Clock clock, long ttl, long step) {
    super(id, clock, ttl);
    this.count = new StepLong(0L, clock, step);
    this.total = new StepLong(0L, clock, step);
    this.totalOfSquares = new StepDouble(0.0, clock, step);
    this.max = new StepLong(0L, clock, step);
    this.stats = new Id[] {
        id.withTags(DsType.rate,  Statistic.count),
        id.withTags(DsType.rate,  Statistic.totalAmount),
        id.withTags(DsType.rate,  Statistic.totalOfSquares),
        id.withTags(DsType.gauge, Statistic.max)
    };
  }

  @Override void measure(long now, MeasurementConsumer consumer) {
    reportMeasurement(now, consumer, stats[0], count);
    reportMeasurement(now, consumer, stats[1], total);
    reportMeasurement(now, consumer, stats[2], totalOfSquares);
    reportMaxMeasurement(now, consumer, stats[3], max);
  }

  private void reportMeasurement(long now, MeasurementConsumer consumer, Id mid, StepValue v) {
    // poll needs to be called before accessing the timestamp to ensure
    // the counters have been rotated if there was no activity in the
    // current interval.
    double rate = v.pollAsRate(now);
    long timestamp = v.timestamp();
    consumer.accept(mid, timestamp, rate);
  }

  private void reportMaxMeasurement(long now, MeasurementConsumer consumer, Id mid, StepLong v) {
    // poll needs to be called before accessing the timestamp to ensure
    // the counters have been rotated if there was no activity in the
    // current interval.
    double maxValue = v.poll(now);
    long timestamp = v.timestamp();
    consumer.accept(mid, timestamp, maxValue);
  }

  @Override public void record(long amount) {
    long now = clock.wallTime();
    count.getCurrent(now).incrementAndGet();
    if (amount > 0) {
      total.getCurrent(now).addAndGet(amount);
      totalOfSquares.getCurrent(now).addAndGet((double) amount * amount);
      updateMax(max.getCurrent(now), amount);
    }
    updateLastModTime(now);
  }

  private void updateMax(AtomicLong maxValue, long v) {
    long p = maxValue.get();
    while (v > p && !maxValue.compareAndSet(p, v)) {
      p = maxValue.get();
    }
  }

  @Override public long count() {
    return count.poll();
  }

  @Override public long totalAmount() {
    return total.poll();
  }
}
