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
package com.netflix.spectator.spark;

import com.typesafe.config.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Function for performing simple conversions to normalize the values.
 */
public final class SparkValueFunction implements ValueFunction {

  /**
   * Create a value function based on a config. It will use the key
   * {@code spectator.spark.value-conversions}.
   */
  public static SparkValueFunction fromConfig(Config config) {
    return fromConfig(config, "spectator.spark.value-conversions");
  }

  /**
   * Create a value function based on a config.
   */
  public static SparkValueFunction fromConfig(Config config, String key) {
    return fromPatternList(config.getConfigList(key));
  }

  private static SparkValueFunction fromPatternList(List<? extends Config> patterns) {
    final List<NameMatcher> matchers = new ArrayList<>();
    for (Config config : patterns) {
      final Pattern pattern = Pattern.compile(config.getString("pattern"));
      matchers.add(new NameMatcher(pattern, config.getDouble("factor")));
    }
    return new SparkValueFunction(matchers);
  }

  private static class NameMatcher {
    private final Pattern pattern;
    private final double factor;

    NameMatcher(Pattern pattern, double factor) {
      this.pattern = pattern;
      this.factor = factor;
    }

    boolean matches(String name) {
      return pattern.matcher(name).matches();
    }

    double apply(double v) {
      // streaming/src/main/scala/org/apache/spark/streaming/StreamingSource.scala
      // -1 is used for abnormal conditions
      return (Math.abs(v + 1.0) <= 1e-12) ? Double.NaN : v * factor;
    }
  }

  private final List<NameMatcher> matchers;

  private SparkValueFunction(List<NameMatcher> matchers) {
    this.matchers = matchers;
  }

  @Override public double convert(String name, double v) {
    for (NameMatcher matcher : matchers) {
      if (matcher.matches(name)) {
        return matcher.apply(v);
      }
    }
    return v;
  }
}
