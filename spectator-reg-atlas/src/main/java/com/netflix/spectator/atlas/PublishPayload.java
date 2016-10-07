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

import com.netflix.spectator.api.Measurement;

import java.util.List;
import java.util.Map;

/**
 * Wraps a list of measurements with a set of common tags. The common tags are
 * typically used for things like the application and instance id.
 */
class PublishPayload {

  private final Map<String, String> tags;
  private final List<Measurement> metrics;

  /** Create a new instance. */
  PublishPayload(Map<String, String> tags, List<Measurement> metrics) {
    this.tags = tags;
    this.metrics = metrics;
  }

  /** Return the common tags. Needs to be public for Jackson bean mapper. */
  public Map<String, String> getTags() {
    return tags;
  }

  /** Return the list of measurements. Needs to be public for Jackson bean mapper. */
  public List<Measurement> getMetrics() {
    return metrics;
  }
}
