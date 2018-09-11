/*
 * Copyright 2014-2018 Netflix, Inc.
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


import com.netflix.spectator.api.AbstractRegistry;
import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DistributionSummary;
import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Timer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Registry implementation that maps spectator types to the metrics3 library. */
public class MetricsRegistry extends AbstractRegistry {

  private final com.codahale.metrics.MetricRegistry impl;
  private final Map<String, DoubleGauge> registeredGauges;
  private final NameUtils nameUtil;

  /** Create a new instance. */
  public MetricsRegistry() {
    this(Clock.SYSTEM, new com.codahale.metrics.MetricRegistry());
  }

  /** Create a new instance. */
  public MetricsRegistry(Clock clock, com.codahale.metrics.MetricRegistry impl) {
    this(clock, impl, DefaultNameUtils.INSTANCE);
  }

  /** Create a new instance. */
  public MetricsRegistry(Clock clock, com.codahale.metrics.MetricRegistry impl, NameUtils nameUtil) {
    super(clock);
    this.impl = impl;
    this.registeredGauges = new ConcurrentHashMap<>();
    this.nameUtil = nameUtil;
  }

  private String toMetricName(Id id) {
    return nameUtil.toMetricName(id);
  }

  @Override protected Counter newCounter(Id id) {
    final String name = toMetricName(id);
    return new MetricsCounter(clock(), id, impl.meter(name));
  }

  @Override protected DistributionSummary newDistributionSummary(Id id) {
    final String name = toMetricName(id);
    return new MetricsDistributionSummary(clock(), id, impl.histogram(name));
  }

  @Override protected Timer newTimer(Id id) {
    final String name = toMetricName(id);
    return new MetricsTimer(clock(), id, impl.timer(name));
  }

  @Override protected Gauge newGauge(Id id) {
    final String name = toMetricName(id);
    DoubleGauge gauge = registeredGauges.computeIfAbsent(name, n -> {
      DoubleGauge g = new DoubleGauge();
      impl.register(name, g);
      return g;
    });
    return new MetricsGauge(clock(), id, gauge);
  }

  @Override protected Gauge newMaxGauge(Id id) {
    final String name = toMetricName(id);
    DoubleGauge gauge = registeredGauges.computeIfAbsent(name, n -> {
      DoubleMaxGauge g = new DoubleMaxGauge();
      impl.register(name, g);
      return g;
    });
    return new MetricsGauge(clock(), id, gauge);
  }
}
