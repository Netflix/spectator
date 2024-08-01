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

/**
 * Helper to compute a 64-bit hash incrementally based on a set of primitives and primitive
 * arrays. It is currently using the XXH64 algorithm. See the
 * <a href="https://github.com/Cyan4973/xxHash/blob/dev/doc/xxhash_spec.md">spec</a> for more
 * details.
 *
 * <p><b>This class is an internal implementation detail only intended for use within spectator.
 * It is subject to change without notice.</b></p>
 */
@SuppressWarnings("PMD")
public final class Hash64 {

  private static final long PRIME64_1 = 0x9E3779B185EBCA87L;
  private static final long PRIME64_2 = 0xC2B2AE3D27D4EB4FL;
  private static final long PRIME64_3 = 0x165667B19E3779F9L;
  private static final long PRIME64_4 = 0x85EBCA77C2B2AE63L;
  private static final long PRIME64_5 = 0x27D4EB2F165667C5L;

  private final long[] stripe;
  private int stripePos;
  private int bitPos;

  private final long seed;
  private long acc1;
  private long acc2;
  private long acc3;
  private long acc4;
  private long inputLength;

  /** Create a new instance with a seed of zero. */
  public Hash64() {
    this(0L);
  }

  /** Create a new instance. */
  public Hash64(long seed) {
    this.seed = seed;
    this.stripe = new long[4];
    reset();
  }

  /** Reset so it can be reused for computing another hash. */
  public void reset() {
    stripePos = 0;
    bitPos = 0;
    stripe[0] = 0L;
    acc1 = seed + PRIME64_1 + PRIME64_2;
    acc2 = seed + PRIME64_2;
    acc3 = seed;
    acc4 = seed - PRIME64_1;
    inputLength = 0L;
  }

  private void checkSpace(int size) {
    // Ensure there is enough space to write the primitive in the current
    // long value, otherwise move to the next
    if (size > Long.SIZE - bitPos) {
      if (++stripePos == stripe.length) {
        processStripe();
      }
      bitPos = 0;
      stripe[stripePos] = 0L;
    }
  }

  /** Update the hash with the specified boolean value. */
  public Hash64 updateBoolean(boolean value) {
    return updateByte((byte) (value ? 1 : 0));
  }

  /** Update the hash with the specified boolean values. */
  public Hash64 updateBooleans(boolean[] values, int offset, int length) {
    for (int i = offset; i < offset + length; ++i) {
      updateBoolean(values[i]);
    }
    return this;
  }

  /** Update the hash with the specified boolean values. */
  public Hash64 updateBooleans(boolean[] values) {
    return updateBooleans(values, 0, values.length);
  }

  /** Update the hash with the specified character value. */
  public Hash64 updateChar(char value) {
    checkSpace(Character.SIZE);
    final long c = ((long) value) << bitPos;
    stripe[stripePos] |= c;
    bitPos += Character.SIZE;
    return this;
  }

  /** Update the hash with the specified character values. */
  public Hash64 updateChars(char[] values, int offset, int length) {
    for (int i = offset; i < offset + length; ++i) {
      updateChar(values[i]);
    }
    return this;
  }

  /** Update the hash with the specified character values. */
  public Hash64 updateChars(char[] values) {
    return updateChars(values, 0, values.length);
  }

  /** Update the hash with the specified character sequence value. */
  public Hash64 updateString(CharSequence str) {
    if (str instanceof String && UnsafeUtils.stringValueSupported()) {
      if (UnsafeUtils.stringValueBytes()) {
        byte[] vs = UnsafeUtils.getStringValueBytes((String) str);
        updateBytes(vs, 0, vs.length);
      } else {
        char[] vs = UnsafeUtils.getStringValueChars((String) str);
        updateChars(vs, 0, vs.length);
      }
    } else {
      for (int i = 0; i < str.length(); ++i) {
        updateChar(str.charAt(i));
      }
    }
    return this;
  }

  /** Update the hash with the specified byte value. */
  public Hash64 updateByte(byte value) {
    checkSpace(Byte.SIZE);
    updateByteImpl(value);
    return this;
  }

  private void updateByteImpl(byte value) {
    final long v = ((long) value & 0xFFL) << bitPos;
    stripe[stripePos] |= v;
    bitPos += Byte.SIZE;
  }

  /** Update the hash with the specified byte values. */
  public Hash64 updateBytes(byte[] values, int offset, int length) {
    if (UnsafeUtils.supported() && length >= 8) {
      final int bytesInStripe = (Long.SIZE - bitPos) / Byte.SIZE;
      final int s = offset + bytesInStripe;
      final int e = s + (length - bytesInStripe) / Long.BYTES * Long.BYTES;

      // Complete current stripe
      for (int i = offset; i < s; ++i) {
        updateByteImpl(values[i]);
      }
      if (++stripePos == stripe.length) {
        processStripe();
      }
      bitPos = 0;

      // Write long values
      for (int i = s; i < e; i += Long.BYTES) {
        stripe[stripePos] = UnsafeUtils.getLong(values, i);
        if (++stripePos == stripe.length) {
          processStripe();
        }
      }

      // Write remaining bytes
      stripe[stripePos] = 0L;
      for (int i = e; i < offset + length; ++i) {
        updateByteImpl(values[i]);
      }
    } else {
      for (int i = offset; i < offset + length; ++i) {
        updateByte(values[i]);
      }
    }
    return this;
  }

  /** Update the hash with the specified byte values. */
  public Hash64 updateBytes(byte[] values) {
    return updateBytes(values, 0, values.length);
  }

  /** Update the hash with the specified short value. */
  public Hash64 updateShort(short value) {
    checkSpace(Short.SIZE);
    final long v = ((long) value & 0xFFFFL) << bitPos;
    stripe[stripePos] |= v;
    bitPos += Short.SIZE;
    return this;
  }

  /** Update the hash with the specified short values. */
  public Hash64 updateShorts(short[] values, int offset, int length) {
    for (int i = offset; i < offset + length; ++i) {
      updateShort(values[i]);
    }
    return this;
  }

  /** Update the hash with the specified short values. */
  public Hash64 updateShorts(short[] values) {
    return updateShorts(values, 0, values.length);
  }

  /** Update the hash with the specified int value. */
  public Hash64 updateInt(int value) {
    checkSpace(Integer.SIZE);
    final long v = ((long) value & 0xFFFFFFFFL) << bitPos;
    stripe[stripePos] |= v;
    bitPos += Integer.SIZE;
    return this;
  }

  /** Update the hash with the specified int values. */
  public Hash64 updateInts(int[] values, int offset, int length) {
    for (int i = offset; i < offset + length; ++i) {
      updateInt(values[i]);
    }
    return this;
  }

  /** Update the hash with the specified int values. */
  public Hash64 updateInts(int[] values) {
    return updateInts(values, 0, values.length);
  }

  /** Update the hash with the specified long value. */
  public Hash64 updateLong(long value) {
    checkSpace(Long.SIZE);
    stripe[stripePos] = value;
    bitPos += Long.SIZE;
    return this;
  }

  /** Update the hash with the specified long values. */
  public Hash64 updateLongs(long[] values, int offset, int length) {
    checkSpace(Long.SIZE);

    // Fill current stripe
    final int prefixLength = Math.min(length, stripe.length - stripePos);
    System.arraycopy(values, offset, stripe, stripePos, prefixLength);
    stripePos += prefixLength;
    if (stripePos == stripe.length) {
      processStripe();
    }

    // Copy the remainder, one stripe at a time
    final int start = offset + prefixLength;
    final int end = offset + length;
    for (int i = start; i < end; i += stripe.length) {
      final int copyLength = Math.min(stripe.length, end - i);
      System.arraycopy(values, i, stripe, 0, copyLength);
      stripePos = copyLength;
      if (stripePos == stripe.length) {
        processStripe();
      } else {
        stripe[stripePos] = 0L;
      }
    }
    return this;
  }

  /** Update the hash with the specified long values. */
  public Hash64 updateLongs(long[] values) {
    return updateLongs(values, 0, values.length);
  }

  /** Update the hash with the specified float value. */
  public Hash64 updateFloat(float value) {
    return updateInt(Float.floatToIntBits(value));
  }

  /** Update the hash with the specified float values. */
  public Hash64 updateFloats(float[] values, int offset, int length) {
    for (int i = offset; i < offset + length; ++i) {
      updateFloat(values[i]);
    }
    return this;
  }

  /** Update the hash with the specified float values. */
  public Hash64 updateFloats(float[] values) {
    return updateFloats(values, 0, values.length);
  }

  /** Update the hash with the specified double value. */
  public Hash64 updateDouble(double value) {
    return updateLong(Double.doubleToLongBits(value));
  }

  /** Update the hash with the specified double values. */
  public Hash64 updateDoubles(double[] values, int offset, int length) {
    for (int i = offset; i < offset + length; ++i) {
      updateDouble(values[i]);
    }
    return this;
  }

  /** Update the hash with the specified double values. */
  public Hash64 updateDoubles(double[] values) {
    return updateDoubles(values, 0, values.length);
  }

  private void processStripe() {
    inputLength += 32;
    acc1 = round(acc1, stripe[0]);
    acc2 = round(acc2, stripe[1]);
    acc3 = round(acc3, stripe[2]);
    acc4 = round(acc4, stripe[3]);
    stripePos = 0;
    bitPos = 0;
    stripe[0] = 0L;
  }

  private long round(long acc, long lane) {
    acc += lane * PRIME64_2;
    acc = Long.rotateLeft(acc, 31);
    return acc * PRIME64_1;
  }

  private long mergeAccumulator(long acc, long accN) {
    acc ^= round(0L, accN);
    acc *= PRIME64_1;
    return acc + PRIME64_4;
  }

  private long consumeRemainingInput(long acc) {

    for (int i = 0; i < stripePos; ++i) {
      long lane = stripe[i];
      acc ^= round(0, lane);
      acc = Long.rotateLeft(acc, 27) * PRIME64_1;
      acc += PRIME64_4;
    }

    long buffer = stripe[stripePos];
    if (bitPos >= 4) {
      long lane = buffer & 0xFFFFFFFFL;
      acc ^= (lane * PRIME64_1);
      acc = Long.rotateLeft(acc, 23) * PRIME64_2;
      acc += PRIME64_3;
      buffer >>>= 32;
      bitPos -= 4;
    }

    while (bitPos >= 1) {
      long lane = buffer & 0xFFL;
      acc ^= (lane * PRIME64_5);
      acc = Long.rotateLeft(acc, 11) * PRIME64_1;
      buffer >>>= 8;
      bitPos -= 1;
    }

    return acc;
  }

  private long avalanche(long acc) {
    acc ^= acc >>> 33;
    acc *= PRIME64_2;
    acc ^= acc >>> 29;
    acc *= PRIME64_3;
    acc ^= acc >>> 32;
    return acc;
  }

  /** Compute and return the final hash value. */
  public long compute() {
    // Write final stripe if it is full
    checkSpace(Byte.SIZE);

    long acc;
    if (inputLength < 32L) {
      // Special case: input is less than 32 bytes
      acc = seed + PRIME64_5;
    } else {
      // Step 3. Accumulator convergence
      acc = Long.rotateLeft(acc1, 1)
          + Long.rotateLeft(acc2, 7)
          + Long.rotateLeft(acc3, 12)
          + Long.rotateLeft(acc4, 18);
      acc = mergeAccumulator(acc, acc1);
      acc = mergeAccumulator(acc, acc2);
      acc = mergeAccumulator(acc, acc3);
      acc = mergeAccumulator(acc, acc4);
    }

    // Step 4. Add input length
    inputLength += stripePos * 8L + bitPos / 8L;
    acc += inputLength;

    // Step 5. Consume remaining input
    acc = consumeRemainingInput(acc);

    // Step 6. Final mix (avalanche)
    return avalanche(acc);
  }

  /** Compute the final hash value and reset the hash accumulator. */
  public long computeAndReset() {
    long h = compute();
    reset();
    return h;
  }

  /**
   * Helper to efficiently reduce hash from to range of {@code [0, n)}. Based on
   * <a href="https://lemire.me/blog/2016/06/27/a-fast-alternative-to-the-modulo-reduction/">fast
   * alternative to modulo reduction</a> post.
   */
  public static int reduce(long hash, int n) {
    return reduce((int) hash, n);
  }

  /**
   * Helper to efficiently reduce hash from to range of {@code [0, n)}. Based on
   * <a href="https://lemire.me/blog/2016/06/27/a-fast-alternative-to-the-modulo-reduction/">fast
   * alternative to modulo reduction</a> post.
   */
  public static int reduce(int hash, int n) {
    return (int) (((hash & 0xFFFFFFFFL) * (n & 0xFFFFFFFFL)) >>> 32);
  }
}
