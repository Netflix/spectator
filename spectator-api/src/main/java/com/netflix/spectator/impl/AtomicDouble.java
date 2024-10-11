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

import java.util.concurrent.atomic.AtomicLongFieldUpdater;

/**
 * Wrapper around AtomicLong to make working with double values easier.
 *
 * <p><b>This class is an internal implementation detail only intended for use within spectator.
 * It is subject to change without notice.</b></p>
 */
@SuppressWarnings("PMD.MissingSerialVersionUID")
public class AtomicDouble extends Number {

  private volatile long value;

  private static final AtomicLongFieldUpdater<AtomicDouble> VALUE_UPDATER =
      AtomicLongFieldUpdater.newUpdater(AtomicDouble.class, "value");

  /** Create an instance with an initial value of 0. */
  public AtomicDouble() {
    super();
  }

  /** Create an instance with an initial value of {@code init}. */
  public AtomicDouble(double init) {
    super();
    this.value = Double.doubleToLongBits(init);
  }

  /** Return the current value. */
  public double get() {
    return Double.longBitsToDouble(value);
  }

  /** Add {@code amount} to the value and return the new value. */
  public double addAndGet(double amount) {
    long v;
    double d;
    double n;
    long next;
    do {
      v = value;
      d = Double.longBitsToDouble(v);
      n = d + amount;
      next = Double.doubleToLongBits(n);
    } while (!VALUE_UPDATER.compareAndSet(this, v, next));
    return n;
  }

  /** Add {@code amount} to the value and return the previous value. */
  public double getAndAdd(double amount) {
    long v;
    double d;
    double n;
    long next;
    do {
      v = value;
      d = Double.longBitsToDouble(v);
      n = d + amount;
      next = Double.doubleToLongBits(n);
    } while (!VALUE_UPDATER.compareAndSet(this, v, next));
    return d;
  }

  /** Set the value to  {@code amount} and return the previous value. */
  public double getAndSet(double amount) {
    long v = VALUE_UPDATER.getAndSet(this, Double.doubleToLongBits(amount));
    return Double.longBitsToDouble(v);
  }

  /**
   * Set the value to  {@code amount} if the current value is {@code expect}. Return true if the
   * value was updated.
   */
  public boolean compareAndSet(double expect, double update) {
    long e = Double.doubleToLongBits(expect);
    long u = Double.doubleToLongBits(update);
    return VALUE_UPDATER.compareAndSet(this, e, u);
  }

  /** Set the current value to {@code amount}. */
  public void set(double amount) {
    value = Double.doubleToLongBits(amount);
  }

  private static boolean isLessThan(double v1, double v2) {
    return v1 < v2 || Double.isNaN(v2);
  }

  /** Set the current value to the maximum of the current value or the provided value. */
  public void min(double v) {
    if (Double.isFinite(v)) {
      double min = get();
      while (isLessThan(v, min) && !compareAndSet(min, v)) {
        min = get();
      }
    }
  }

  private static boolean isGreaterThan(double v1, double v2) {
    return v1 > v2 || Double.isNaN(v2);
  }

  /** Set the current value to the maximum of the current value or the provided value. */
  public void max(double v) {
    if (Double.isFinite(v)) {
      double max = get();
      while (isGreaterThan(v, max) && !compareAndSet(max, v)) {
        max = get();
      }
    }
  }

  @Override public int intValue() {
    return (int) get();
  }

  @Override public long longValue() {
    return (long) get();
  }

  @Override public float floatValue() {
    return (float) get();
  }

  @Override public double doubleValue() {
    return get();
  }

  @Override public String toString() {
    return Double.toString(get());
  }
}
