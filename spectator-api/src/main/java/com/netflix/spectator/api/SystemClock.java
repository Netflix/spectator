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
 * Clock implementation that uses {@link System#currentTimeMillis()} and {@link System#nanoTime()}.
 * Implemented as an enum to that the clock instance will be serializable if using in environments
 * like Spark or Flink.
 */
enum SystemClock implements Clock {

  /** Singleton instance for the system clock. */
  INSTANCE;

  @Override public long wallTime() {
    return System.currentTimeMillis();
  }

  @Override public long monotonicTime() {
    return System.nanoTime();
  }
}
