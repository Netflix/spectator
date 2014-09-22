/**
 * Copyright 2014 Netflix, Inc.
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

import java.util.concurrent.TimeUnit;

/**
 * Wraps a timer with an {@link java.lang.AutoCloseable} so it can be used in a try with
 * resources block. The
 * {@link com.netflix.spectator.api.Timer#record(long, java.util.concurrent.TimeUnit)} call will
 * be made on close to record the duration since the object was created. Example usage:
 *
 * <pre>
 * try (AutoTimer t = new AutoTimer(registry.timer("blockTimer"))) {
 *   ... something that might throw ...
 * }
 * </pre>
 */
public class AutoTimer implements AutoCloseable {

  private final Timer timer;
  private final Clock clock;
  private final long start;

  /**
   * Create a new instance. This method will use the clock setup with the main global registry.
   *
   * @param timer
   *     Timer instance to wrap.
   */
  public AutoTimer(Timer timer) {
    this(timer, Spectator.registry().clock());
  }

  /**
   * Create a new instance.
   *
   * @param timer
   *     Timer instance to wrap.
   * @param clock
   *     Clock to use for measure the elapsed time.
   */
  public AutoTimer(Timer timer, Clock clock) {
    this.timer = timer;
    this.clock = clock;
    this.start = clock.monotonicTime();
  }

  /**
   * Returns the current measured duration.
   *
   * @param unit
   *     Unit to use for the return value.
   * @return
   *     Duration since this auto timer instance was created.
   */
  public long duration(TimeUnit unit) {
    return unit.convert(clock.monotonicTime() - start, TimeUnit.NANOSECONDS);
  }

  /**
   * Record the elapsed time since this instance of auto timer was created.
   */
  @Override public void close() {
    timer.record(clock.monotonicTime() - start, TimeUnit.NANOSECONDS);
  }
}
