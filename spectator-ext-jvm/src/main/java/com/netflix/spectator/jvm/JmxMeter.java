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
package com.netflix.spectator.jvm;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Meter;
import com.netflix.spectator.api.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/** Meter based on a {@link JmxConfig}. */
final class JmxMeter implements Meter {

  private static final Logger LOGGER = LoggerFactory.getLogger(JmxMeter.class);

  private final Registry registry;
  private final JmxConfig config;
  private final Id id;

  /** Create a new instance. */
  JmxMeter(Registry registry, JmxConfig config) {
    this.registry = registry;
    this.config = config;
    this.id = registry.createId(config.getQuery().getCanonicalName());
  }

  @Override public Id id() {
    return id;
  }

  @Override
  public Iterable<Measurement> measure() {

    List<Measurement> ms = new ArrayList<>();
    try {
      for (JmxData data : JmxData.query(config.getQuery())) {
        for (JmxMeasurementConfig cfg : config.getMeasurements()) {
          cfg.measure(registry, data, ms);
        }
      }
    } catch (Exception e) {
      LOGGER.warn("failed to query jmx data: {}", config.getQuery().getCanonicalName(), e);
    }
    return ms;
  }

  @Override public boolean hasExpired() {
    return false;
  }
}
