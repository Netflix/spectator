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
package com.netflix.spectator.metrics3;

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;

import java.util.Collections;

/**
 * Spectator gauge that wraps {@link DoubleGauge}.
 *
 * @author Kennedy Oliveira
 */
class MetricsGauge implements com.netflix.spectator.api.Gauge {

  private final Clock clock;
  private final Id id;
  private final DoubleGauge gauge;

  /**
   * Create a gauge that samples the provided number for the value.
   *
   * @param clock
   *     Clock used for accessing the current time.
   * @param id
   *     Identifier for the gauge.
   * @param gauge
   *     Gauge object that is registered with metrics3.
   */
  MetricsGauge(Clock clock, Id id, DoubleGauge gauge) {
    this.clock = clock;
    this.id = id;
    this.gauge = gauge;
  }

  @Override public Id id() {
    return id;
  }

  @Override public boolean hasExpired() {
    return false;
  }

  @Override public Iterable<Measurement> measure() {
    return Collections.singleton(new Measurement(id, clock.wallTime(), value()));
  }

  @Override public void set(double v) {
    gauge.set(v);
  }

  @Override public double value() {
    return gauge.getValue();
  }
}
