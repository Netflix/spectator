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
package com.netflix.spectator.stateless;

import com.netflix.spectator.api.RegistryConfig;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

/**
 * Configuration for stateless registry.
 */
@FunctionalInterface
public interface StatelessConfig extends RegistryConfig {

  /**
   * Returns true if publishing is enabled. Default is true.
   */
  default boolean enabled() {
    String v = get("stateless.enabled");
    return v == null || Boolean.parseBoolean(v);
  }

  /**
   * Returns the frequency to collect data and forward to the aggregation service. The
   * default is 5 seconds.
   */
  default Duration frequency() {
    String v = get("stateless.frequency");
    return v == null ? Duration.ofSeconds(5) : Duration.parse(v);
  }

  /**
   * Returns the TTL for meters that do not have any activity. After this period the meter
   * will be considered expired and will not get reported. Default is 15 minutes.
   */
  default Duration meterTTL() {
    String v = get("stateless.meterTTL");
    return (v == null) ? Duration.ofMinutes(15) : Duration.parse(v);
  }

  /**
   * Returns the connection timeout for requests to the backend. The default is
   * 1 second.
   */
  default Duration connectTimeout() {
    String v = get("stateless.connectTimeout");
    return (v == null) ? Duration.ofSeconds(1) : Duration.parse(v);
  }

  /**
   * Returns the read timeout for requests to the backend. The default is
   * 10 seconds.
   */
  default Duration readTimeout() {
    String v = get("stateless.readTimeout");
    return (v == null) ? Duration.ofSeconds(10) : Duration.parse(v);
  }

  /**
   * Returns the URI for the aggregation service. The default is
   * {@code http://localhost:7101/api/v4/update}.
   */
  default String uri() {
    String v = get("stateless.uri");
    return (v == null) ? "http://localhost:7101/api/v4/update" : v;
  }

  /**
   * Returns the number of measurements per request to use for the backend. If more
   * measurements are found, then multiple requests will be made. The default is
   * 10,000.
   */
  default int batchSize() {
    String v = get("stateless.batchSize");
    return (v == null) ? 10000 : Integer.parseInt(v);
  }

  /**
   * Returns the common tags to apply to all metrics. The default is an empty map.
   */
  default Map<String, String> commonTags() {
    return Collections.emptyMap();
  }
}
