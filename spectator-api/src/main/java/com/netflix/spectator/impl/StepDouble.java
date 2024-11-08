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
public class StepDouble implements StepValue {

  private final double init;
  private final Clock clock;
  private final long step;

  private volatile double previous;
  private volatile long current;

  private static final AtomicLongFieldUpdater<StepDouble> CURRENT_UPDATER =
      AtomicLongFieldUpdater.newUpdater(StepDouble.class, "current");

  private volatile long lastInitPos;

  private static final AtomicLongFieldUpdater<StepDouble> LAST_INIT_POS_UPDATER =
      AtomicLongFieldUpdater.newUpdater(StepDouble.class, "lastInitPos");

  /** Create a new instance. */
  public StepDouble(double init, Clock clock, long step) {
    this.init = init;
    this.clock = clock;
    this.step = step;
    previous = init;
    current = Double.doubleToLongBits(init);
    lastInitPos = clock.wallTime() / step;
  }

  private void rollCount(long now) {
    final long stepTime = now / step;
    final long lastInit = lastInitPos;
    if (lastInit < stepTime && LAST_INIT_POS_UPDATER.compareAndSet(this, lastInit, stepTime)) {
      final double v = Double.longBitsToDouble(
          CURRENT_UPDATER.getAndSet(this, Double.doubleToLongBits(init)));
      // Need to check if there was any activity during the previous step interval. If there was
      // then the init position will move forward by 1, otherwise it will be older. No activity
      // means the previous interval should be set to the `init` value.
      previous = (lastInit == stepTime - 1) ? v : init;
    }
  }

  /** Get the value for the current bucket. */
  public double getCurrent() {
    return getCurrent(clock.wallTime());
  }

  /** Get the value for the current bucket. */
  public double getCurrent(long now) {
    rollCount(now);
    return Double.longBitsToDouble(current);
  }

  /** Set the value for the current bucket. */
  public void setCurrent(long now, double value) {
    rollCount(now);
    current = Double.doubleToLongBits(value);
  }

  /** Increment the current value and return the result. */
  public double addAndGet(long now, double amount) {
    rollCount(now);
    long v;
    double d;
    double n;
    long next;
    do {
      v = current;
      d = Double.longBitsToDouble(v);
      n = d + amount;
      next = Double.doubleToLongBits(n);
    } while (!CURRENT_UPDATER.compareAndSet(this, v, next));
    return n;
  }

  /** Increment the current value and return the value before incrementing. */
  public double getAndAdd(long now, double amount) {
    rollCount(now);
    long v;
    double d;
    double n;
    long next;
    do {
      v = current;
      d = Double.longBitsToDouble(v);
      n = d + amount;
      next = Double.doubleToLongBits(n);
    } while (!CURRENT_UPDATER.compareAndSet(this, v, next));
    return d;
  }

  /** Set the current value and return the previous value. */
  public double getAndSet(long now, double value) {
    rollCount(now);
    long v = CURRENT_UPDATER.getAndSet(this, Double.doubleToLongBits(value));
    return Double.longBitsToDouble(v);
  }

  private boolean compareAndSet(double expect, double update) {
    long e = Double.doubleToLongBits(expect);
    long u = Double.doubleToLongBits(update);
    return CURRENT_UPDATER.compareAndSet(this, e, u);
  }

  /** Set the current value and return the previous value. */
  public boolean compareAndSet(long now, double expect, double update) {
    rollCount(now);
    return compareAndSet(expect, update);
  }

  private static boolean isLessThan(double v1, double v2) {
    return v1 < v2 || Double.isNaN(v2);
  }

  /** Set the current value to the minimum of the current value or the provided value. */
  public void min(long now, double value) {
    if (Double.isFinite(value)) {
      rollCount(now);
      double min = Double.longBitsToDouble(current);
      while (isLessThan(value, min) && !compareAndSet(min, value)) {
        min = Double.longBitsToDouble(current);
      }
    }
  }

  private static boolean isGreaterThan(double v1, double v2) {
    return v1 > v2 || Double.isNaN(v2);
  }

  /** Set the current value to the maximum of the current value or the provided value. */
  public void max(long now, double value) {
    if (Double.isFinite(value)) {
      rollCount(now);
      double max = Double.longBitsToDouble(current);
      while (isGreaterThan(value, max) && !compareAndSet(max, value)) {
        max = Double.longBitsToDouble(current);
      }
    }
  }

  /** Get the value for the last completed interval. */
  public double poll() {
    return poll(clock.wallTime());
  }

  /** Get the value for the last completed interval. */
  public double poll(long now) {
    rollCount(now);
    return previous;
  }

  /** Get the value for the last completed interval as a rate per second. */
  @Override public double pollAsRate() {
    return pollAsRate(clock.wallTime());
  }

  /** Get the value for the last completed interval as a rate per second. */
  @Override public double pollAsRate(long now) {
    final double amount = poll(now);
    final double period = step / 1000.0;
    return amount / period;
  }

  /** Get the timestamp for the end of the last completed interval. */
  @Override public long timestamp() {
    return lastInitPos * step;
  }

  @Override public String toString() {
    return "StepDouble{init="  + init
        + ", previous=" + previous
        + ", current=" + Double.longBitsToDouble(current)
        + ", lastInitPos=" + lastInitPos + '}';
  }
}
