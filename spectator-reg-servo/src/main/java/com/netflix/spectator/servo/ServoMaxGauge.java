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
package com.netflix.spectator.servo;

import com.netflix.servo.annotations.DataSourceType;
import com.netflix.servo.monitor.AbstractMonitor;
import com.netflix.servo.monitor.MaxGauge;
import com.netflix.servo.monitor.Monitor;
import com.netflix.servo.monitor.MonitorConfig;
import com.netflix.servo.monitor.NumericMonitor;
import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Reports a constant value passed into the constructor.
 */
final class ServoMaxGauge<T extends Number> extends AbstractMonitor<Double>
    implements Gauge, ServoMeter, NumericMonitor<Double> {

  private final Id id;
  private final Clock clock;
  private final MaxGauge impl;
  private final AtomicLong lastUpdated;

  /**
   * Create a new monitor that returns {@code value}.
   */
  ServoMaxGauge(Id id, Clock clock, MonitorConfig config) {
    super(config.withAdditionalTag(DataSourceType.GAUGE));
    this.id = id;
    this.clock = clock;
    this.impl = new MaxGauge(config);
    this.lastUpdated = new AtomicLong(clock.wallTime());
  }

  @Override public Id id() {
    return id;
  }

  @Override public boolean hasExpired() {
    long now = clock.wallTime();
    return now - lastUpdated.get() > ServoRegistry.EXPIRATION_TIME_MILLIS;
  }

  @Override public Iterable<Measurement> measure() {
    long now = clock.wallTime();
    double v = impl.getValue(0).doubleValue();
    return Collections.singleton(new Measurement(id(), now, v));
  }

  @Override public void set(double v) {
    impl.update((long) v);
    lastUpdated.set(clock.wallTime());
  }

  @Override public double value() {
    return hasExpired() ? Double.NaN : impl.getValue(0).doubleValue();
  }

  @Override public Double getValue(int pollerIndex) {
    return value();
  }

  @Override public void addMonitors(List<Monitor<?>> monitors) {
    monitors.add(this);
  }
}
