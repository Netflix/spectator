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

import com.netflix.spectator.api.Registry;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Helper to poll JMX data based on a config and update a registry.
 *
 * <p><b>This class is an internal implementation detail only intended for use by
 * {@code spectator-agent}. It is subject to change without notice.</b></p>
 */
public class JmxPoller {

  private static final Logger LOGGER = LoggerFactory.getLogger(JmxPoller.class);

  private final Registry registry;
  private List<JmxConfig> configs = Collections.emptyList();

  /**
   * Create a new instance.
   *
   * @param registry
   *     Registry to update when polling the data.
   */
  public JmxPoller(Registry registry) {
    this.registry = registry;
  }

  /**
   * Update the set of configs for what to poll.
   */
  public void updateConfigs(List<? extends Config> configs) {
    this.configs = configs.stream()
        .map(JmxConfig::from)
        .collect(Collectors.toList());
  }

  /**
   * Poll the JMX data once and update the registry.
   */
  public void poll() {
    for (JmxConfig config : configs) {
      try {
        for (JmxData data : JmxData.query(config.getQuery())) {
          for (JmxMeasurementConfig cfg : config.getMeasurements()) {
            cfg.measure(registry, data);
          }
        }
      } catch (Exception e) {
        LOGGER.warn("failed to query jmx data: {}", config.getQuery().getCanonicalName(), e);
      }
    }
  }
}
