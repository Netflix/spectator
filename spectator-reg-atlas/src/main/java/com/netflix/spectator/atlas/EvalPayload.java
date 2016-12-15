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

import java.util.List;
import java.util.Map;

/**
 * Wraps a list of measurements with a set of common tags. The common tags are
 * typically used for things like the application and instance id.
 */
class EvalPayload {

  private final long timestamp;
  private final List<Metric> metrics;

  /** Create a new instance. */
  EvalPayload(long timestamp, List<Metric> metrics) {
    this.timestamp = timestamp;
    this.metrics = metrics;
  }

  /** Return the timestamp for metrics in this payload. */
  public long getTimestamp() {
    return timestamp;
  }

  /** Return the metric values for the data for this payload. */
  public List<Metric> getMetrics() {
    return metrics;
  }

  /** Metric value. */
  static class Metric {
    private final String id;
    private final Map<String, String> tags;
    private final double value;

    /** Create a new instance. */
    Metric(String id, Map<String, String> tags, double value) {
      this.id = id;
      this.tags = tags;
      this.value = value;
    }

    /** Id for the expression that this data corresponds with. */
    public String getId() {
      return id;
    }

    /** Tags for identifying the metric. */
    public Map<String, String> getTags() {
      return tags;
    }

    /** Value for the metric. */
    public double getValue() {
      return value;
    }
  }
}
