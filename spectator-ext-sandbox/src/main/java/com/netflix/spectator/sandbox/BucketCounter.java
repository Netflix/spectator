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
package com.netflix.spectator.sandbox;

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DistributionSummary;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Spectator;

import java.util.Collections;

/** Counters that get incremented based on the bucket for recorded values. */
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
    return get(Spectator.registry(), id, f);
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
    return new BucketCounter(registry, id, f);
  }

  private final Registry registry;
  private final Id id;
  private final BucketFunction f;

  /** Create a new instance. */
  BucketCounter(Registry registry, Id id, BucketFunction f) {
    this.registry = registry;
    this.id = id;
    this.f = f;
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

  @Override public void record(long amount) {
    counter(f.apply(amount)).increment();
  }

  /**
   * Return the count for a given bucket.
   */
  public Counter counter(String bucket) {
    return registry.counter(id.withTag("bucket", bucket));
  }

  @Override public long count() {
    return 0L;
  }

  @Override public long totalAmount() {
    return 0L;
  }
}
