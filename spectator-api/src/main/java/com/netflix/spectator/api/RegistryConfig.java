/**
 * Copyright 2014-2016 Netflix, Inc.
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

import java.time.Duration;

/**
 * Configuration settings for the registry.
 */
public interface RegistryConfig {

  /**
   * Get the value associated with a key.
   *
   * @param k
   *     Key to lookup in the config.
   * @return
   *     Value for the key or null if no key is present.
   */
  String get(String k);

  /** Should an exception be thrown for warnings? */
  default boolean propagateWarnings() {
    String v = get("propagateWarnings");
    return (v == null) ? false : Boolean.valueOf(v);
  }

  /**
   * For classes based on {@link com.netflix.spectator.api.AbstractRegistry} this setting is used
   * to determine the maximum number of registered meters permitted. This limit is used to help
   * protect the system from a memory leak if there is a bug or irresponsible usage of registering
   * meters.
   *
   * @return
   *     Maximum number of distinct meters that can be registered at a given time. The default is
   *     {@link java.lang.Integer#MAX_VALUE}.
   */
  default int maxNumberOfMeters() {
    String v = get("maxNumberOfMeters");
    return (v == null) ? Integer.MAX_VALUE : Integer.parseInt(v);
  }

  /** How often registered gauges should get polled. */
  default Duration gaugePollingFrequency() {
    String v = get("gaugePollingFrequency");
    return (v == null) ? Duration.ofSeconds(10) : Duration.parse(v);
  }
}
