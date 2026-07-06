/*
 * Copyright 2014-2026 Netflix, Inc.
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

/**
 * Helpers for reading primitive values out of a byte array. This is the base variant used on
 * JDK 8 and reconstructs the value using plain shifts. On JDK 9+ a faster variant backed by a
 * {@link java.lang.invoke.VarHandle} is provided via the multi-release jar
 * ({@code META-INF/versions/17}).
 *
 * <p><b>This class is an internal implementation detail only intended for use within spectator.
 * It is subject to change without notice.</b></p>
 */
public final class ByteArrays {

  private ByteArrays() {
  }

  /** Read 8 bytes starting at the given offset as a little endian long value. */
  public static long getLong(byte[] bytes, int offset) {
    return ((long) bytes[offset]     & 0xFFL)
        | (((long) bytes[offset + 1] & 0xFFL) << 8)
        | (((long) bytes[offset + 2] & 0xFFL) << 16)
        | (((long) bytes[offset + 3] & 0xFFL) << 24)
        | (((long) bytes[offset + 4] & 0xFFL) << 32)
        | (((long) bytes[offset + 5] & 0xFFL) << 40)
        | (((long) bytes[offset + 6] & 0xFFL) << 48)
        | (((long) bytes[offset + 7] & 0xFFL) << 56);
  }
}
