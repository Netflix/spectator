/*
 * Copyright 2014-2024 Netflix, Inc.
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

import java.util.concurrent.atomic.AtomicLongFieldUpdater;

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
  private volatile long current;

  private static final AtomicLongFieldUpdater<StepLong> CURRENT_UPDATER =
      AtomicLongFieldUpdater.newUpdater(StepLong.class, "current");

  /**
   * Wall time at which the current step interval ends. Holding this boundary rather than the step
   * index means an update landing inside the current interval is a comparison instead of a
   * division.
   */
  private volatile long nextStepBoundary;

  private static final AtomicLongFieldUpdater<StepLong> NEXT_STEP_BOUNDARY_UPDATER =
      AtomicLongFieldUpdater.newUpdater(StepLong.class, "nextStepBoundary");

  /** Create a new instance. */
  public StepLong(long init, Clock clock, long step) {
    this.init = init;
    this.clock = clock;
    this.step = step;
    previous = init;
    current = init;
    nextStepBoundary = (clock.wallTime() / step + 1) * step;
  }

  /**
   * Roll over to a new step interval if {@code now} has moved past the end of the current one.
   *
   * <p>Kept small so it inlines into the update methods. {@code step} is a non-trusted final
   * instance field, so C2 cannot constant fold it and {@code now / step} compiles to a real
   * {@code idivq} plus the divide-by-zero and {@code MIN_VALUE / -1} guards. Comparing against
   * the boundary keeps all of that off the update path and pays for the division only on an
   * actual rollover.</p>
   */
  private void rollCount(long now) {
    if (now >= nextStepBoundary) {
      rollCountSlow(now);
    }
  }

  private void rollCountSlow(long now) {
    // Boundaries are exact multiples of step, so comparing them orders the intervals the same
    // way comparing the step indices did.
    final long boundary = (now / step + 1) * step;
    final long lastBoundary = nextStepBoundary;
    // The boundary compare is not redundant with the CAS. Without it two threads that both see
    // the same boundary would both roll: the second would reset `current` again and overwrite
    // `previous` with init, publishing zero for an interval that had data.
    if (lastBoundary < boundary
        && NEXT_STEP_BOUNDARY_UPDATER.compareAndSet(this, lastBoundary, boundary)) {
      final long v = CURRENT_UPDATER.getAndSet(this, init);
      // Need to check if there was any activity during the previous step interval. If there was
      // then the init position will move forward by 1, otherwise it will be older. No activity
      // means the previous interval should be set to the `init` value.
      previous = (lastBoundary == boundary - step) ? v : init;
    }
  }

  /** Get the value for the current bucket. */
  public long getCurrent() {
    return getCurrent(clock.wallTime());
  }

  /** Get the value for the current bucket. */
  public long getCurrent(long now) {
    rollCount(now);
    return current;
  }

  /** Set the value for the current bucket. */
  public void setCurrent(long now, long value) {
    rollCount(now);
    current = value;
  }

  /** Increment the current value and return the result. */
  public long incrementAndGet(long now) {
    rollCount(now);
    return CURRENT_UPDATER.incrementAndGet(this);
  }

  /** Increment the current value and return the value before incrementing. */
  public long getAndIncrement(long now) {
    rollCount(now);
    return CURRENT_UPDATER.getAndIncrement(this);
  }

  /** Increment the current value and return the result. */
  public long addAndGet(long now, long value) {
    rollCount(now);
    return CURRENT_UPDATER.addAndGet(this, value);
  }

  /** Increment the current value and return the value before incrementing. */
  public long getAndAdd(long now, long value) {
    rollCount(now);
    return CURRENT_UPDATER.getAndAdd(this, value);
  }

  /** Set the current value and return the previous value. */
  public long getAndSet(long now, long value) {
    rollCount(now);
    return CURRENT_UPDATER.getAndSet(this, value);
  }

  /** Set the current value and return the previous value. */
  public boolean compareAndSet(long now, long expect, long update) {
    rollCount(now);
    return CURRENT_UPDATER.compareAndSet(this, expect, update);
  }

  /** Set the current value to the minimum of the current value or the provided value. */
  public void min(long now, long value) {
    rollCount(now);
    long min = current;
    while (value < min && !CURRENT_UPDATER.compareAndSet(this, min, value)) {
      min = current;
    }
  }

  /** Set the current value to the maximum of the current value or the provided value. */
  public void max(long now, long value) {
    rollCount(now);
    long max = current;
    while (value > max && !CURRENT_UPDATER.compareAndSet(this, max, value)) {
      max = current;
    }
  }

  /** Get the value for the last completed interval. */
  public long poll() {
    return poll(clock.wallTime());
  }

  /** Get the value for the last completed interval. */
  public long poll(long now) {
    rollCount(now);
    return previous;
  }

  /** Get the value for the last completed interval as a rate per second. */
  @Override public double pollAsRate() {
    return pollAsRate(clock.wallTime());
  }

  /** Get the value for the last completed interval as a rate per second. */
  @Override public double pollAsRate(long now) {
    final long amount = poll(now);
    final double period = step / 1000.0;
    return amount / period;
  }

  /** Get the timestamp for the end of the last completed interval. */
  @Override public long timestamp() {
    // Start of the current interval, which is the end of the last completed one. Equivalent to
    // the lastInitPos * step this used to compute.
    return nextStepBoundary - step;
  }

  @Override public String toString() {
    return "StepLong{init="  + init
        + ", previous=" + previous
        + ", current=" + current
        + ", lastInitPos=" + (nextStepBoundary / step - 1) + '}';
  }
}
