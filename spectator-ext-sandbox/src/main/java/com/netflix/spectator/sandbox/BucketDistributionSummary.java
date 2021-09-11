/*
 * Copyright 2014-2019 Netflix, Inc.
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
package com.netflix.spectator.sandbox;

import com.netflix.spectator.api.DistributionSummary;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Spectator;

/**
 * Distribution summaries that get updated based on the bucket for recorded values.
 *
 * @deprecated Moved to {@code com.netflix.spectator.api.histogram} package. This is now just a
 * thin wrapper to preserve compatibility. This class is scheduled for removal in a future release.
 */
@Deprecated
public final class BucketDistributionSummary implements DistributionSummary {

  /**
   * Creates a distribution summary object that manages a set of distribution summaries based on
   * the bucket function supplied. Calling record will be mapped to the record on the appropriate
   * distribution summary.
   *
   * @param id
   *     Identifier for the metric being registered.
   * @param f
   *     Function to map values to buckets.
   * @return
   *     Distribution summary that manages sub-counters based on the bucket function.
   */
  public static BucketDistributionSummary get(Id id, BucketFunction f) {
    return get(Spectator.globalRegistry(), id, f);
  }

  /**
   * Creates a distribution summary object that manages a set of distribution summaries based on
   * the bucket function supplied. Calling record will be mapped to the record on the appropriate
   * distribution summary.
   *
   * @param registry
   *     Registry to use.
   * @param id
   *     Identifier for the metric being registered.
   * @param f
   *     Function to map values to buckets.
   * @return
   *     Distribution summary that manages sub-counters based on the bucket function.
   */
  public static BucketDistributionSummary get(Registry registry, Id id, BucketFunction f) {
    return new BucketDistributionSummary(
        com.netflix.spectator.api.histogram.BucketDistributionSummary.get(registry, id, f));
  }

  private final com.netflix.spectator.api.histogram.BucketDistributionSummary s;

  /** Create a new instance. */
  BucketDistributionSummary(com.netflix.spectator.api.histogram.BucketDistributionSummary s) {
    this.s = s;
  }

  @Override public Id id() {
    return s.id();
  }

  @Override public Iterable<Measurement> measure() {
    return s.measure();
  }

  @Override public boolean hasExpired() {
    return s.hasExpired();
  }

  @Override public void record(long amount) {
    s.record(amount);
  }

  @Override public long count() {
    return s.count();
  }

  @Override public long totalAmount() {
    return s.totalAmount();
  }
}
