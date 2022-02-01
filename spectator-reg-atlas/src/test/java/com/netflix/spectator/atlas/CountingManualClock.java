/*
 * Copyright 2022-2022 Netflix, Inc.
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

import java.util.concurrent.atomic.AtomicLong;

import com.netflix.spectator.api.ManualClock;

/**
 * Clock implementation that allows the user to explicitly control the time, and also
 * keeps a count of the number of times it was polled. Used in tests to assert the count of
 * times the clock has been called.
 */
public class CountingManualClock extends ManualClock {
  private final AtomicLong countPolled;

  /** Create a new instance. */
  public CountingManualClock() {
    this(0L, 0L);
  }

  /**
   * Create a new instance.
   *
   * @param wallInit
   *     Initial value for the wall time.
   * @param monotonicInit
   *     Initial value for the monotonic time.
   */
  public CountingManualClock(long wallInit, long monotonicInit) {
    super(wallInit, monotonicInit);
    countPolled = new AtomicLong(0);
  }

  @Override public long wallTime() {
    countPolled.incrementAndGet();
    return super.wallTime();
  }

  @Override public long monotonicTime() {
    countPolled.incrementAndGet();
    return super.monotonicTime();
  }

  public long countPolled() {
    return countPolled.get();
  }
}
