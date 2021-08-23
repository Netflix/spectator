/*
 * Copyright 2014-2021 Netflix, Inc.
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
package com.netflix.spectator.api.histogram;


import com.netflix.spectator.api.DistributionSummary;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Utils;

import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.LongFunction;

/** Distribution summaries that get updated based on the bucket for recorded values. */
public final class BucketDistributionSummary implements DistributionSummary {

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
   *     Function to map values to buckets. See {@link BucketFunctions} for more information.
   * @return
   *     Distribution summary that manages sub-counters based on the bucket function.
   */
  public static BucketDistributionSummary get(Registry registry, Id id, LongFunction<String> f) {
    return new BucketDistributionSummary(registry, id, f);
  }

  private final Id id;
  private final LongFunction<String> f;
  private final ConcurrentHashMap<String, DistributionSummary> summaries;
  private final Function<String, DistributionSummary> distSummaryFactory;

  /** Create a new instance. */
  BucketDistributionSummary(Registry registry, Id id, LongFunction<String> f) {
    this.id = id;
    this.f = f;
    this.summaries = new ConcurrentHashMap<>();
    this.distSummaryFactory = k -> registry.distributionSummary(id.withTag("bucket", k));
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
    distributionSummary(f.apply(amount)).record(amount);
  }

  /** Return the distribution summary for a given bucket. */
  DistributionSummary distributionSummary(String bucket) {
    return Utils.computeIfAbsent(summaries, bucket, distSummaryFactory);
  }

  /** Not supported, will always return 0. */
  @Override public long count() {
    return 0L;
  }

  /** Not supported, will always return 0. */
  @Override public long totalAmount() {
    return 0L;
  }
}
