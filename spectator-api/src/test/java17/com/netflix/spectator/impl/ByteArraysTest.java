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

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the JDK 9+ {@link ByteArrays} variant that is provided via the multi-release jar
 * ({@code META-INF/versions/17}). This test runs on JDK 17 so it exercises the VarHandle based
 * implementation rather than the shift based variant in the base jar.
 */
public class ByteArraysTest {

  private static long readLongLE(byte[] b, int offset) {
    return ((long) b[offset]     & 0xFFL)
        | (((long) b[offset + 1] & 0xFFL) << 8)
        | (((long) b[offset + 2] & 0xFFL) << 16)
        | (((long) b[offset + 3] & 0xFFL) << 24)
        | (((long) b[offset + 4] & 0xFFL) << 32)
        | (((long) b[offset + 5] & 0xFFL) << 40)
        | (((long) b[offset + 6] & 0xFFL) << 48)
        | (((long) b[offset + 7] & 0xFFL) << 56);
  }

  @Test
  public void getLongIsLittleEndian() {
    // Include a high-bit byte to catch any sign-extension issues and cover unaligned offsets.
    byte[] bytes = {
        (byte) 0xEF, 1, 2, 3, (byte) 0x80, 5, 6, 7, 8, 9, 10, (byte) 0xFF, 12, 13, 14, 15
    };
    for (int offset = 0; offset + 8 <= bytes.length; ++offset) {
      assertEquals(readLongLE(bytes, offset), ByteArrays.getLong(bytes, offset),
          "mismatch at offset " + offset);
    }
  }

  @Test
  public void hashMatchesBytewisePath() {
    // The bulk updateBytes path uses ByteArrays.getLong (the VarHandle variant here) once the
    // length is at least 8. It must produce the same hash as feeding the bytes one at a time,
    // which never touches ByteArrays. Checking a range of lengths covers the stripe boundaries.
    Random random = new Random(42);
    for (int length = 0; length <= 200; ++length) {
      byte[] data = new byte[length];
      random.nextBytes(data);

      long bulk = new Hash64().updateBytes(data).computeAndReset();

      Hash64 h = new Hash64();
      for (byte b : data) {
        h.updateByte(b);
      }
      long bytewise = h.computeAndReset();

      assertEquals(bytewise, bulk, "hash mismatch for length " + length);
    }
  }
}
