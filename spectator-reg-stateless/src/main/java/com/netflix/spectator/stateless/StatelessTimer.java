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
package com.netflix.spectator.stateless;

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Statistic;
import com.netflix.spectator.api.Timer;
import com.netflix.spectator.impl.AtomicDouble;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongToDoubleFunction;

/**
 * Timer that keeps track of the deltas since the last time it was measured.
 */
class StatelessTimer extends StatelessMeter implements Timer {

  private final AtomicLong count;
  private final AtomicDouble totalTime;
  private final AtomicDouble totalOfSquares;
  private final AtomicDouble max;

  private final Id[] stats;

  /** Create a new instance. */
  StatelessTimer(Id id, Clock clock, long ttl) {
    super(id, clock, ttl);
    count = new AtomicLong(0);
    totalTime = new AtomicDouble(0);
    totalOfSquares = new AtomicDouble(0.0);
    max = new AtomicDouble(0);
    stats = new Id[] {
        id.withTags(Statistic.count),
        id.withTags(Statistic.totalTime),
        id.withTags(Statistic.totalOfSquares),
        id.withTags(Statistic.max)
    };
  }

  @Override public void record(long amount, TimeUnit unit) {
    final double seconds = unit.toNanos(amount) / 1e9;
    if (seconds >= 0.0) {
      count.incrementAndGet();
      totalTime.addAndGet(seconds);
      totalOfSquares.addAndGet(seconds * seconds);
      max.max(seconds);
      updateLastModTime();
    }
  }

  @Override public <T> T record(Callable<T> f) throws Exception {
    final long start = clock.monotonicTime();
    try {
      return f.call();
    } finally {
      record(clock.monotonicTime() - start, TimeUnit.NANOSECONDS);
    }
  }

  @Override public void record(Runnable f) {
    final long start = clock.monotonicTime();
    try {
      f.run();
    } finally {
      record(clock.monotonicTime() - start, TimeUnit.NANOSECONDS);
    }
  }

  @Override public long count() {
    return count.get();
  }

  @Override public long totalTime() {
    return (long) (totalTime.get() * 1e9);
  }

  @Override
  public Iterable<Measurement> measure() {
    if (count.get() == 0) {
      return Collections.emptyList();
    } else {
      List<Measurement> ms = new ArrayList<>(4);
      ms.add(newMeasurement(stats[0], count::getAndSet));
      ms.add(newMeasurement(stats[1], totalTime::getAndSet));
      ms.add(newMeasurement(stats[2], totalOfSquares::getAndSet));
      ms.add(newMeasurement(stats[3], max::getAndSet));
      return ms;
    }
  }

  private Measurement newMeasurement(Id mid, LongToDoubleFunction getAndSet) {
    double delta = getAndSet.applyAsDouble(0);
    long timestamp = clock.wallTime();
    return new Measurement(mid, timestamp, delta);
  }
}
