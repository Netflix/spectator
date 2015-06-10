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

import com.netflix.archaius.annotations.Configuration;
import com.netflix.archaius.annotations.DefaultValue;

/**
 * Configuration settings for the digest plugin.
 */
@Configuration(prefix = "spectator.tdigest.kinesis")
public interface TDigestConfig {
  /** Kinesis endpoint to use. */
  @DefaultValue("kinesis.${EC2_REGION}.amazonaws.com")
  String endpoint();

  /** Name of the kinesis stream where the data should be written. */
  @DefaultValue("spectator-tdigest")
  String stream();

  /** Polling frequency for digest data. */
  @DefaultValue("60")
  long pollingFrequency();
}
