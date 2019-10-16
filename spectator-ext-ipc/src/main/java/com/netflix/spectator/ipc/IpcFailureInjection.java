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
package com.netflix.spectator.ipc;

import com.netflix.spectator.api.Tag;

/**
 * Dimension indicating whether failure was injected into a request.
 *
 * @see IpcTagKey#failureInjected
 */
public enum IpcFailureInjection implements Tag {
  /** Indicates there was no failure injected into the request. */
  none,

  /** Indicates there was a fault injected into the request. */
  failure,

  /** Indicates there was latency into the request. */
  delay;

  @Override public String key() {
    return IpcTagKey.failureInjected.key();
  }

  @Override public String value() {
    return name();
  }
}
