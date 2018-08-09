/*
 * Copyright 2014-2018 Netflix, Inc.
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
package com.netflix.spectator.stateless;

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.DistributionSummary;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Statistic;
import com.netflix.spectator.impl.AtomicDouble;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongToDoubleFunction;

/**
 * Distribution summary that keeps track of the deltas since the last time it was measured.
 */
class StatelessDistributionSummary extends StatelessMeter implements DistributionSummary {

  private final AtomicLong count;
  private final AtomicLong totalAmount;
  private final AtomicDouble totalOfSquares;
  private final AtomicLong max;

  private final Id[] stats;

  /** Create a new instance. */
  StatelessDistributionSummary(Id id, Clock clock, long ttl) {
    super(id, clock, ttl);
    count = new AtomicLong(0);
    totalAmount = new AtomicLong(0);
    totalOfSquares = new AtomicDouble(0.0);
    max = new AtomicLong(0);
    stats = new Id[] {
        id.withTags(Statistic.count),
        id.withTags(Statistic.totalAmount),
        id.withTags(Statistic.totalOfSquares),
        id.withTags(Statistic.max)
    };
  }

  @Override public void record(long amount) {
    if (amount >= 0) {
      count.incrementAndGet();
      totalAmount.addAndGet(amount);
      totalOfSquares.addAndGet(1.0 * amount * amount);
      updateMax(amount);
      updateLastModTime();
    }
  }

  private void updateMax(long v) {
    long p = max.get();
    while (v > p && !max.compareAndSet(p, v)) {
      p = max.get();
    }
  }

  @Override public long count() {
    return count.get();
  }

  @Override public long totalAmount() {
    return totalAmount.get();
  }

  @Override public Iterable<Measurement> measure() {
    if (count.get() == 0) {
      return Collections.emptyList();
    } else {
      List<Measurement> ms = new ArrayList<>(4);
      ms.add(newMeasurement(stats[0], count::getAndSet));
      ms.add(newMeasurement(stats[1], totalAmount::getAndSet));
      ms.add(newMeasurement(stats[2], totalOfSquares::getAndSet));
      ms.add(newMeasurement(stats[3], max::getAndSet));
      return ms;
    }
  }

  private Measurement newMeasurement(Id mid, LongToDoubleFunction getAndSet) {
    double delta = getAndSet.applyAsDouble(0);
    long timestamp = clock.wallTime();
    return new Measurement(mid, timestamp, delta);
  }
}
