/*
 * Copyright 2014-2024 Netflix, Inc.
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

package com.netflix.spectator.jvm;

import com.netflix.spectator.api.Registry;

import java.util.concurrent.Executor;

/**
 * Helpers supporting continuous monitoring with Java Flight Recorder.
 */
public final class JavaFlightRecorder {

  private JavaFlightRecorder() {
  }

  /**
   * Return if Java Flight Recorder continuous monitoring is supported on the current JVM.
   */
  public static boolean isSupported() {
    return false;
  }

 /**
   * Collect low-overhead Java Flight Recorder events, using the provided
   * {@link java.util.concurrent.Executor} to execute a single task to collect events.
   * <p>
   * These measures provide parity with {@link Jmx#registerStandardMXBeans} and the
   * `spectator-ext-gc` module.
   *
   * @param registry the registry
   * @param executor the executor to execute the task for streaming events
   * @return an {@link AutoCloseable} allowing the underlying event stream to be closed
   */
  public static AutoCloseable monitorDefaultEvents(Registry registry, Executor executor) {
    throw new UnsupportedOperationException("Java Flight Recorder support is only available on Java 17 and later");
  }

}
