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

import com.typesafe.config.Config;

import javax.management.ObjectName;
import java.util.ArrayList;
import java.util.List;

/**
 * Config for fetching data from JMX. A configuration consists of:
 *
 * <ul>
 *   <li><b>query:</b> object name query expression, see {@link ObjectName}.</li>
 *   <li><b>measurements:</b> list of {@link JmxMeasurementConfig} objects.</li>
 * </ul>
 */
final class JmxConfig {

  /** Create a new instance from the Typesafe Config object. */
  static JmxConfig from(Config config) {
    try {
      ObjectName query = new ObjectName(config.getString("query"));
      List<JmxMeasurementConfig> ms = new ArrayList<>();
      for (Config cfg : config.getConfigList("measurements")) {
        ms.add(JmxMeasurementConfig.from(cfg));
      }
      return new JmxConfig(query, ms);
    } catch (Exception e) {
      throw new IllegalArgumentException("invalid mapping config", e);
    }
  }

  private final ObjectName query;
  private final List<JmxMeasurementConfig> measurements;

  /** Create a new instance. */
  JmxConfig(ObjectName query, List<JmxMeasurementConfig> measurements) {
    this.query = query;
    this.measurements = measurements;
  }

  /** Object name query expression. */
  ObjectName getQuery() {
    return query;
  }

  /** Measurements to extract for the query. */
  List<JmxMeasurementConfig> getMeasurements() {
    return measurements;
  }
}
