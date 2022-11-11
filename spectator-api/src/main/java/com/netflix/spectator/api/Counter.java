/*
 * Copyright 2014-2022 Netflix, Inc.
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

  /**
   * Returns a helper that can be used to more efficiently update the counter within a
   * single thread. For example, if you need to update a meter within a loop where the
   * rest of the loop body is fairly cheap, the instrumentation code may add considerable
   * overhead if done in the loop body. A batched updater can offset a fair amount of that
   * cost, but the updates may be delayed a bit in reaching the meter. The updates will only
   * be seen after the updater is explicitly flushed.
   *
   * The caller should ensure that the updater is closed after using to guarantee any resources
   * associated with it are cleaned up. In some cases failure to close the updater could result
   * in a memory leak.
   *
   * @param batchSize
   *     Number of updates to batch before forcing a flush to the meter.
   * @return
   *     Batch updater implementation for this meter.
   */
  default BatchUpdater batchUpdater(int batchSize) {
    return new CounterBatchUpdater(this, batchSize);
  }

  /** See {@link #batchUpdater(int)}. */
  interface BatchUpdater extends AutoCloseable {
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

    /** Push updates to the associated counter. */
    void flush();
  }
}
