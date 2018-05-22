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
 * Dimension indicating if it is the final attempt for a given request.
 *
 * @see IpcTagKey#attempt
 * @see IpcTagKey#attemptFinal
 */
public enum IpcAttemptFinal implements Tag {
  /** It is the final attempt for the request. */
  is_true("true"),

  /** Further attempts will be made if there is a retriable failure. */
  is_false("false");

  private final String value;

  /** Create a new instance. */
  IpcAttemptFinal(String value) {
    this.value = value;
  }

  @Override public String key() {
    return IpcTagKey.attemptFinal.key();
  }

  @Override public String value() {
    return value;
  }

  /**
   * Get the enum value associated with the boolean.
   */
  public static IpcAttemptFinal forValue(boolean v) {
    return v ? is_true : is_false;
  }
}
