/*
 * Copyright 2014-2020 Netflix, Inc.
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
import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Statistic;
import com.netflix.spectator.impl.StepDouble;

/**
 * Gauge that reports the maximum value submitted during an interval to Atlas. Main use-case
 * right now is for allowing the max stat used internally to AtlasDistributionSummary and
 * AtlasTimer to be transferred to a remote AtlasRegistry.
 */
class AtlasMaxGauge extends AtlasMeter implements Gauge {

  private final StepDouble value;
  private final Id stat;

  /** Create a new instance. */
  AtlasMaxGauge(Registry registry, Id id, Clock clock, long ttl, long step) {
    super(id, clock, ttl);
    // Initialize to NaN so that it can be used with negative values
    this.value = new StepDouble(Double.NaN, clock, step);
    // Add the statistic for typing. Re-adding the tags from the id is to retain
    // the statistic from the id if it was already set
    this.stat = AtlasMeter.addIfMissing(id, Statistic.max, DsType.gauge);
  }

  @Override void measure(long now, MeasurementConsumer consumer) {
    // poll needs to be called before accessing the timestamp to ensure
    // the counters have been rotated if there was no activity in the
    // current interval.
    double v = value.poll(now);
    if (Double.isFinite(v)) {
      consumer.accept(stat, value.timestamp(), v);
    }
  }

  @Override public void set(double v) {
    long now = clock.wallTime();
    value.getCurrent(now).max(v);
    updateLastModTime(now);
  }

  @Override public double value() {
    return value.poll();
  }
}
