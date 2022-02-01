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

/**
 * Track the sample distribution of events. An example would be the response sizes for requests
 * hitting and http server.
 *
 * The precise set of information maintained depends on the implementation. Most should try to
 * provide a consistent implementation of {@link #count()} and {@link #totalAmount()},
 * but some implementations may not. In particular, the implementation from {@link NoopRegistry}
 * will always return 0.
 */
public interface DistributionSummary extends Meter {

  /**
   * Updates the statistics kept by the summary with the specified amount.
   *
   * @param amount
   *     Amount for an event being measured. For example, if the size in bytes of responses
   *     from a server. If the amount is less than 0 the value will be dropped.
   */
  void record(long amount);

  /**
   * Updates the statistics kept by the summary with the specified amounts as a batch. Behaves
   * as if `record()` was called in a loop, but may be faster in some cases.
   *
   * @param amounts
   *     Amounts for an event being measured. For example, if the size in bytes of responses
   *     from a server. If the amount is less than 0 the value will be dropped.
   * @param n
   *     The number of elements to write from the amounts array (starting from 0). If n is
   *     <= 0 no entries will be recorded. If n is greater than amounts.length, all amounts
   *     will be recorded.
   *
   * @see #record(long)
   */
  default void record(long[] amounts, int n) {
    final int limit = Math.min(amounts.length, n);
    for (int i = 0; i < limit; i++) {
      record(amounts[i]);
    }
  }

  /**
   * The number of times that record has been called since this timer was last reset.
   * How often a timer is reset depends on the underlying registry implementation.
   */
  long count();

  /**
   * The total amount of all recorded events since this summary was last reset.
   * How often a timer is reset depends on the underlying registry implementation.
   */
  long totalAmount();
}
