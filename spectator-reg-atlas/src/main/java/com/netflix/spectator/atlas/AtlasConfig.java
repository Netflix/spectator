/*
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
package com.netflix.spectator.atlas;

import com.netflix.spectator.api.RegistryConfig;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

/**
 * Configuration for Atlas registry.
 */
public interface AtlasConfig extends RegistryConfig {

  /**
   * Returns the step size (reporting frequency) to use. The default is 1 minute.
   */
  default Duration step() {
    String v = get("atlas.step");
    return (v == null) ? Duration.ofMinutes(1) : Duration.parse(v);
  }

  /**
   * Returns the number of threads to use with the scheduler. The default is
   * 2 threads.
   */
  default int numThreads() {
    String v = get("atlas.numThreads");
    return (v == null) ? 2 : Integer.parseInt(v);
  }

  /**
   * Returns the URI for the Atlas backend. The default is
   * {@code http://localhost:7101/api/v1/publish}.
   */
  default String uri() {
    String v = get("atlas.uri");
    return (v == null) ? "http://localhost:7101/api/v1/publish" : v;
  }

  /**
   * Returns the connection timeout for requests to the backend. The default is
   * 1 second.
   */
  default Duration connectTimeout() {
    String v = get("atlas.connectTimeout");
    return (v == null) ? Duration.ofSeconds(1) : Duration.parse(v);
  }

  /**
   * Returns the read timeout for requests to the backend. The default is
   * 10 seconds.
   */
  default Duration readTimeout() {
    String v = get("atlas.readTimeout");
    return (v == null) ? Duration.ofSeconds(10) : Duration.parse(v);
  }

  /**
   * Returns the number of measurements per request to use for the backend. If more
   * measurements are found, then multiple requests will be made. The default is
   * 10,000.
   */
  default int batchSize() {
    String v = get("atlas.batchSize");
    return (v == null) ? 10000 : Integer.parseInt(v);
  }

  /**
   * Returns the common tags to apply to all metrics reported to Atlas.
   * The default is an empty map.
   */
  default Map<String, String> commonTags() {
    return Collections.emptyMap();
  }
}
