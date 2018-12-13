/*
 * Copyright 2014-2018 Netflix, Inc.
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

import com.netflix.spectator.api.BasicTag;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DistributionSummary;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Statistic;
import com.netflix.spectator.api.patterns.IdBuilder;
import com.netflix.spectator.api.patterns.TagsBuilder;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Distribution summary that buckets the counts to allow for estimating percentiles. This
 * distribution summary type will track the data distribution for the summary by maintaining a
 * set of counters. The distribution can then be used on the server side to estimate percentiles
 * while still allowing for arbitrary slicing and dicing based on dimensions.
 *
 * <p><b>Percentile distribution summaries are expensive compared to basic distribution summaries
 * from the registry.</b> In particular they have a higher storage cost, worst case ~300x, to
 * maintain the data distribution. Be diligent about any additional dimensions added to percentile
 * distribution summaries and ensure they have a small bounded cardinality. In addition it is
 * highly recommended to set a threshold (see {@link Builder#withRange(long, long)}) whenever
 * possible to greatly restrict the worst case overhead.</p>
 */
public class PercentileDistributionSummary implements DistributionSummary {

  // Precomputed values for the corresponding buckets. This is done to avoid expensive
  // String.format calls when creating new instances of a percentile variant. The
  // String.format calls uses regex internally to parse out the `%` substitutions which
  // has a lot of overhead.
  private static final String[] TAG_VALUES;

  static {
    int length = PercentileBuckets.length();
    TAG_VALUES = new String[length];
    for (int i = 0; i < length; ++i) {
      TAG_VALUES[i] = String.format("D%04X", i);
    }
  }

  /**
   * Creates a distribution summary object that can be used for estimating percentiles.
   * <b>Percentile distribution summaries are expensive compared to basic distribution summaries
   * from the registry.</b> Be diligent with ensuring that any additional dimensions have a small
   * bounded cardinality. It is also highly recommended to explicitly set the threshold
   * (see {@link Builder#withRange(long, long)}) whenever possible.
   *
   * @param registry
   *     Registry to use.
   * @param id
   *     Identifier for the metric being registered.
   * @return
   *     Distribution summary that keeps track of counts by buckets that can be used to estimate
   *     the percentiles for the distribution.
   */
  public static PercentileDistributionSummary get(Registry registry, Id id) {
    return new PercentileDistributionSummary(registry, id, 0L, Long.MAX_VALUE);
  }

  /**
   * Return a builder for configuring and retrieving and instance of a percentile distribution
   * summary. If the distribution summary has dynamic dimensions, then the builder can be used
   * with the new dimensions. If the id is the same as an existing distribution summary, then it
   * will update the same underlying distribution summaries in the registry.
   */
  public static IdBuilder<Builder> builder(Registry registry) {
    return new IdBuilder<Builder>(registry) {
      @Override protected Builder createTypeBuilder(Id id) {
        return new Builder(registry, id);
      }
    };
  }

  /**
   * Helper for getting instances of a PercentileDistributionSummary.
   */
  public static final class Builder extends TagsBuilder<Builder> {

    private Registry registry;
    private Id baseId;
    private long min;
    private long max;

    /** Create a new instance. */
    Builder(Registry registry, Id baseId) {
      super();
      this.registry = registry;
      this.baseId = baseId;
      this.min = 0L;
      this.max = Long.MAX_VALUE;
    }

    /**
     * Sets the range for this summary. The range is should be the SLA boundary or
     * failure point for the activity. Explicitly setting the threshold allows us to optimize
     * for the important range of values and reduce the overhead associated with tracking the
     * data distribution.
     *
     * For example, suppose you are making a client call and the max payload size is 8mb. Setting
     * the threshold to 8mb will restrict the possible set of buckets used to those approaching
     * the boundary. So we can still detect if it is nearing failure, but percentiles
     * that are further away from the range may be inflated compared to the actual value.
     *
     * @param min
     *     Amount indicating the minimum allowed value for this summary.
     * @param max
     *     Amount indicating the maximum allowed value for this summary.
     * @return
     *     This builder instance to allow chaining of operations.
     */
    public Builder withRange(long min, long max) {
      this.min = min;
      this.max = max;
      return this;
    }

    /**
     * Create or get an instance of the percentile distribution summary with the specified
     * settings.
     */
    public PercentileDistributionSummary build() {
      final Id id = baseId.withTags(extraTags);
      return new PercentileDistributionSummary(registry, id, min, max);
    }
  }

  private final Registry registry;
  private final Id id;
  private final DistributionSummary summary;
  private final long min;
  private final long max;
  private final AtomicReferenceArray<Counter> counters;

  /** Create a new instance. */
  PercentileDistributionSummary(Registry registry, Id id, long min, long max) {
    this.registry = registry;
    this.id = id;
    this.summary = registry.distributionSummary(id);
    this.min = min;
    this.max = max;
    this.counters = new AtomicReferenceArray<>(PercentileBuckets.length());
  }

  @Override public Id id() {
    return id;
  }

  @Override public Iterable<Measurement> measure() {
    return Collections.emptyList();
  }

  @Override public boolean hasExpired() {
    return summary.hasExpired();
  }

  // Lazily load the counter for a given bucket. This avoids the allocation for
  // creating the id and the map lookup after the first time a given bucket is
  // accessed for a distribution summary.
  private Counter counterFor(int i) {
    Counter c = counters.get(i);
    if (c == null) {
      Id counterId = id.withTags(Statistic.percentile, new BasicTag("percentile", TAG_VALUES[i]));
      c = registry.counter(counterId);
      counters.set(i, c);
    }
    return c;
  }

  private long restrict(long amount) {
    long v = Math.min(amount, max);
    return Math.max(v, min);
  }

  @Override public void record(long amount) {
    if (amount >= 0L) {
      summary.record(amount);
      counterFor(PercentileBuckets.indexOf(restrict(amount))).increment();
    }
  }

  /**
   * Computes the specified percentile for this distribution summary.
   *
   * @param p
   *     Percentile to compute, value must be {@code 0.0 <= p <= 100.0}.
   * @return
   *     An approximation of the {@code p}`th percentile in seconds.
   */
  public double percentile(double p) {
    long[] counts = new long[PercentileBuckets.length()];
    for (int i = 0; i < counts.length; ++i) {
      counts[i] = counterFor(i).count();
    }
    return PercentileBuckets.percentile(counts, p);
  }

  @Override public long count() {
    return summary.count();
  }

  @Override public long totalAmount() {
    return summary.totalAmount();
  }
}
