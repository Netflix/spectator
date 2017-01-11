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
package com.netflix.spectator.servo;

import com.netflix.servo.monitor.AbstractMonitor;
import com.netflix.servo.monitor.Monitor;
import com.netflix.servo.monitor.MonitorConfig;
import com.netflix.servo.monitor.NumericMonitor;
import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.impl.AtomicDouble;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Reports a constant value passed into the constructor.
 */
final class ServoGauge<T extends Number> extends AbstractMonitor<Double>
    implements Gauge, ServoMeter, NumericMonitor<Double> {

  private final Clock clock;
  private final AtomicDouble value;
  private final AtomicLong lastUpdated;

  /**
   * Create a new monitor that returns {@code value}.
   */
  ServoGauge(Clock clock, MonitorConfig config) {
    super(config);
    this.clock = clock;
    this.value = new AtomicDouble(Double.NaN);
    this.lastUpdated = new AtomicLong(0L);
  }

  @Override public Id id() {
    return new ServoId(config);
  }

  @Override public boolean hasExpired() {
    long now = clock.wallTime();
    return now - lastUpdated.get() > ServoRegistry.EXPIRATION_TIME_MILLIS;
  }

  @Override public Iterable<Measurement> measure() {
    return null;
  }

  @Override public void set(double v) {
    value.set(v);
    lastUpdated.set(clock.wallTime());
  }

  @Override public double value() {
    return hasExpired() ? Double.NaN : value.get();
  }

  @Override public Double getValue(int pollerIndex) {
    return value();
  }

  @Override public void addMonitors(List<Monitor<?>> monitors) {
    monitors.add(this);
  }
}
