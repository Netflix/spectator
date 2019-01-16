/*
 * Copyright 2014-2019 Netflix, Inc.
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

/**
 * Timer intended to track a large number of short running events. Example would be something like
 * an http request. Though "short running" is a bit subjective the assumption is that it should be
 * under a minute.
 *
 * The precise set of information maintained by the timer depends on the implementation. Most
 * should try to provide a consistent implementation of {@link #count()} and {@link #totalTime()},
 * but some implementations may not. In particular, the implementation from {@link NoopRegistry}
 * will always return 0.
 */
public interface Timer extends Meter {
  /**
   * Updates the statistics kept by the counter with the specified amount.
   *
   * @param amount
   *     Duration of a single event being measured by this timer. If the amount is less than 0
   *     the value will be dropped.
   * @param unit
   *     Time unit for the amount being recorded.
   */
  void record(long amount, TimeUnit unit);

  /**
   * Updates the statistics kept by the counter with the specified amount.
   *
   * @param amount
   *     Duration of a single event being measured by this timer.
   */
  default void record(Duration amount) {
    record(amount.toNanos(), TimeUnit.NANOSECONDS);
  }

  /**
   * Executes the callable `f` and records the time taken.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     The return value of `f`.
   */
  <T> T record(Callable<T> f) throws Exception;

  /**
   * Executes the runnable `f` and records the time taken.
   *
   * @param f
   *     Function to execute and measure the execution time.
   */
  void record(Runnable f);

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
}
