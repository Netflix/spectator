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
import java.util.Objects;


/**
 * A serializable Measurement.
 * Measurement is a value at a given time.
 *
 * The id is implicit by the parent node containing this DataPoint.
 *
 * This is only public for testing purposes so implements equals but not hash.
 */
public class DataPoint {
  /**
   * Factory method to create a DataPoint from a Measurement.
   */
  public static DataPoint make(Measurement m) {
    return new DataPoint(m.timestamp(), m.value());
  }

  /**
   * The measurement timestamp.
   */
  public long getT() {
      return timestamp;
  }

  /**
   * The measurement value.
   */
  public double getV() {
      return value;
  }

  /**
   * Constructor.
   */
  public DataPoint(long timestamp, double value) {
    this.timestamp = timestamp;
    this.value = value;
  }

  /**
   * Adds another Measurment to this datapoint where multiple
   * measurements should be combined together (typically an AggrMeter).
   */
  public void aggregate(Measurement m) {
    if (m.timestamp() > this.timestamp) {
      this.timestamp = m.timestamp();
    }
    this.value += m.value();
  }

  @Override
  public String toString() {
    return String.format("t=%d, v=%f", timestamp, value);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null || !(obj instanceof DataPoint)) return false;
    DataPoint other = (DataPoint) obj;
    return timestamp == other.timestamp && (Math.abs(value - other.value) < 0.001);
  }

  @Override
  public int hashCode() {
    return Objects.hash(timestamp, value);
  }

  private long timestamp;
  private double value;
};

