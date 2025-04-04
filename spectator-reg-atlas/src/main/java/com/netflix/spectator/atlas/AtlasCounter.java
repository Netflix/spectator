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
import com.netflix.spectator.api.Statistic;
import com.netflix.spectator.impl.StepDouble;

/**
 * Counter that reports a rate per second to Atlas. Note that {@link #count()} will
 * report the number events in the last complete interval rather than the total for
 * the life of the process.
 */
class AtlasCounter extends AtlasMeter implements Counter {

  private final StepDouble value;
  private final Id stat;

  /** Create a new instance. */
  AtlasCounter(Id id, Clock clock, long ttl, long step) {
    super(id, clock, ttl);
    this.value = new StepDouble(0.0, clock, step);
    // Add the statistic for typing. Re-adding the tags from the id is to retain
    // the statistic from the id if it was already set
    this.stat = AtlasMeter.addIfMissing(id, Statistic.count, DsType.rate);
  }

  @Override void measure(long now, MeasurementConsumer consumer) {
    final double rate = value.pollAsRate(now);
    consumer.accept(stat, value.timestamp(), rate);
  }

  @Override public void add(double amount) {
    if (Double.isFinite(amount) && amount > 0.0) {
      final long now = clock.wallTime();
      value.addAndGet(now, amount);
      updateLastModTime(now);
    }
  }

  @Override public double actualCount() {
    return value.poll();
  }

  @Override public Counter.BatchUpdater batchUpdater(int batchSize) {
    AtlasCounterBatchUpdater updater = new AtlasCounterBatchUpdater(batchSize);
    updater.accept(() -> this);
    return updater;
  }
}
