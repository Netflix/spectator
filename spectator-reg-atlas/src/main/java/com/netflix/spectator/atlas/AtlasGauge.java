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

  /**
   * Step size the reported timestamp is floored to, so every gauge sampled for an interval
   * carries the same timestamp. It is applied to the timestamp the registry polls with rather
   * than to a clock of its own: the poll timestamp is the interval the registry is asking
   * about, which is not always the interval the wall clock is in, and the clock the meter
   * holds has to stay the one that drives expiry so the cleanup pass and the meter agree.
   */
  private final long step;

  /**
   * Create a new instance. {@code clock} tracks activity and has to be the clock the registry
   * sweeps with; {@code step} is the interval the reported timestamps are aligned to.
   */
  AtlasGauge(Id id, Clock clock, long ttl, long step) {
    super(id, clock, ttl);
    this.step = step;
    this.value = new AtomicDouble(0.0);
    // Add the statistic for typing. Re-adding the tags from the id is to retain
    // the statistic from the id if it was already set
    this.stat = AtlasMeter.addIfMissing(id, Statistic.gauge, DsType.gauge);
  }

  @Override void measure(long now, MeasurementConsumer consumer) {
    final double v = value();
    if (Double.isFinite(v)) {
      consumer.accept(stat, now / step * step, v);
    }
  }

  @Override public void set(double v) {
    value.set(v);
    updateLastModTime(clock.wallTime());
  }

  @Override public double value() {
    return value.get();
  }
}
