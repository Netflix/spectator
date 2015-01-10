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
package com.netflix.spectator.metrics2;

import com.netflix.spectator.api.*;
import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.MetricName;

import java.util.concurrent.TimeUnit;

/** Registry implementation that maps spectator types to the metrics2 library. */
public class MetricsRegistry extends AbstractRegistry {

  private final com.yammer.metrics.core.MetricsRegistry impl;

  /** Create a new instance. */
  public MetricsRegistry() {
    this(Clock.SYSTEM, Metrics.defaultRegistry());
  }

  /** Create a new instance. */
  public MetricsRegistry(Clock clock, com.yammer.metrics.core.MetricsRegistry impl) {
    super(clock);
    this.impl = impl;
  }

  private MetricName toMetricName(Id id) {
    final String name = id.name();
    final int pos = name.lastIndexOf(".");
    if (pos != -1) {
      final String prefix = name.substring(0, pos);
      final String suffix = name.substring(pos + 1);
      return new MetricName("spectator", prefix, suffix);
    } else {
      return new MetricName("spectator", "default", id.name());
    }
  }

  @Override protected Counter newCounter(Id id) {
    final MetricName name = toMetricName(id);
    return new MetricsCounter(clock(), id, impl.newMeter(name, "calls", TimeUnit.SECONDS));
  }

  @Override protected DistributionSummary newDistributionSummary(Id id) {
    final MetricName name = toMetricName(id);
    return new MetricsDistributionSummary(clock(), id, impl.newHistogram(name, false));
  }

  @Override protected Timer newTimer(Id id) {
    final MetricName name = toMetricName(id);
    return new MetricsTimer(clock(), id, impl.newTimer(name, TimeUnit.SECONDS, TimeUnit.SECONDS));
  }
}
