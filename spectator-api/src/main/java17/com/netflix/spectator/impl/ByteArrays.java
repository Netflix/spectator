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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

/**
 * Helpers for reading primitive values out of a byte array. This is the JDK 9+ variant provided
 * via the multi-release jar ({@code META-INF/versions/17}); it uses a {@link VarHandle} to read
 * the value in a single access rather than reconstructing it with shifts as the JDK 8 base
 * variant does.
 *
 * <p><b>This class is an internal implementation detail only intended for use within spectator.
 * It is subject to change without notice.</b></p>
 */
public final class ByteArrays {

  // Reads a long from a byte[] at an arbitrary (possibly unaligned) byte offset. Little endian
  // is used to match the byte order of the JDK 8 base variant so hashes are identical.
  private static final VarHandle LONG_HANDLE =
      MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);

  private ByteArrays() {
  }

  /** Read 8 bytes starting at the given offset as a little endian long value. */
  public static long getLong(byte[] bytes, int offset) {
    return (long) LONG_HANDLE.get(bytes, offset);
  }
}
