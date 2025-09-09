/*
 * Copyright 2014-2024 Netflix, Inc.
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
package com.netflix.spectator.ipc;

/**
 * Configuration settings for IpcLogger.
 */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface IpcLoggerConfig {

  /**
   * Get the value for a given key or return null if it is not present.
   */
  String get(String key);

  /**
   * Get the value for a given key or return the specified default if it is not present.
   */
  default String get(String key, String dflt) {
    String v = get(key);
    return v == null ? dflt : v;
  }

  /**
   * Get the value for a given key or return the specified default if it is not present.
   */
  default int getInt(String key, int dflt) {
    int n = dflt;
    String v = get(key, Integer.toString(n));
    try {
      n = Integer.parseInt(v);
    } catch (NumberFormatException ignored) {
    }
    return n;
  }

  /**
   * Determines whether the metrics for number of in flight requests will be generated.
   * These metrics are poorly modeled and often lead to confusion. To estimate number
   * of in flight requests for a given scope, Little's law can be used along with the
   * latency metrics. Defaults to true for backwards compatibility.
   */
  default boolean inflightMetricsEnabled() {
    String v = get("spectator.ipc.inflight-metrics-enabled", "true");
    return "true".equals(v);
  }

  /**
   * Limit to use for the cardinality limiter applied on a given tag key. IPC metrics
   * have many dimensions and a high volume, be careful with increasing the limit as
   * it can easily cause a large increase.
   */
  default int cardinalityLimit(String tagKey) {
    String propKey = "spectator.ipc.cardinality-limit." + tagKey;
    return getInt(propKey, 25);
  }

  /**
   * Size to use for the queue of reusable logger entries.
   */
  default int entryQueueSize() {
    return getInt("spectator.ipc.entry-queue-size", 1000);
  }
}
