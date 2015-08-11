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

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Spectator;
import com.netflix.spectator.api.Timer;

import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/** Timers that get updated based on the bucket for recorded values. */
public final class BucketTimer implements Timer {

  /**
   * Creates a timer object that manages a set of timers based on the bucket
   * function supplied. Calling record will be mapped to the record on the appropriate timer.
   *
   * @param id
   *     Identifier for the metric being registered.
   * @param f
   *     Function to map values to buckets.
   * @return
   *     Timer that manages sub-timers based on the bucket function.
   */
  public static BucketTimer get(Id id, BucketFunction f) {
    return get(Spectator.globalRegistry(), id, f);
  }

  /**
   * Creates a timer object that manages a set of timers based on the bucket
   * function supplied. Calling record will be mapped to the record on the appropriate timer.
   *
   * @param registry
   *     Registry to use.
   * @param id
   *     Identifier for the metric being registered.
   * @param f
   *     Function to map values to buckets.
   * @return
   *     Timer that manages sub-timers based on the bucket function.
   */
  public static BucketTimer get(Registry registry, Id id, BucketFunction f) {
    return new BucketTimer(registry, id, f);
  }

  private final Registry registry;
  private final Id id;
  private final BucketFunction f;

  /** Create a new instance. */
  BucketTimer(Registry registry, Id id, BucketFunction f) {
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

  @Override public void record(long amount, TimeUnit unit) {
    final long nanos = unit.toNanos(amount);
    timer(f.apply(nanos)).record(amount, unit);
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
   * Return the timer for a given bucket.
   */
  public Timer timer(String bucket) {
    return registry.timer(id.withTag("bucket", bucket));
  }

  @Override public long count() {
    return 0L;
  }

  @Override public long totalTime() {
    return 0L;
  }
}
