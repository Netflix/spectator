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
package com.netflix.spectator.servo;

import com.netflix.servo.util.Clock;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Utility class for managing a set of AtomicLong instances mapped to a particular step interval.
 * The current implementation keeps an array of with two items where one is the current value
 * being updated and the other is the value from the previous interval and is only available for
 * polling.
 */
class StepLong {
  private static final int PREVIOUS = 0;
  private static final int CURRENT = 1;

  private final long init;
  private final Clock clock;

  private final AtomicLong[] data;

  private final AtomicLong[] lastPollTime;

  private final AtomicLong[] lastInitPos;

  /** Create a new instance. */
  StepLong(long init, Clock clock) {
    this.init = init;
    this.clock = clock;
    lastInitPos = new AtomicLong[ServoPollers.NUM_POLLERS];
    lastPollTime = new AtomicLong[ServoPollers.NUM_POLLERS];
    for (int i = 0; i < ServoPollers.NUM_POLLERS; ++i) {
      lastInitPos[i] = new AtomicLong(0L);
      lastPollTime[i] = new AtomicLong(0L);
    }
    data = new AtomicLong[2 * ServoPollers.NUM_POLLERS];
    for (int i = 0; i < data.length; ++i) {
      data[i] = new AtomicLong(init);
    }
  }

  /** Add to the current bucket. */
  void addAndGet(long amount) {
    for (int i = 0; i < ServoPollers.NUM_POLLERS; ++i) {
      getCurrent(i).addAndGet(amount);
    }
  }

  private void rollCount(int pollerIndex, long now) {
    final long step = ServoPollers.POLLING_INTERVALS[pollerIndex];
    final long stepTime = now / step;
    final long lastInit = lastInitPos[pollerIndex].get();
    if (lastInit < stepTime && lastInitPos[pollerIndex].compareAndSet(lastInit, stepTime)) {
      final int prev = 2 * pollerIndex + PREVIOUS;
      final int curr = 2 * pollerIndex + CURRENT;
      data[prev].set(data[curr].getAndSet(init));
    }
  }

  /** Get the AtomicLong for the current bucket. */
  AtomicLong getCurrent(int pollerIndex) {
    rollCount(pollerIndex, clock.now());
    return data[2 * pollerIndex + CURRENT];
  }

  /** Get the value for the last completed interval. */
  Long poll(int pollerIndex) {
    final long now = clock.now();
    final long step = ServoPollers.POLLING_INTERVALS[pollerIndex];

    rollCount(pollerIndex, now);
    final int prevPos = 2 * pollerIndex + PREVIOUS;
    final long value = data[prevPos].get();

    final long last = lastPollTime[pollerIndex].getAndSet(now);
    final long missed = (now - last) / step - 1;

    if (last / step == now / step) {
      return value;
    } else if (last > 0L && missed > 0L) {
      return null;
    } else {
      return value;
    }
  }

  @Override public String toString() {
    return "StepLong{init="  + init
        + ", data=" + Arrays.toString(data)
        + ", lastPollTime=" + Arrays.toString(lastPollTime)
        + ", lastInitPos=" + Arrays.toString(lastInitPos) + '}';
  }
}
