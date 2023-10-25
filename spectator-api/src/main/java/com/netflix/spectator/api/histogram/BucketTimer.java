/*
 * Copyright 2014-2023 Netflix, Inc.
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

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Timer;
import com.netflix.spectator.api.Utils;

import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.LongFunction;

/** Timers that get updated based on the bucket for recorded values. */
public final class BucketTimer implements Timer {

  /**
   * Creates a timer object that manages a set of timers based on the bucket
   * function supplied. Calling record will be mapped to the record on the appropriate timer.
   *
   * @param registry
   *     Registry to use.
   * @param id
   *     Identifier for the metric being registered.
   * @param f
   *     Function to map values to buckets. See {@link BucketFunctions} for more information.
   * @return
   *     Timer that manages sub-timers based on the bucket function.
   */
  public static BucketTimer get(Registry registry, Id id, LongFunction<String> f) {
    return new BucketTimer(registry, id, f);
  }

  private final Registry registry;
  private final Id id;
  private final LongFunction<String> f;
  private final ConcurrentHashMap<String, Timer> timers;
  private final Function<String, Timer> timerFactory;

  /** Create a new instance. */
  BucketTimer(Registry registry, Id id, LongFunction<String> f) {
    this.registry = registry;
    this.id = id;
    this.f = f;
    this.timers = new ConcurrentHashMap<>();
    this.timerFactory = k -> registry.timer(id.withTag("bucket", k));
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

  @Override public Clock clock() {
    return registry.clock();
  }

  @Override public void record(long amount, TimeUnit unit) {
    final long nanos = unit.toNanos(amount);
    timer(f.apply(nanos)).record(amount, unit);
  }

  /** Return the timer for a given bucket. */
  Timer timer(String bucket) {
    return Utils.computeIfAbsent(timers, bucket, timerFactory);
  }

  /** Not supported, will always return 0. */
  @Override public long count() {
    return 0L;
  }

  /** Not supported, will always return 0. */
  @Override public long totalTime() {
    return 0L;
  }
}
