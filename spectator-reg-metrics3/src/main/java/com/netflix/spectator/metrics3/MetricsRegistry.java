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
package com.netflix.spectator.metrics3;

import com.netflix.spectator.api.*;

/** Registry implementation that maps spectator types to the metrics3 library. */
public class MetricsRegistry extends AbstractRegistry {

  private final com.codahale.metrics.MetricRegistry impl;

  /** Create a new instance. */
  public MetricsRegistry() {
    this(Clock.SYSTEM, new com.codahale.metrics.MetricRegistry());
  }

  /** Create a new instance. */
  public MetricsRegistry(Clock clock, com.codahale.metrics.MetricRegistry impl) {
    super(clock);
    this.impl = impl;
  }

  private String toMetricName(Id id) {
    StringBuilder buf = new StringBuilder();
    buf.append(id.name());
    for (Tag t : id.tags()) {
      buf.append(t.key()).append("-").append(t.value());
    }
    return buf.toString();
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
}
