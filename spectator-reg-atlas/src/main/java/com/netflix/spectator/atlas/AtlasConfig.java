/*
 * Copyright 2014-2021 Netflix, Inc.
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

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Registry;
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
   * Returns the TTL for meters that do not have any activity. After this period the meter
   * will be considered expired and will not get reported. Default is 15 minutes.
   */
  default Duration meterTTL() {
    String v = get("atlas.meterTTL");
    return (v == null) ? Duration.ofMinutes(15) : Duration.parse(v);
  }

  /**
   * Returns true if publishing to Atlas is enabled. Default is true.
   */
  default boolean enabled() {
    String v = get("atlas.enabled");
    return v == null || Boolean.parseBoolean(v);
  }

  /**
   * Returns true if the registry should automatically start the background reporting threads
   * in the constructor. When using DI systems this can be used to automatically start the
   * registry when it is constructed. Otherwise the {@code AtlasRegistry.start()} method will
   * need to be called explicitly. Default is false.
   */
  default boolean autoStart() {
    String v = get("atlas.autoStart");
    return v != null && Boolean.parseBoolean(v);
  }

  /**
   * Returns the number of threads to use with the scheduler. The default is
   * 4 threads.
   */
  default int numThreads() {
    String v = get("atlas.numThreads");
    return (v == null) ? 4 : Integer.parseInt(v);
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
   * Returns the step size (reporting frequency) to use for streaming to Atlas LWC.
   * The default is 5s. This is the highest resolution that would be supported for getting
   * an on-demand stream of the data. It must be less than or equal to the Atlas step
   * ({@link #step()}) and the Atlas step should be an even multiple of this value.
   */
  default Duration lwcStep() {
    String v = get("atlas.lwc.step");
    return (v == null) ? Duration.ofSeconds(5) : Duration.parse(v);
  }

  /**
   * Returns true if streaming to Atlas LWC is enabled. Default is false.
   */
  default boolean lwcEnabled() {
    String v = get("atlas.lwc.enabled");
    return v != null && Boolean.parseBoolean(v);
  }

  /**
   * Returns true if expressions with the same step size as Atlas publishing should be
   * ignored for streaming. This is used for cases where data being published to Atlas
   * is also sent into streaming from the backend. Default is true.
   */
  default boolean lwcIgnorePublishStep() {
    String v = get("atlas.lwc.ignore-publish-step");
    return v == null || Boolean.parseBoolean(v);
  }

  /** Returns the frequency for refreshing config settings from the LWC service. */
  default Duration configRefreshFrequency() {
    String v = get("atlas.configRefreshFrequency");
    return (v == null) ? Duration.ofSeconds(10) : Duration.parse(v);
  }

  /** Returns the TTL for subscriptions from the LWC service. */
  default Duration configTTL() {
    return configRefreshFrequency().multipliedBy(15);
  }

  /**
   * Returns the URI for the Atlas LWC endpoint to retrieve current subscriptions.
   * The default is {@code http://localhost:7101/lwc/api/v1/expressions/local-dev}.
   */
  default String configUri() {
    String v = get("atlas.config-uri");
    return (v == null) ? "http://localhost:7101/lwc/api/v1/expressions/local-dev" : v;
  }

  /**
   * Returns the URI for the Atlas LWC endpoint to evaluate the data for a suscription.
   * The default is {@code http://localhost:7101/lwc/api/v1/evaluate}.
   */
  default String evalUri() {
    String v = get("atlas.eval-uri");
    return (v == null) ? "http://localhost:7101/lwc/api/v1/evaluate" : v;
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
   * Returns the common tags to apply to all metrics reported to Atlas. The returned tags
   * must only use valid characters as defined by {@link #validTagCharacters()}. The default
   * is an empty map.
   */
  default Map<String, String> commonTags() {
    return Collections.emptyMap();
  }

  /**
   * Returns a pattern indicating the valid characters for a tag key or value. The default is
   * {@code -._A-Za-z0-9~^}.
   */
  default String validTagCharacters() {
    return "-._A-Za-z0-9~^";
  }

  /**
   * Returns a map from tag key to a pattern indicating the valid characters for the values
   * of that key. The default is an empty map.
   *
   * @deprecated This method is no longer used internally.
   */
  @Deprecated
  default Map<String, String> validTagValueCharacters() {
    return Collections.emptyMap();
  }

  /**
   * Returns a registry to use for recording metrics about the behavior of the AtlasRegistry.
   * By default it will return null and the metrics will be reported to itself. In some cases
   * it is useful to customize this for debugging so that the metrics for the behavior of
   * AtlasRegistry will have a different failure mode than AtlasRegistry.
   */
  default Registry debugRegistry() {
    return null;
  }

  /**
   * Returns a rollup policy that will be applied to the measurements before sending to Atlas.
   * The policy will not be applied to data going to the streaming path. Default is a no-op
   * policy.
   */
  default RollupPolicy rollupPolicy() {
    return RollupPolicy.noop(commonTags());
  }

  /**
   * Avoid collecting right on boundaries to minimize transitions on step longs
   * during a collection. By default it will randomly distribute across the middle
   * of the step interval.
   */
  default long initialPollingDelay(Clock clock, long stepSize) {
    long now = clock.wallTime();
    long stepBoundary = now / stepSize * stepSize;

    // Buffer by 10% of the step interval on either side
    long offset = stepSize / 10;

    // For larger intervals spread it out, otherwise bias towards the start
    // to ensure there is plenty of time to send without needing to cross over
    // to the next interval. The threshold of 1s was chosen because it is typically
    // big enough to avoid GC troubles where it is common to see pause times in the
    // low 100s of milliseconds.
    if (offset >= 1000L) {
      // Check if the current delay is within the acceptable range
      long delay = now - stepBoundary;
      if (delay < offset) {
        return delay + offset;
      } else {
        return Math.min(delay, stepSize - offset);
      }
    } else {
      long firstTime = stepBoundary + stepSize / 10;
      return firstTime > now
          ? firstTime - now
          : firstTime + stepSize - now;
    }
  }

  /**
   * <strong>Alpha:</strong> this method is experimental and may change or be completely
   * removed with no notice.
   *
   * Override to provide a custom publisher for sending data to Atlas. The intended use is
   * for some cases where it is desirable to send the payload somewhere else or to use an
   * alternate client. If the return value is null, then the data will be sent via the normal
   * path.
   */
  default Publisher publisher() {
    return null;
  }
}
