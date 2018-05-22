/*
 * Copyright 2014-2017 Netflix, Inc.
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
import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Statistic;
import com.netflix.spectator.api.Timer;
import com.netflix.spectator.api.patterns.IdBuilder;
import com.netflix.spectator.api.patterns.TagsBuilder;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Timer that buckets the counts to allow for estimating percentiles. This timer type will track
 * the data distribution for the timer by maintaining a set of counters. The distribution
 * can then be used on the server side to estimate percentiles while still allowing for
 * arbitrary slicing and dicing based on dimensions.
 *
 * <p><b>Percentile timers are expensive compared to basic timers from the registry.</b> In
 * particular they have a higher storage cost, worst case ~300x, to maintain the data
 * distribution. Be diligent about any additional dimensions added to percentile timers and
 * ensure they have a small bounded cardinality. In addition it is highly recommended to
 * set a threshold (see {@link Builder#withThreshold(long, TimeUnit)}) whenever possible to
 * greatly restrict the worst case overhead.</p>
 */
public final class PercentileTimer implements Timer {

  // Precomputed values for the corresponding buckets. This is done to avoid expensive
  // String.format calls when creating new instances of a percentile variant. The
  // String.format calls uses regex internally to parse out the `%` substitutions which
  // has a lot of overhead.
  private static final String[] TAG_VALUES;

  static {
    int length = PercentileBuckets.length();
    TAG_VALUES = new String[length];
    for (int i = 0; i < length; ++i) {
      TAG_VALUES[i] = String.format("T%04X", i);
    }
  }

  /**
   * Creates a timer object that can be used for estimating percentiles. <b>Percentile timers
   * are expensive compared to basic timers from the registry.</b> Be diligent with ensuring
   * that any additional dimensions have a small bounded cardinality. It is also highly
   * recommended to explicitly set the threshold
   * (see {@link Builder#withThreshold(long, TimeUnit)}) whenever possible.
   *
   * @param registry
   *     Registry to use.
   * @param id
   *     Identifier for the metric being registered.
   * @return
   *     Timer that keeps track of counts by buckets that can be used to estimate
   *     the percentiles for the distribution.
   */
  public static PercentileTimer get(Registry registry, Id id) {
    return new PercentileTimer(registry, id, 0L, Long.MAX_VALUE);
  }

  /**
   * Return a builder for configuring and retrieving and instance of a percentile timer. If
   * the timer has dynamic dimensions, then the builder can be used with the new dimensions.
   * If the id is the same as an existing timer, then it will update the same underlying timers
   * in the registry.
   */
  public static IdBuilder<Builder> builder(Registry registry) {
    return new IdBuilder<Builder>(registry) {
      @Override protected Builder createTypeBuilder(Id id) {
        return new Builder(registry, id);
      }
    };
  }

  /**
   * Helper for getting instances of a PercentileTimer.
   */
  public static final class Builder extends TagsBuilder<Builder> {

    private Registry registry;
    private Id baseId;
    private float accuracy;
    private long max;

    /** Create a new instance. */
    Builder(Registry registry, Id baseId) {
      super();
      this.registry = registry;
      this.baseId = baseId;
      this.accuracy = 0.1f;
      this.max = Long.MAX_VALUE;
    }

    /**
     * Sets the desired accuracy for the percentile approximation when an explicit threshold is
     * set ({@link #withThreshold(long, TimeUnit)}). The accuracy flag is used to help reduce the
     * cost of the percentile approximation by trading off accuracy for percentiles that are
     * further away from the known SLA or failure threshold.
     *
     * @param accuracy
     *     Value from 0.0 (least accurate) to 1.0 (most accurate). Default is 0.1.
     * @return
     *     This builder instance to allow chaining of operations.
     */
    public Builder withAccuracy(float accuracy) {
      if (accuracy < 0.0f || accuracy > 1.0f) {
        // Invalid value provided, use default.
        IllegalArgumentException e = new IllegalArgumentException(
            "Invalid accuracy value for PercentileTimer [" + baseId + "]. Expected value"
            + " between 0 and 1, received " + accuracy + ".");
        registry.propagate(e);
        this.accuracy = 0.1f;
      } else {
        // Use the user selection.
        this.accuracy = accuracy;
      }
      return this;
    }

    /**
     * Sets the threshold for this timer. For more information see
     * {@link #withThreshold(long, TimeUnit)}.
     *
     * @param duration
     *     Duration indicating the threshold for this timer.
     * @return
     *     This builder instance to allow chaining of operations.
     */
    public Builder withThreshold(Duration duration) {
      return withThreshold(duration.toNanos(), TimeUnit.NANOSECONDS);
    }

    /**
     * Sets the threshold for this timer. The threshold is should be the SLA boundary or
     * failure point for the activity. Explicitly setting the threshold allows us to optimize
     * for the important range of values and reduce the overhead associated with tracking the
     * data distribution.
     *
     * For example, suppose you are making a client call and timeout after 10 seconds. Setting
     * the threshold to 10 seconds will restrict the possible set of buckets used to those
     * approaching the boundary. So we can still detect if it is nearing failure, but percentiles
     * that are further away from the threshold may be inflated compared to the actual value.
     *
     * @param amount
     *     Amount indicating the threshold for this timer.
     * @param unit
     *     Unit for the specified amount.
     * @return
     *     This builder instance to allow chaining of operations.
     */
    public Builder withThreshold(long amount, TimeUnit unit) {
      max = unit.toNanos(amount);
      return this;
    }

    /**
     * Create or get an instance of the percentile timer with the specified settings.
     */
    public PercentileTimer build() {
      long min = 0L;
      if (max < Long.MAX_VALUE) {
        int maxPos = PercentileBuckets.indexOf(max);
        int num = Math.round(Math.max(100, maxPos) * accuracy);
        if (num < 4) {
          // Always ensure there are a few
          num = 4;
        }

        int minPos = maxPos - num;
        min = (minPos > 0) ? PercentileBuckets.get(minPos) : 0L;
      }

      final Id id = baseId.withTags(extraTags);
      return new PercentileTimer(registry, id, min, max);
    }
  }

  private final Registry registry;
  private final Id id;
  private final Timer timer;
  private final long min;
  private final long max;
  private final AtomicReferenceArray<Counter> counters;

  /** Create a new instance. */
  PercentileTimer(Registry registry, Id id, long min, long max) {
    this.registry = registry;
    this.id = id;
    this.timer = registry.timer(id);
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
    return timer.hasExpired();
  }

  // Lazily load the counter for a given bucket. This avoids the allocation for
  // creating the id and the map lookup after the first time a given bucket is
  // accessed for a timer.
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

  @Override public void record(long amount, TimeUnit unit) {
    final long nanos = restrict(unit.toNanos(amount));
    timer.record(amount, unit);
    counterFor(PercentileBuckets.indexOf(nanos)).increment();
  }

  @Override public <T> T record(Callable<T> rf) throws Exception {
    final Clock clock = registry.clock();
    final long s = clock.monotonicTime();
    try {
      return rf.call();
    } finally {
      final long e = clock.monotonicTime();
      record(e - s, TimeUnit.NANOSECONDS);
    }
  }

  @Override public void record(Runnable rf) {
    final Clock clock = registry.clock();
    final long s = clock.monotonicTime();
    try {
      rf.run();
    } finally {
      final long e = clock.monotonicTime();
      record(e - s, TimeUnit.NANOSECONDS);
    }
  }

  /**
   * Computes the specified percentile for this timer. The unit will be seconds.
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
    double v = PercentileBuckets.percentile(counts, p);
    return v / 1e9;
  }

  @Override public long count() {
    return timer.count();
  }

  @Override public long totalTime() {
    return timer.totalTime();
  }
}
