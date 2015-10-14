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
package com.netflix.spectator.tdigest;

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.tdunning.math.stats.TDigest;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Utility class for managing a set of TDigest instances mapped to a particular step interval.
 * The current implementation keeps an array of with two items where one is the current value
 * being updated and the other is the value from the previous interval and is only available for
 * polling.
 */
class StepDigest {

  private final Counter recorded;
  private final Counter dropped;
  private final Clock clock;

  private final Id id;
  private final double init;
  private final long step;

  private final ReentrantLock lock = new ReentrantLock();

  private final AtomicReference<TDigest> previous;
  private final AtomicReference<TDigest> current;

  private final AtomicLong lastPollTime;
  private final AtomicLong lastInitPos;

  /**
   * Create a new instance.
   *
   * @param registry
   *     Underlying registry.
   * @param id
   *     Id for the meter using the digest.
   * @param init
   *     Initial compression value to use when creating the digest.
   * @param step
   *     Step size in milliseconds.
   */
  StepDigest(Registry registry, Id id, double init, long step) {
    this.recorded = registry.counter("spectator.tdigest.samples", "id", "recorded");
    this.dropped = registry.counter("spectator.tdigest.samples", "id", "dropped");
    this.clock = registry.clock();
    this.id = id;
    this.init = init;
    this.step = step;
    lastInitPos = new AtomicLong(0L);
    lastPollTime = new AtomicLong(0L);
    previous = new AtomicReference<>(TDigest.createDigest(init));
    current = new AtomicReference<>(TDigest.createDigest(init));
  }

  private void roll(long now) {
    final long stepTime = now / step;
    final long lastInit = lastInitPos.get();
    if (lastInit < stepTime && lastInitPos.compareAndSet(lastInit, stepTime)) {
      previous.set(current.getAndSet(TDigest.createDigest(init)));
    }
  }

  /** Return the id for the digest. */
  Id id() {
    return id;
  }

  /** Return the clock for the digest. */
  Clock clock() {
    return clock;
  }

  /** Return the previous digest. */
  TDigest previous() {
    roll(clock.wallTime());
    return previous.get();
  }

  /** Return the current digest. */
  TDigest current() {
    roll(clock.wallTime());
    return current.get();
  }

  /**
   * Add a value to the current digest. If another thread holds the lock the sample will be
   * dropped. We are assuming that in-practice the contention should be low and the number of
   * dropped samples would be relatively small. A dropped sample count is maintained so we
   * can collect to some data for actual usage.
   */
  void add(double v) {
    if (lock.tryLock()) {
      recorded.increment();
      try {
        current().add(v);
      } finally {
        lock.unlock();
      }
    } else {
      dropped.increment();
    }
  }

  /** Get the value for the last completed interval. */
  TDigest poll() {
    final long now = clock.wallTime();

    roll(now);
    final TDigest value = previous.get();

    final long last = lastPollTime.getAndSet(now);
    final long missed = (now - last) / step - 1;

    if (last / step == now / step) {
      return value;
    } else if (last > 0L && missed > 0L) {
      return null;
    } else {
      return value;
    }
  }

  /** Get the value for the last completed interval. */
  TDigestMeasurement measure() {
    final long t = clock.wallTime() / step * step;
    return new TDigestMeasurement(id, t, poll());
  }

  @Override public String toString() {
    return "StepDigest{init=" + init
        + ", step=" + step
        + ", lastPollTime=" + lastPollTime.get()
        + ", lastInitPos=" + lastInitPos.get() + '}';
  }
}
