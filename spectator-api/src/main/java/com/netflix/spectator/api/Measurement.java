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
package com.netflix.spectator.api;

/**
 * A measurement sampled from a meter.
 */
public final class Measurement {

  private final Id id;
  private final long timestamp;
  private final double value;

  /** Create a new instance. */
  public Measurement(Id id, long timestamp, double value) {
    this.id = id;
    this.timestamp = timestamp;
    this.value = value;
  }

  /** Identifier for the measurement. */
  public Id id() {
    return id;
  }

  /**
   * The timestamp in milliseconds since the epoch for when the measurement was taken.
   */
  public long timestamp() {
    return timestamp;
  }

  /** Value for the measurement. */
  public double value() {
    return value;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || !(obj instanceof Measurement)) return false;
    Measurement other = (Measurement) obj;
    return id.equals(other.id)
      && timestamp == other.timestamp
      && Double.compare(value, other.value) == 0;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int hc = prime;
    hc = prime * hc + id.hashCode();
    hc = prime * hc + Long.valueOf(timestamp).hashCode();
    hc = prime * hc + Double.valueOf(value).hashCode();
    return hc;
  }

  @Override
  public String toString() {
    return "Measurement(" + id.toString() + "," + timestamp + "," + value + ")";
  }
}
