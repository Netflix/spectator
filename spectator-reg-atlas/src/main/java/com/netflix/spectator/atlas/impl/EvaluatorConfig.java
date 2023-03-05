/*
 * Copyright 2014-2023 Netflix, Inc.
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
package com.netflix.spectator.atlas.impl;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.atlas.AtlasConfig;

import java.util.Map;
import java.util.function.Function;

/**
 * Additional interface that can be implemented by the AtlasConfig instance providing knobs
 * for internal registry details.
 *
 * <p><b>Classes in this package are only intended for use internally within spectator. They may
 * change at any time and without notice.</b>
 */
public interface EvaluatorConfig {

  /** Create a new instance from an AtlasConfig. */
  static EvaluatorConfig fromAtlasConfig(AtlasConfig config) {
    if (config instanceof EvaluatorConfig) {
      return (EvaluatorConfig) config;
    } else {
      return new EvaluatorConfig() {
        @Override public long evaluatorStepSize() {
          return config.lwcStep().toMillis();
        }

        @Override public Map<String, String> commonTags() {
          return config.commonTags();
        }

        @Override public Function<Id, Map<String, String>> idMapper() {
          return new IdMapper(JsonUtils.createReplacementFunction(config.validTagCharacters()));
        }
      };
    }
  }

  /** Step size used for the raw measurements. */
  long evaluatorStepSize();

  /** Returns the common tags to apply to all metrics reported to Atlas. */
  Map<String, String> commonTags();

  /** Function to convert an id to a map of key/value pairs. */
  default Function<Id, Map<String, String>> idMapper() {
    return new IdMapper(Function.identity());
  }

  /** Supplier for cache to use within the evaluator query index. */
  default <T> QueryIndex.CacheSupplier<T> indexCacheSupplier() {
    return new QueryIndex.DefaultCacheSupplier<>(new NoopRegistry());
  }

  /**
   * Returns true if the measurements should be polled in parallel using the default
   * common fork join pool. For apps that are mostly just reporting metrics this can be
   * useful to more quickly process them. Default is false.
   */
  default boolean parallelMeasurementPolling() {
    return false;
  }
}
