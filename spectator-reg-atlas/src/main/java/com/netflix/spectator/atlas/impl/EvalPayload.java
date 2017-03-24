/*
 * Copyright 2014-2017 Netflix, Inc.
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

import java.util.List;
import java.util.Map;

/**
 * Wraps a list of measurements with a set of common tags. The common tags are
 * typically used for things like the application and instance id.
 *
 * <b>Classes in this package are only intended for use internally within spectator. They may
 * change at any time and without notice.</b>
 */
public final class EvalPayload {

  private final long timestamp;
  private final List<Metric> metrics;

  /** Create a new instance. */
  public EvalPayload(long timestamp, List<Metric> metrics) {
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

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    EvalPayload payload = (EvalPayload) o;
    return timestamp == payload.timestamp && metrics.equals(payload.metrics);
  }

  @Override public int hashCode() {
    int result = (int) (timestamp ^ (timestamp >>> 32));
    result = 31 * result + metrics.hashCode();
    return result;
  }

  @Override public String toString() {
    return "EvalPayload(timestamp=" + timestamp + ", metrics=" + metrics + ")";
  }

  /** Metric value. */
  public static final class Metric {
    private final String id;
    private final Map<String, String> tags;
    private final double value;

    /** Create a new instance. */
    public Metric(String id, Map<String, String> tags, double value) {
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

    @Override public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Metric metric = (Metric) o;
      return Double.compare(metric.value, value) == 0
          && id.equals(metric.id)
          && tags.equals(metric.tags);
    }

    @Override public int hashCode() {
      int result;
      long temp;
      result = id.hashCode();
      result = 31 * result + tags.hashCode();
      temp = Double.doubleToLongBits(value);
      result = 31 * result + (int) (temp ^ (temp >>> 32));
      return result;
    }

    @Override public String toString() {
      return "Metric(id=" + id + ", tags=" + tags + ", value=" + value + ")";
    }
  }
}
