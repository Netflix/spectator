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
package com.netflix.spectator.tdigest;

import com.typesafe.config.Config;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Configuration settings for the digest plugin.
 */
public final class TDigestConfig {

  private final Config cfg;

  /**
   * Create a new instance based on the {@link Config} object.
   */
  public TDigestConfig(Config cfg) {
    this.cfg = cfg;
  }

  /** Returns the compression factor to use when creating the digests. */
  public double getCompressionFactor() {
    return cfg.getDouble("compression-factor");
  }

  /** Kinesis endpoint to use. */
  public String getEndpoint() {
    return cfg.getString("kinesis.endpoint");
  }

  /** Name of the kinesis stream where the data should be written. */
  public String getStream() {
    return cfg.getString("kinesis.stream");
  }

  /** Polling frequency for digest data. */
  public long getPollingFrequency(TimeUnit unit) {
    return cfg.getDuration("polling-frequency", unit);
  }

  /** Common tags to add on when pushing to the writer. */
  public Map<String, String> getCommonTags() {
    Map<String, String> tags = new HashMap<>();
    for (Config t : cfg.getConfigList("tags")) {
      tags.put(t.getString("key"), t.getString("value"));
    }
    return Collections.unmodifiableMap(tags);
  }
}
