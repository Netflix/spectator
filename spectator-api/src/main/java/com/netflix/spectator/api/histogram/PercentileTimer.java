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

import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Timer that buckets the counts to allow for estimating percentiles.
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
   * Creates a timer object that can be used for estimating percentiles.
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
    return new PercentileTimer(registry, id);
  }

  private final Registry registry;
  private final Id id;
  private final Timer timer;
  private final AtomicReferenceArray<Counter> counters;

  /** Create a new instance. */
  PercentileTimer(Registry registry, Id id) {
    this.registry = registry;
    this.id = id;
    this.timer = registry.timer(id);
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

  @Override public void record(long amount, TimeUnit unit) {
    final long nanos = unit.toNanos(amount);
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
