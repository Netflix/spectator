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
package com.netflix.spectator.api;

/**
 * Measures the rate of change based on calls to increment.
 */
public interface Counter extends Meter {
  /** Update the counter by one. */
  default void increment() {
    add(1.0);
  }

  /**
   * Update the counter by {@code amount}.
   *
   * @param amount
   *     Amount to add to the counter.
   */
  default void increment(long amount) {
    add(amount);
  }

  /** Update the counter by the specified amount. */
  void add(double amount);

  /**
   * The cumulative count since this counter was last reset. How often a counter
   * is reset depends on the underlying registry implementation.
   */
  default long count() {
    return (long) actualCount();
  }

  /**
   * The cumulative count as a floating point value since this counter was last reset. How
   * often a counter is reset depends on the underlying registry implementation.
   */
  double actualCount();
}
