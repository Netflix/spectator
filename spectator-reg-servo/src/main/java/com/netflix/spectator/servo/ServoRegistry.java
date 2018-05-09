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

import com.netflix.servo.DefaultMonitorRegistry;
import com.netflix.servo.monitor.*;
import com.netflix.spectator.api.*;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.Timer;
import com.netflix.spectator.impl.SwapMeter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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

  // Create an id for the composite monitor that will be used with Servo. This
  // id will not get reported since the composite will get flattened. The UUID
  // is to avoid having multiple instances of ServoRegistry clobber each other.
  private MonitorConfig defaultConfig() {
    return (new MonitorConfig.Builder("spectator.registry"))
        .withTag("id", UUID.randomUUID().toString()).build();
  }

  private final MonitorConfig config;

  /** Create a new instance. */
  public ServoRegistry() {
    this(Clock.SYSTEM);
  }

  /** Create a new instance. */
  public ServoRegistry(Clock clock) {
    this(clock, null);
  }

  /** Create a new instance. */
  ServoRegistry(Clock clock, MonitorConfig config) {
    super(clock);
    this.config = (config == null) ? defaultConfig() : config;
    DefaultMonitorRegistry.getInstance().register(this);
  }

  /** Converts a spectator id into a MonitorConfig that can be used by servo. */
  MonitorConfig toMonitorConfig(Id id, Tag stat) {
    MonitorConfig.Builder builder = new MonitorConfig.Builder(id.name());
    if (stat != null) {
      builder.withTag(stat.key(), stat.value());
    }
    for (Tag t : id.tags()) {
      builder.withTag(t.key(), t.value());
    }
    return builder.build();
  }

  @Override protected Counter newCounter(Id id) {
    MonitorConfig cfg = toMonitorConfig(id, Statistic.count);
    DoubleCounter counter = new DoubleCounter(cfg, new ServoClock(clock()));
    return new ServoCounter(id, clock(), counter);
  }

  @Override protected DistributionSummary newDistributionSummary(Id id) {
    return new ServoDistributionSummary(this, id);
  }

  @Override protected Timer newTimer(Id id) {
    return new ServoTimer(this, id);
  }

  @Override protected Gauge newGauge(Id id) {
    return new ServoGauge(id, clock(), toMonitorConfig(id, Statistic.gauge));
  }

  @Override protected Gauge newMaxGauge(Id id) {
    return new ServoMaxGauge(id, clock(), toMonitorConfig(id, Statistic.max));
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
      ServoMeter sm = getServoMeter(meter);
      if (!meter.hasExpired() && sm != null) {
        sm.addMonitors(monitors);
      }
    }
    removeExpiredMeters();
    return monitors;
  }

  @SuppressWarnings("unchecked")
  private ServoMeter getServoMeter(Meter meter) {
    if (meter instanceof SwapMeter<?>) {
      return getServoMeter(((SwapMeter<?>) meter).get());
    } else if (meter instanceof ServoMeter) {
      return (ServoMeter) meter;
    } else {
      return null;
    }
  }
}
