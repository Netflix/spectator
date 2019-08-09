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
 * A timing source that can be used to access the current wall time as well as a high resolution
 * monotonic time to measuring elapsed times. Most of the time the {@link #SYSTEM} implementation
 * that calls the builtin java methods is probably the right one to use. Other implementations
 * would typically only get used for unit tests or other cases where precise control of the clock
 * is needed.
 */
public interface Clock {
  /**
   * Current wall time in milliseconds since the epoch. Typically equivalent to
   * System.currentTimeMillis.
   */
  long wallTime();

  /**
   * Current time from a monotonic clock source. The value is only meaningful when compared with
   * another snapshot to determine the elapsed time for an operation. The difference between two
   * samples will have a unit of nanoseconds. The returned value is typically equivalent to
   * System.nanoTime.
   */
  long monotonicTime();

  /**
   * Default clock implementation based on corresponding calls in {@link java.lang.System}.
   */
  Clock SYSTEM = SystemClock.INSTANCE;
}
