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
package com.netflix.spectator.api;

/** Default implementation of registry. */
public final class DefaultRegistry extends AbstractRegistry {

  /** Create a new instance. */
  public DefaultRegistry() {
    this(Clock.SYSTEM);
  }

  /** Create a new instance. */
  public DefaultRegistry(Clock clock) {
    super(clock);
  }

  /** Create a new instance. */
  public DefaultRegistry(Clock clock, RegistryConfig config) {
    super(clock, config);
  }

  @Override protected Counter newCounter(Id id) {
    return new DefaultCounter(clock(), id);
  }

  @Override protected DistributionSummary newDistributionSummary(Id id) {
    return new DefaultDistributionSummary(clock(), id);
  }

  @Override protected Timer newTimer(Id id) {
    return new DefaultTimer(clock(), id);
  }

  @Override protected Gauge newGauge(Id id) {
    return new DefaultGauge(clock(), id);
  }

  @Override protected Gauge newMaxGauge(Id id) {
    return new DefaultMaxGauge(clock(), id);
  }
}
