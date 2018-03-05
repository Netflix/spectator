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
package com.netflix.spectator.atlas;

import com.netflix.spectator.api.Counter;

/**
 * <p><b>Experimental:</b> This type may be removed in a future release.</p>
 *
 * Counter that reports a rate per second to Atlas based on the accumulation of floating
 * point amounts. This differs from the primary counter type in that the values and internal
 * storage are based on a double. Main use-case right now is for allowing total of squares
 * counter used internally to AtlasDistributionSummary and AtlasTimer to be transferred to
 * a remote AtlasRegistry. A double type is used for that use-case because squaring the amount
 * for timers easily leads to an overflow of the long value.
 */
public interface DoubleCounter extends Counter {

  /** Update the counter by the specified amount. */
  void add(double amount);

  /**
   * Current count as a double value. The {@link #count()} method returns a long to stay
   * compatible with the default counter type.
   */
  double actualCount();

  @Override default void increment() {
    add(1.0);
  }

  @Override default void increment(long amount) {
    add(amount);
  }

  @Override default long count() {
    return (long) actualCount();
  }
}
