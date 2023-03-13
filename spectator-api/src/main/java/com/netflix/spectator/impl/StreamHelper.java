/*
 * Copyright 2014-2023 Netflix, Inc.
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
package com.netflix.spectator.impl;

import java.io.ByteArrayOutputStream;

/**
 * Caches ByteArrayOutputStream objects in a thread local to allow for reuse. This can be
 * useful to reduce the allocations for growing the buffer. It will hold onto the streams
 * so it should only be used for use-cases where the data written to the stream is bounded.
 *
 * <p><b>This class is an internal implementation detail only intended for use within spectator.
 * It is subject to change without notice.</b></p>
 */
public final class StreamHelper {

  private final ThreadLocal<ByteArrayOutputStream> streams;

  public StreamHelper() {
    streams = new ThreadLocal<>();
  }

  public ByteArrayOutputStream getOrCreateStream() {
    ByteArrayOutputStream baos = streams.get();
    if (baos == null) {
      baos = new ByteArrayOutputStream();
      streams.set(baos);
    } else {
      baos.reset();
    }
    return baos;
  }
}
