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

import com.netflix.spectator.api.DistributionSummary;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;

/**
 * Timer that updates a T-Digest with recorded values.
 */
class TDigestDistributionSummary implements TDigestMeter, DistributionSummary {

  private final StepDigest digest;
  private final DistributionSummary underlying;

  /** Create a new instance. */
  TDigestDistributionSummary(StepDigest digest, DistributionSummary underlying) {
    this.digest = digest;
    this.underlying = underlying;
  }

  @Override public void record(long amount) {
    if (amount >= 0L) {
      digest.add(amount);
      underlying.record(amount);
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
    return underlying.count();
  }

  @Override public long totalAmount() {
    return underlying.totalAmount();
  }

  @Override public Id id() {
    return digest.id();
  }

  @Override public Iterable<Measurement> measure() {
    return underlying.measure();
  }

  @Override public boolean hasExpired() {
    return false;
  }

  @Override public TDigestMeasurement measureDigest() {
    return digest.measure();
  }
}
