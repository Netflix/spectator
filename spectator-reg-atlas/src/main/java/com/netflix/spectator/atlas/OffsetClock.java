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
package com.netflix.spectator.atlas;

import com.netflix.spectator.api.Clock;

/**
 * Wraps a clock implementation to allow overriding the wall clock time. This is typically
 * used to adjust the clock to the next step boundary during shutdown.
 */
class OverridableClock implements Clock {

  private final Clock impl;
  private long timestamp;

  /** Create a new instance. */
  OverridableClock(Clock impl) {
    this.impl = impl;
    this.timestamp = -1L;
  }

  /** Set the wall time to use. */
  void setWallTime(long timestamp) {
    this.timestamp = timestamp;
  }

  @Override
  public long wallTime() {
    return timestamp != -1L ? timestamp : impl.wallTime();
  }

  @Override
  public long monotonicTime() {
    return impl.monotonicTime();
  }
}
