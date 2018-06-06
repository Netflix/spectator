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
package com.netflix.spectator.jvm;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Registry;
import com.typesafe.config.Config;

import javax.management.ObjectName;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Config for extracting a measurment from a JMX bean.
 *
 * <ul>
 *   <li><b>name:</b> name pattern to use, see {@link MappingExpr#substitute(String, Map)}</li>
 *   <li><b>tags:</b> tags to add on, values can use patterns,
 *   see {@link MappingExpr#substitute(String, Map)}</li>
 *   <li><b>value:</b> value expression, see {@link MappingExpr#eval(String, Map)}</li>
 *   <li><b>counter:</b> is the value a monotonically increasing counter value?</li>
 * </ul>
 */
final class JmxMeasurementConfig {

  /** Create from a Typesafe Config object. */
  static JmxMeasurementConfig from(Config config) {
    String name = config.getString("name");
    Map<String, String> tags = config.getConfigList("tags")
        .stream()
        .collect(Collectors.toMap(c -> c.getString("key"), c -> c.getString("value")));
    String value = config.getString("value");
    boolean counter = config.hasPath("counter") && config.getBoolean("counter");
    return new JmxMeasurementConfig(name, tags, value, counter);
  }

  private final String nameMapping;
  private final Map<String, String> tagMappings;
  private final String valueMapping;
  private final boolean counter;

  private final Map<ObjectName, JmxData> previousData;
  private final Map<Id, AtomicLong> previousCount;

  /** Create a new instance. */
  JmxMeasurementConfig(
      String nameMapping,
      Map<String, String> tagMappings,
      String valueMapping,
      boolean counter) {
    this.nameMapping = nameMapping;
    this.tagMappings = tagMappings;
    this.valueMapping = valueMapping;
    this.counter = counter;
    this.previousData = new ConcurrentHashMap<>();
    this.previousCount = new ConcurrentHashMap<>();
  }

  /**
   * Fill in {@code ms} with measurements extracted from {@code data}.
   */
  void measure(Registry registry, JmxData data, List<Measurement> ms) {
    Map<String, String> tags = tagMappings.entrySet().stream().collect(Collectors.toMap(
        Map.Entry::getKey,
        e -> MappingExpr.substitute(e.getValue(), data.getStringAttrs())
    ));
    Id id = registry
        .createId(MappingExpr.substitute(nameMapping, data.getStringAttrs()))
        .withTags(tags);

    Map<String, Number> numberAttrs = new HashMap<>(data.getNumberAttrs());
    JmxData previous = previousData.put(data.getName(), data);
    if (previous != null) {
      previous.getNumberAttrs().forEach((key, value) -> numberAttrs.put("previous:" + key, value));
    }

    Double v = MappingExpr.eval(valueMapping, numberAttrs);

    if (v != null && !v.isNaN()) {
      if (counter) {
        updateCounter(registry, id, v.longValue());
      } else {
        ms.add(new Measurement(id, registry.clock().wallTime(), v));
      }
    }
  }

  private void updateCounter(Registry registry, Id id, long v) {
    AtomicLong prev = previousCount.computeIfAbsent(id, i -> new AtomicLong(Long.MIN_VALUE));
    long p = prev.get();
    if (prev.compareAndSet(p, v) && p != Long.MIN_VALUE) {
      registry.counter(id).increment(v - p);
    }
  }
}
