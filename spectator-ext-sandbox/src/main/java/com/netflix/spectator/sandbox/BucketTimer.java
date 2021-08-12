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

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Spectator;
import com.netflix.spectator.api.Timer;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Timers that get updated based on the bucket for recorded values.
 *
 * @deprecated Moved to {@code com.netflix.spectator.api.histogram} package. This is now just a
 * thin wrapper to preserve compatibility. Scheduled for removal after in Q3 2016.
 */
@Deprecated
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
    return new BucketTimer(
        com.netflix.spectator.api.histogram.BucketTimer.get(registry, id, f));
  }

  private final com.netflix.spectator.api.histogram.BucketTimer t;

  /** Create a new instance. */
  BucketTimer(com.netflix.spectator.api.histogram.BucketTimer t) {
    this.t = t;
  }

  @Override public Id id() {
    return t.id();
  }

  @Override public Iterable<Measurement> measure() {
    return t.measure();
  }

  @Override public boolean hasExpired() {
    return t.hasExpired();
  }

  @Override public void record(long amount, TimeUnit unit) {
    t.record(amount, unit);
  }

  @Override public <T> T record(Callable<T> rf) throws Exception {
    return t.record(rf);
  }

  @Override public void record(Runnable rf) {
    t.record(rf);
  }

  @Override public long count() {
    return t.count();
  }

  @Override public long totalTime() {
    return t.totalTime();
  }
}
