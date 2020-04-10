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
import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Statistic;
import com.netflix.spectator.impl.AtomicDouble;

/**
 * Meter that reports a single value to Atlas.
 */
class AtlasGauge extends AtlasMeter implements Gauge {

  private final AtomicDouble value;
  private final Id stat;

  /** Create a new instance. */
  AtlasGauge(Id id, Clock clock, long ttl) {
    super(id, clock, ttl);
    this.value = new AtomicDouble(0.0);
    // Add the statistic for typing. Re-adding the tags from the id is to retain
    // the statistic from the id if it was already set
    this.stat = id.withTag(Statistic.gauge).withTags(id.tags()).withTag(DsType.gauge);
  }

  @Override void measure(MeasurementConsumer consumer) {
    final double v = value();
    if (Double.isFinite(v)) {
      consumer.accept(stat, clock.wallTime(), v);
    }
  }

  @Override public void set(double v) {
    value.set(v);
    updateLastModTime();
  }

  @Override public double value() {
    return value.get();
  }
}
