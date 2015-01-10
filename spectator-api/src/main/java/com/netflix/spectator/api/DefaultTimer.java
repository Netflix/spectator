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
package com.netflix.spectator.api;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Timer implementation for the default registry. */
final class DefaultTimer implements Timer {

  private final Clock clock;
  private final Id id;
  private final AtomicLong count;
  private final AtomicLong totalTime;

  private final Id countId;
  private final Id totalTimeId;

  /** Create a new instance. */
  DefaultTimer(Clock clock, Id id) {
    this.clock = clock;
    this.id = id;
    count = new AtomicLong(0L);
    totalTime = new AtomicLong(0L);
    countId = id.withTag("statistic", "count");
    totalTimeId = id.withTag("statistic", "totalTime");
  }

  @Override public Id id() {
    return id;
  }

  @Override public boolean hasExpired() {
    return false;
  }

  @Override public void record(long amount, TimeUnit unit) {
    if (amount >= 0) {
      final long nanos = TimeUnit.NANOSECONDS.convert(amount, unit);
      totalTime.addAndGet(nanos);
      count.incrementAndGet();
    }
  }

  @Override public Iterable<Measurement> measure() {
    final long now = clock.wallTime();
    final List<Measurement> ms = new ArrayList<>(2);
    ms.add(new Measurement(countId, now, count.get()));
    ms.add(new Measurement(totalTimeId, now, totalTime.get()));
    return ms;
  }

  @Override public <T> T record(Callable<T> f) throws Exception {
    final long s = clock.monotonicTime();
    try {
      return f.call();
    } finally {
      final long e = clock.monotonicTime();
      record(e - s, TimeUnit.NANOSECONDS);
    }
  }

  @Override public void record(Runnable f) {
    final long s = clock.monotonicTime();
    try {
      f.run();
    } finally {
      final long e = clock.monotonicTime();
      record(e - s, TimeUnit.NANOSECONDS);
    }
  }

  @Override public long count() {
    return count.get();
  }

  @Override public long totalTime() {
    return totalTime.get();
  }
}
