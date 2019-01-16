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
package com.netflix.spectator.placeholders;

import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.Registry;

/**
 * Gauge implementation that delegates the value tracking to component gauges
 * based on the current value of the tags associated with the PlaceholderId when the
 * increment methods are called.
 */
class DefaultPlaceholderGauge extends AbstractDefaultPlaceholderMeter<Gauge> implements Gauge {
  /**
   * Constructs a new counter with the specified dynamic id.
   *
   * @param id
   *     The dynamic (template) id for generating the individual counters.
   * @param registry
   *     The registry to use to instantiate the individual counters.
   */
  DefaultPlaceholderGauge(PlaceholderId id, Registry registry) {
    super(id, registry::gauge);
  }

  @Override public void set(double value) {
    resolveToCurrentMeter().set(value);
  }

  @Override public double value() {
    return resolveToCurrentMeter().value();
  }
}
