/*
 * Copyright 2014-2023 Netflix, Inc.
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

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Timer intended to track a large number of short running events. Example would be something like
 * an http request. Though "short running" is a bit subjective the assumption is that it should be
 * under a minute.
 *
 * <p>The precise set of information maintained by the timer depends on the implementation. Most
 * should try to provide a consistent implementation of {@link #count()} and {@link #totalTime()},
 * but some implementations may not. In particular, the implementation from {@link NoopRegistry}
 * will always return 0.
 */
public interface Timer extends Meter {

  /**
   * The clock used for timing events.
   */
  default Clock clock() {
    return Clock.SYSTEM;
  }

  /**
   * Updates the statistics kept by the timer with the specified amount.
   *
   * @param amount
   *     Duration of a single event being measured by this timer. If the amount is less than 0
   *     the value will be dropped.
   * @param unit
   *     Time unit for the amount being recorded.
   */
  void record(long amount, TimeUnit unit);

  /**
   * Updates the statistics kept by the timer with the specified amount.
   *
   * @param amount
   *     Duration of a single event being measured by this timer.
   */
  default void record(Duration amount) {
    record(amount.toNanos(), TimeUnit.NANOSECONDS);
  }

  /**
   * Updates the statistics kept by the timer with the specified amounts. Behaves as if
   * `record()` was called in a loop, but may be faster in some cases.
   *
   * @param amounts
   *     Duration of events being measured by this timer. If the amount is less than 0
   *     for a value, the value will be dropped.
   * @param n
   *     The number of elements to write from the amounts array (starting from 0). If n is
   *     &lt;= 0 no entries will be recorded. If n is greater than amounts.length, all amounts
   *     will be recorded.
   * @param unit
   *     Time unit for the amounts being recorded.
   *
   * @see #record(long, TimeUnit)
   */
  default void record(long[] amounts, int n, TimeUnit unit) {
    final int limit = Math.min(amounts.length, n);
    for (int i = 0; i < limit; i++) {
      record(amounts[i], unit);
    }
  }

  /**
   * Updates the statistics kept by the timer with the specified amounts. Behaves as if
   * `record()` was called in a loop, but may be faster in some cases.
   *
   * @param amounts
   *     Duration of events being measured by this timer. If the amount is less than 0
   *     for a value, the value will be dropped.
   * @param n
   *     The number of elements to write from the amounts array (starting from 0). If n is
   *     <= 0 no entries will be recorded. If n is greater than amounts.length, all amounts
   *     will be recorded.
   *
   * @see #record(Duration)
   */
  default void record(Duration[] amounts, int n) {
    final int limit = Math.min(amounts.length, n);
    for (int i = 0; i < limit; i++) {
      record(amounts[i]);
    }
  }

  /**
   * Executes the callable `f` and records the time taken.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     The return value of `f`.
   *
   * @deprecated Use {@link #recordCallable(Callable)} instead.
   */
  @Deprecated
  default <T> T record(Callable<T> f) throws Exception {
    return recordCallable(f);
  }

  /**
   * Executes the runnable `f` and records the time taken.
   *
   * @param f
   *     Function to execute and measure the execution time.
   *
   * @deprecated Use {@link #recordRunnable(Runnable)} instead.
   */
  @Deprecated
  default void record(Runnable f) {
    recordRunnable(f);
  }

  /**
   * Executes the callable `f` and records the time taken.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     The return value of `f`.
   */
  default <T> T recordCallable(Callable<T> f) throws Exception {
    final Clock clock = clock();
    long s = clock.monotonicTime();
    try {
      return f.call();
    } finally {
      long e = clock.monotonicTime();
      record(e - s, TimeUnit.NANOSECONDS);
    }
  }

  /**
   * Executes the runnable `f` and records the time taken.
   *
   * @param f
   *     Function to execute and measure the execution time.
   */
  default void recordRunnable(Runnable f) {
    final Clock clock = clock();
    long s = clock.monotonicTime();
    try {
      f.run();
    } finally {
      long e = clock.monotonicTime();
      record(e - s, TimeUnit.NANOSECONDS);
    }
  }

  /**
   * Executes the callable `f` and records the time taken.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     The return value of `f`.
   */
  default <T> T recordSupplier(Supplier<T> f) {
    final Clock clock = clock();
    long s = clock.monotonicTime();
    try {
      return f.get();
    } finally {
      long e = clock.monotonicTime();
      record(e - s, TimeUnit.NANOSECONDS);
    }
  }

  /**
   * Executes the callable `f` and records the time taken.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     The return value of `f`.
   */
  default boolean recordBooleanSupplier(BooleanSupplier f) {
    final Clock clock = clock();
    long s = clock.monotonicTime();
    try {
      return f.getAsBoolean();
    } finally {
      long e = clock.monotonicTime();
      record(e - s, TimeUnit.NANOSECONDS);
    }
  }

  /**
   * Executes the callable `f` and records the time taken.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     The return value of `f`.
   */
  default double recordDoubleSupplier(DoubleSupplier f) {
    final Clock clock = clock();
    long s = clock.monotonicTime();
    try {
      return f.getAsDouble();
    } finally {
      long e = clock.monotonicTime();
      record(e - s, TimeUnit.NANOSECONDS);
    }
  }

  /**
   * Executes the callable `f` and records the time taken.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     The return value of `f`.
   */
  default int recordIntSupplier(IntSupplier f) {
    final Clock clock = clock();
    long s = clock.monotonicTime();
    try {
      return f.getAsInt();
    } finally {
      long e = clock.monotonicTime();
      record(e - s, TimeUnit.NANOSECONDS);
    }
  }

  /**
   * Executes the callable `f` and records the time taken.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     The return value of `f`.
   */
  default long recordLongSupplier(LongSupplier f) {
    final Clock clock = clock();
    long s = clock.monotonicTime();
    try {
      return f.getAsLong();
    } finally {
      long e = clock.monotonicTime();
      record(e - s, TimeUnit.NANOSECONDS);
    }
  }

  /**
   * The number of times that record has been called since this timer was last reset.
   * How often a timer is reset depends on the underlying registry implementation.
   */
  long count();

  /**
   * The total time in nanoseconds of all recorded events since this timer was last reset.
   * How often a timer is reset depends on the underlying registry implementation.
   */
  long totalTime();

  /**
   * Returns a helper that can be used to more efficiently update the timer within a
   * single thread. For example, if you need to update a meter within a loop where the
   * rest of the loop body is fairly cheap, the instrumentation code may add considerable
   * overhead if done in the loop body. A batched updater can offset a fair amount of that
   * cost, but the updates may be delayed a bit in reaching the meter. The updates will only
   * be seen after the updater is explicitly flushed.
   *
   * <p>The caller should ensure that the updater is closed after using to guarantee any resources
   * associated with it are cleaned up. In some cases failure to close the updater could result
   * in a memory leak.
   *
   * @param batchSize
   *     Number of updates to batch before forcing a flush to the meter.
   * @return
   *     Batch updater implementation for this meter.
   */
  default BatchUpdater batchUpdater(int batchSize) {
    return new TimerBatchUpdater(this, batchSize);
  }

  /** See {@link #batchUpdater(int)}. */
  interface BatchUpdater extends AutoCloseable {
    /**
     * Updates the statistics kept by the timer with the specified amount.
     *
     * @param amount
     *     Duration of a single event being measured by this timer. If the amount is less than 0
     *     the value will be dropped.
     * @param unit
     *     Time unit for the amount being recorded.
     */
    void record(long amount, TimeUnit unit);

    /**
     * Updates the statistics kept by the timer with the specified amount.
     *
     * @param amount
     *     Duration of a single event being measured by this timer.
     */
    default void record(Duration amount) {
      record(amount.toNanos(), TimeUnit.NANOSECONDS);
    }

    /** Push updates to the associated timer. */
    void flush();
  }
}
