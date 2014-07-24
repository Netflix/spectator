/**
 * Copyright 2014 Netflix, Inc.
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
import com.netflix.spectator.api.Timer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Registry that maps spectator types to servo. */
public class ServoRegistry extends AbstractRegistry implements CompositeMonitor<Integer> {

  private static final MonitorConfig DEFAULT_CONFIG =
    (new MonitorConfig.Builder("spectator.registry")).build();

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

  private MonitorConfig toMonitorConfig(Id id) {
    MonitorConfig.Builder builder = new MonitorConfig.Builder(id.name());
    for (Tag t : id.tags()) {
      builder.withTag(t.key(), t.value());
    }
    return builder.build();
  }

  @Override protected Counter newCounter(Id id) {
    MonitorConfig cfg = toMonitorConfig(id);
    StepCounter counter = new StepCounter(cfg);
    return new ServoCounter(clock(), new ServoId(cfg), counter);
  }

  @Override protected DistributionSummary newDistributionSummary(Id id) {
    MonitorConfig cfg = toMonitorConfig(id);
    BasicDistributionSummary distributionSummary = new BasicDistributionSummary(cfg);
    return new ServoDistributionSummary(clock(), new ServoId(cfg), distributionSummary);
  }

  @Override protected Timer newTimer(Id id) {
    MonitorConfig cfg = toMonitorConfig(id);
    BasicTimer timer = new BasicTimer(cfg, TimeUnit.SECONDS);
    return new ServoTimer(clock(), new ServoId(cfg), timer);
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
      if (meter instanceof ServoMeter) {
        monitors.add(((ServoMeter) meter).monitor());
      } else {
        for (Measurement m : meter.measure()) {
          monitors.add(new NumberGauge(toMonitorConfig(m.id()), m.value()));
        }
      }
    }
    return monitors;
  }
}
