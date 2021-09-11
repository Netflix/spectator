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
 * Counters that get incremented based on the bucket for recorded values.
 *
 * @deprecated Moved to {@code com.netflix.spectator.api.histogram} package. This is now just a
 * thin wrapper to preserve compatibility. This class is scheduled for removal in a future release.
 */
@Deprecated
public final class BucketCounter implements DistributionSummary {

  /**
   * Creates a distribution summary object that manages a set of counters based on the bucket
   * function supplied. Calling record will increment the appropriate counter.
   *
   * @param id
   *     Identifier for the metric being registered.
   * @param f
   *     Function to map values to buckets.
   * @return
   *     Distribution summary that manages sub-counters based on the bucket function.
   */
  public static BucketCounter get(Id id, BucketFunction f) {
    return get(Spectator.globalRegistry(), id, f);
  }

  /**
   * Creates a distribution summary object that manages a set of counters based on the bucket
   * function supplied. Calling record will increment the appropriate counter.
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
  public static BucketCounter get(Registry registry, Id id, BucketFunction f) {
    return new BucketCounter(
        com.netflix.spectator.api.histogram.BucketCounter.get(registry, id, f));
  }

  private final com.netflix.spectator.api.histogram.BucketCounter c;

  /** Create a new instance. */
  BucketCounter(com.netflix.spectator.api.histogram.BucketCounter c) {
    this.c = c;
  }

  @Override public Id id() {
    return c.id();
  }

  @Override public Iterable<Measurement> measure() {
    return c.measure();
  }

  @Override public boolean hasExpired() {
    return c.hasExpired();
  }

  @Override public void record(long amount) {
    c.record(amount);
  }

  @Override public long count() {
    return c.count();
  }

  @Override public long totalAmount() {
    return c.totalAmount();
  }
}
