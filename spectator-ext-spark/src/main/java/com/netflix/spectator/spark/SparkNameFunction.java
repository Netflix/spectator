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
package com.netflix.spectator.spark;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps hierarchical spark names into tagged ids.
 */
public final class SparkNameFunction implements NameFunction {

  private static final String PREFIX = "spark.";

  private static final Id DROP_METRIC = null;

  /**
   * Create a name function based on a config. It will use the key
   * {@code spectator.spark.name-patterns}.
   */
  public static SparkNameFunction fromConfig(Config config, Registry registry) {
    return fromConfig(config, "spectator.spark.name-patterns", registry);
  }

  /**
   * Create a name function based on a config.
   */
  public static SparkNameFunction fromConfig(Config config, String key, Registry registry) {
    return fromPatternList(config.getConfigList(key), registry);
  }

  private static SparkNameFunction fromPatternList(List<? extends Config> patterns, Registry registry) {
    final List<NameMatcher> matchers = new ArrayList<>();
    for (Config config : patterns) {
      final Pattern pattern = Pattern.compile(config.getString("pattern"));
      final Map<String, Integer> tagsMap = new LinkedHashMap<>();
      final Config tagsCfg = config.getConfig("tags");
      for (Map.Entry<String, ConfigValue> entry : tagsCfg.entrySet()) {
        tagsMap.put(entry.getKey(), (Integer) entry.getValue().unwrapped());
      }
      matchers.add(new NameMatcher(pattern, registry, config.getInt("name"), tagsMap));
    }
    return new SparkNameFunction(matchers);
  }

  private static class NameMatcher {
    private final Pattern pattern;
    private final Registry registry;
    private final int name;
    private final Map<String, Integer> tags;

    NameMatcher(Pattern pattern, Registry registry, int name, Map<String, Integer> tags) {
      this.pattern = pattern;
      this.registry = registry;
      this.name = name;
      this.tags = tags;
    }

    Id apply(String metric) {
      final Matcher m = pattern.matcher(metric);
      if (m.matches()) {
        Id id = registry.createId(PREFIX + m.group(name));
        for (Map.Entry<String, Integer> entry : tags.entrySet()) {
          id = id.withTag(entry.getKey(), m.group(entry.getValue()));
        }
        // separate executor jvm metrics from driver executor metrics
        if (!tags.containsKey("role")) {
          id = id.withTag("role", "executor");
        }
        return id;
      } else {
        return DROP_METRIC;
      }

    }
  }

  private final List<NameMatcher> matchers;

  private SparkNameFunction(List<NameMatcher> matchers) {
    this.matchers = matchers;
  }

  @Override public Id apply(String name) {
    for (NameMatcher matcher : matchers) {
      Id id = matcher.apply(name);
      if (id != DROP_METRIC) {
        return id;
      }
    }
    return DROP_METRIC;
  }
}
