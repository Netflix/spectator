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

import sun.misc.Unsafe; // NOPMD

import java.lang.reflect.Field;

/**
 * Utility class for using {@code sun.misc.Unsafe} to access some internal details to get
 * better performance.
 *
 * <p><b>This class is an internal implementation detail only intended for use within spectator.
 * It is subject to change without notice.</b></p>
 */
@SuppressWarnings("PMD")
public final class UnsafeUtils {

  private static final Unsafe UNSAFE;

  private static final long STRING_VALUE_OFFSET;
  private static final boolean STRING_VALUE_BYTES;

  static {
    Unsafe instance;
    try {
      final Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
      theUnsafe.setAccessible(true);
      instance = (Unsafe) theUnsafe.get(null);
    } catch (Exception e) {
      instance = null;
    }
    UNSAFE = instance;

    if (UNSAFE != null) {
      long stringValueOffset;
      boolean stringValueBytes;
      try {
        final Field value = String.class.getDeclaredField("value");
        stringValueOffset = UNSAFE.objectFieldOffset(value);
        Object obj = UNSAFE.getObject("value", stringValueOffset);
        stringValueBytes = obj instanceof byte[];
      } catch (Exception e) {
        stringValueOffset = -1L;
        stringValueBytes = false;
      }
      STRING_VALUE_OFFSET = stringValueOffset;
      STRING_VALUE_BYTES = stringValueBytes;
    } else {
      STRING_VALUE_OFFSET =  -1L;
      STRING_VALUE_BYTES = false;
    }
  }

  private UnsafeUtils() {
  }

  /**
   * Returns true if unsafe operations are supported. Code should have a fallback that
   * works when it is not available.
   */
  public static boolean supported() {
    return UNSAFE != null;
  }

  /** Returns true if extracting the underlying value array for a String is supported. */
  public static boolean stringValueSupported() {
    return STRING_VALUE_OFFSET > 0L;
  }

  /** Returns true if it is a newer JDK that uses a byte array for the characters. */
  public static boolean stringValueBytes() {
    return STRING_VALUE_BYTES;
  }

  /** Get the value array for a String as an array of bytes. */
  public static byte[] getStringValueBytes(String str) {
    return (byte[]) UNSAFE.getObject(str, STRING_VALUE_OFFSET);
  }

  /** Get the value array for a String as an array of characters. */
  public static char[] getStringValueChars(String str) {
    return (char[]) UNSAFE.getObject(str, STRING_VALUE_OFFSET);
  }

  /** Treat a seq of 8 bytes at the given offset as a long value. */
  public static long getLong(byte[] bytes, int offset) {
    long idx = Unsafe.ARRAY_BYTE_BASE_OFFSET + offset;
    return UNSAFE.getLong(bytes, idx);
  }
}
