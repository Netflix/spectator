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
package com.netflix.spectator.ipc;

import com.netflix.spectator.api.Tag;

/**
 * Dimension indicating the retry attempt.
 *
 * @see IpcTagKey#attempt
 * @see IpcTagKey#attemptFinal
 */
public enum IpcAttempt implements Tag {
  /** Initial request. */
  initial,

  /** Second attempt or the first retry. */
  second,

  /** A third or later attempt. */
  third_up,

  /** The attempt number cannot be determined. */
  unknown;

  @Override public String key() {
    return IpcTagKey.attempt.key();
  }

  @Override public String value() {
    return name();
  }

  /**
   * Get the appropriate tag value based on the current attempt number. The attempt number
   * should start at 1 for the initial request and increment for each retry.
   *
   * @param attempt
   *     Current attempt number.
   * @return
   *     Tag value corresponding to the attempt number.
   */
  public static IpcAttempt forAttemptNumber(int attempt) {
    IpcAttempt t;
    switch (attempt) {
      case 1:  t = initial;                             break;
      case 2:  t = second;                              break;
      default: t = (attempt >= 3) ? third_up : unknown; break;
    }
    return t;
  }
}
