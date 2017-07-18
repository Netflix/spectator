/**
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spectator.api.patterns;

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Functions;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Statistic;
import com.netflix.spectator.api.Utils;

import java.util.Collections;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A counter that also keeps track of the time since last update.
 */
public final class IntervalCounter implements Counter {

  private static final double MILLIS_PER_SECOND = (double) TimeUnit.SECONDS.toMillis(1L);

  /**
   * Create a new instance.
   *
   * @param registry
   *    Registry to use.
   * @param id
   *    Identifier for the metric being registered.
   * @return
   *    Counter instance.
   */
  public static IntervalCounter get(Registry registry, Id id) {
    ConcurrentMap<Id, Object> state = registry.state();
    Object c = Utils.computeIfAbsent(state, id, i -> new IntervalCounter(registry, i));
    if (!(c instanceof IntervalCounter)) {
      Utils.propagateTypeError(registry, id, IntervalCounter.class, c.getClass());
      c = new IntervalCounter(new NoopRegistry(), id);
    }
    return (IntervalCounter) c;
  }

  private final Clock clock;
  private final Id id;
  private final Counter counter;
  private final AtomicLong lastUpdated;

  /**
   * Create a new IntervalCounter using the given registry and base id.
   */
  IntervalCounter(Registry registry, Id id) {
    this.clock = registry.clock();
    this.id = id;
    this.counter = registry.counter(id.withTag(Statistic.count));
    this.lastUpdated = registry.gauge(id.withTag(Statistic.duration), new AtomicLong(0L),
        Functions.age(clock));
  }

  @Override
  public void increment() {
    counter.increment();
    lastUpdated.set(clock.wallTime());
  }

  @Override
  public void increment(long amount) {
    counter.increment(amount);
    lastUpdated.set(clock.wallTime());
  }

  @Override
  public long count() {
    return counter.count();
  }

  @Override
  public Id id() {
    return id;
  }

  /**
   * Return the number of seconds since the last time the counter was incremented.
   */
  public double secondsSinceLastUpdate() {
    final long now = clock.wallTime();
    return  (now - lastUpdated.get()) / MILLIS_PER_SECOND;
  }

  @Override
  public Iterable<Measurement> measure() {
    return Collections.emptyList();
  }

  @Override
  public boolean hasExpired() {
    return false;
  }
}
