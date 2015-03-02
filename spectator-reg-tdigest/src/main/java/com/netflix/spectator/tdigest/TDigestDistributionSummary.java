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

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.DistributionSummary;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;

import java.util.Collections;

/**
 * Timer that updates a T-Digest with recorded values.
 */
public class TDigestDistributionSummary implements TDigestMeter, DistributionSummary {

  private final Id id;
  private final StepDigest digest;

  /** Create a new instance. */
  TDigestDistributionSummary(Clock clock, Id id) {
    this.id = id;
    this.digest = new StepDigest(100.0, clock, 60000L);
  }

  @Override public void record(long amount) {
    if (amount >= 0L) {
      digest.current().add(amount);
    }
  }

  /**
   * Return a percentile from the last complete digest.
   *
   * @param p
   *     Percentile to retrieve. The value should be in the interval [0.0, 100.0].
   * @return
   *     The {@code p}th percentile of the last complete digest.
   */
  public double percentile(double p) {
    if (p < 0.0 || p > 100.0) {
      throw new IllegalArgumentException("p should be in [0.0, 100.0], got " + p);
    }
    final double q = p / 100.0;
    return digest.poll().quantile(q);
  }

  @Override public long count() {
    return -1L;
  }

  @Override public long totalAmount() {
    return -1L;
  }

  @Override public Id id() {
    return id;
  }

  @Override public Iterable<Measurement> measure() {
    return Collections.emptyList();
  }

  @Override public boolean hasExpired() {
    return false;
  }

  @Override public TDigestMeasurement measureDigest() {
    return digest.measure(id());
  }
}
