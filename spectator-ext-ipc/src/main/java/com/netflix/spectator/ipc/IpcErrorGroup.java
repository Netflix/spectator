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
 * Dimension indicating a high level cause for the request failure. These groups
 * are the same for all implementations to make it easier to query across all
 * services and client implementations. An implementation specific failure can be
 * specified with {@link IpcTagKey#errorReason}.
 */
public enum IpcErrorGroup implements Tag {

  /** No error. */
  none,

  /** Catch all category for errors that do not fit a more specific group. */
  general,

  /**
   * Any failure to initialize the connection. Examples include connection timeout,
   * host not found exception, and SSL handshake errors.
   */
  connection_init,

  /**
   * The request timed out while waiting for a response to arrive or be generated.
   */
  read_timeout,

  /**
   * The client cancelled the request before the server generated a response.
   */
  cancelled,

  /**
   * The client is being throttled due to an excessive number of requests.
   */
  client_throttled,

  /**
   * The client is being throttled due to a problem on the server side.
   */
  server_throttled,

  /**
   * There was an error due to problems with the client request, e.g., HTTP status 400.
   */
  client_error,

  /**
   * There was an error due to problems on the server side, e.g., HTTP status 500.
   */
  server_error;

  @Override public String key() {
    return IpcTagKey.errorGroup.key();
  }

  @Override public String value() {
    return name();
  }
}
