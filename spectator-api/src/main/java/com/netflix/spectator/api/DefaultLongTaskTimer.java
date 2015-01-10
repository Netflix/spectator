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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Long task timer implementation used by the extended registry. */
final class DefaultLongTaskTimer implements LongTaskTimer {

  private static final double NANOS_PER_SECOND = (double) TimeUnit.SECONDS.toNanos(1L);

  private final Clock clock;
  private final Id id;
  private final ConcurrentMap<Long, Long> tasks = new ConcurrentHashMap<>();
  private final AtomicLong nextTask = new AtomicLong(0L);
  private final Id activeTasksId;
  private final Id durationId;

  /** Create a new instance. */
  DefaultLongTaskTimer(Clock clock, Id id) {
    this.clock = clock;
    this.id = id;

    this.activeTasksId = id.withTag("statistic", "activeTasks");
    this.durationId = id.withTag("statistic", "duration");
  }

  @Override public Id id() {
    return id;
  }

  @Override public boolean hasExpired() {
    return false;
  }

  @Override public long start() {
    long task = nextTask.getAndIncrement();
    tasks.put(task, clock.monotonicTime());
    return task;
  }

  @Override public long stop(long task) {
    Long startTime = tasks.get(task);
    if (startTime != null) {
      tasks.remove(task);
      return clock.monotonicTime() - startTime;
    } else {
      return -1L;
    }
  }

  @Override public long duration(long task) {
    Long startTime = tasks.get(task);
    return (startTime != null) ? (clock.monotonicTime() - startTime) : -1L;
  }

  @Override public long duration() {
    long now = clock.monotonicTime();
    long sum = 0L;
    for (long startTime : tasks.values()) {
      sum += now - startTime;
    }
    return sum;
  }

  @Override public int activeTasks() {
    return tasks.size();
  }

  @Override public Iterable<Measurement> measure() {
    final List<Measurement> ms = new ArrayList<>(2);
    final long now = clock.wallTime();
    final double durationSeconds = duration() / NANOS_PER_SECOND;
    ms.add(new Measurement(durationId, now, durationSeconds));
    ms.add(new Measurement(activeTasksId, now, activeTasks()));
    return ms;
  }
}
