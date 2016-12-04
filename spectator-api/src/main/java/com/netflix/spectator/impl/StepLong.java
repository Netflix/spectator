/*
 * Copyright 2014-2016 Netflix, Inc.
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
package com.netflix.spectator.impl;

import com.netflix.spectator.api.Clock;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Utility class for managing a set of AtomicLong instances mapped to a particular step interval.
 * The current implementation keeps an array of with two items where one is the current value
 * being updated and the other is the value from the previous interval and is only available for
 * polling.
 *
 * <p><b>This class is an internal implementation detail only intended for use within spectator.
 * It is subject to change without notice.</b></p>
 */
public class StepLong implements StepValue {

  private final long init;
  private final Clock clock;
  private final long step;

  private volatile long previous;
  private final AtomicLong current;

  private final AtomicLong lastInitPos;

  /** Create a new instance. */
  public StepLong(long init, Clock clock, long step) {
    this.init = init;
    this.clock = clock;
    this.step = step;
    previous = init;
    current = new AtomicLong(init);
    lastInitPos = new AtomicLong(clock.wallTime() / step);
  }

  private void rollCount(long now) {
    final long stepTime = now / step;
    final long lastInit = lastInitPos.get();
    if (lastInit < stepTime && lastInitPos.compareAndSet(lastInit, stepTime)) {
      final long v = current.getAndSet(init);
      // Need to check if there was any activity during the previous step interval. If there was
      // then the init position will move forward by 1, otherwise it will be older. No activity
      // means the previous interval should be set to the `init` value.
      previous = (lastInit == stepTime - 1) ? v : init;
    }
  }

  /** Get the AtomicLong for the current bucket. */
  public AtomicLong getCurrent() {
    rollCount(clock.wallTime());
    return current;
  }

  /** Get the value for the last completed interval. */
  public long poll() {
    rollCount(clock.wallTime());
    return previous;
  }

  /** Get the value for the last completed interval as a rate per second. */
  @Override public double pollAsRate() {
    final long amount = poll();
    final double period = step / 1000.0;
    return amount / period;
  }

  /** Get the timestamp for the end of the last completed interval. */
  @Override public long timestamp() {
    return lastInitPos.get() * step;
  }

  @Override public String toString() {
    return "StepLong{init="  + init
        + ", previous=" + previous
        + ", current=" + current.get()
        + ", lastInitPos=" + lastInitPos.get() + '}';
  }
}
