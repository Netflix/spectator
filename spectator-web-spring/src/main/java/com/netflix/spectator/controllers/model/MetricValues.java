/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spectator.controllers.model;

import com.netflix.spectator.api.Measurement;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A metric and all its tagged values
 *
 * This is only public for testing purposes so implements equals but not hash.
 */
public class MetricValues {
  /**
   * Returns a string denoting the type of Meter.
   * These are strings we've added; Spectator doesnt have this.
   */
  public String getKind() {
      return kind;
  }

  /**
   * Returns the current data point values for this metric.
   */
  public Iterable<TaggedDataPoints> getValues() {
      return dataPoints;
  }

  /**
   * Adds another measurement datapoint.
   */
  public void addMeasurement(Measurement measurement) {
    dataPoints.add(new TaggedDataPoints(measurement));
  }

  /**
   * Constructor from a single measurement datapoint.
   */
  public MetricValues(String kind, Measurement measurement) {
    this.kind = kind;
    dataPoints = new ArrayList<TaggedDataPoints>();
    dataPoints.add(new TaggedDataPoints(measurement));
  }

  /**
   * Constructor from a list of datapoints (for testing).
   */
  public MetricValues(String kind, List<TaggedDataPoints> dataPoints) {
    this.kind = kind;
    this.dataPoints = new ArrayList<TaggedDataPoints>(dataPoints);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null || !(obj instanceof MetricValues)) return false;
    MetricValues other = (MetricValues) obj;

    // Ignore the kind because spectator internally transforms it
    // into internal types that we cannot test against.
    return dataPoints.equals(other.dataPoints);
  }

  @Override
  public int hashCode() {
    return Objects.hash(kind, dataPoints);
  }

  @Override
  public String toString() {
    return String.format("%s: %s", kind, dataPoints);
  }

  private String kind;
  private List<TaggedDataPoints> dataPoints;
}
