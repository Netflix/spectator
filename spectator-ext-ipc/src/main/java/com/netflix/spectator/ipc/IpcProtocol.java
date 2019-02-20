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
 * Dimension indicating the protocol. This enum includes the standard values for well
 * established protocols that should agree across all implementations.
 */
public enum IpcProtocol implements Tag {
  /** HTTP 1.x. */
  http_1,

  /** HTTP 2.x. */
  http_2,

  /** gRPC protocol. */
  grpc;

  @Override public String key() {
    return IpcTagKey.protocol.key();
  }

  @Override public String value() {
    return name();
  }
}
