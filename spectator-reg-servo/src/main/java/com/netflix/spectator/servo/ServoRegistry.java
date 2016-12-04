/**
 * Copyright 2015 Netflix, Inc.
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

import com.netflix.servo.DefaultMonitorRegistry;
import com.netflix.servo.monitor.*;
import com.netflix.spectator.api.*;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Registry that maps spectator types to servo. */
public class ServoRegistry extends AbstractRegistry implements CompositeMonitor<Integer> {


  private static final Logger LOGGER = LoggerFactory.getLogger(ServoRegistry.class);

  /**
   * The amount of time in milliseconds after which activity based meters will get marked as
   * expired. Right now they will not go away completely so the total count for the life of the
   * process can be maintained in the api.
   *
   * The configuration setting is in minutes.
   */
  static final long EXPIRATION_TIME_MILLIS = getExpirationTimeMillis();

  private static final MonitorConfig DEFAULT_CONFIG =
    (new MonitorConfig.Builder("spectator.registry")).build();

  private static long getExpirationTimeMillis() {
    final String key = "spectator.servo.expirationTimeInMinutes";
    long minutes = 15;
    String v = System.getProperty(key, "" + minutes);
    try {
      minutes = Long.parseLong(v);
    } catch (NumberFormatException e) {
      LOGGER.error("invalid value for property '" + key + "', expecting integer: '" + v + "'."
        + " The default value of " + minutes + " minutes will be used.", e);
    }
    return TimeUnit.MINUTES.toMillis(minutes);
  }

  private final MonitorConfig config;

  /** Create a new instance. */
  public ServoRegistry() {
    this(Clock.SYSTEM);
  }

  /** Create a new instance. */
  public ServoRegistry(Clock clock) {
    this(clock, DEFAULT_CONFIG);
  }

  /** Create a new instance. */
  ServoRegistry(Clock clock, MonitorConfig config) {
    super(clock);
    this.config = config;
    DefaultMonitorRegistry.getInstance().register(this);
  }

  /** Converts a spectator id into a MonitorConfig that can be used by servo. */
  MonitorConfig toMonitorConfig(Id id) {
    MonitorConfig.Builder builder = new MonitorConfig.Builder(id.name());
    for (Tag t : id.tags()) {
      builder.withTag(t.key(), t.value());
    }
    return builder.build();
  }

  @Override protected Counter newCounter(Id id) {
    MonitorConfig cfg = toMonitorConfig(id);
    StepCounter counter = new StepCounter(cfg, new ServoClock(clock()));
    return new ServoCounter(clock(), counter);
  }

  @Override protected DistributionSummary newDistributionSummary(Id id) {
    return new ServoDistributionSummary(this, id);
  }

  @Override protected Timer newTimer(Id id) {
    return new ServoTimer(this, id);
  }

  @Override protected Gauge newGauge(Id id) {
    return new ServoGauge(clock(), toMonitorConfig(id));
  }

  @Override public Integer getValue() {
    return 0;
  }

  @Override public Integer getValue(int pollerIndex) {
    return 0;
  }

  @Override public MonitorConfig getConfig() {
    return config;
  }

  @Override public List<Monitor<?>> getMonitors() {
    List<Monitor<?>> monitors = new ArrayList<>();
    for (Meter meter : this) {
      if (!meter.hasExpired()) {
        ((ServoMeter) meter).addMonitors(monitors);
      }
    }
    return monitors;
  }
}
