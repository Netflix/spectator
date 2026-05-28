/*
 * Copyright 2014-2025 Netflix, Inc.
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

import java.nio.charset.StandardCharsets;

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

  /**
   * Threshold above which {@link #updateString(CharSequence)} hands a {@code String} off
   * to {@code getBytes(UTF_8) + updateBytes}. Below this length the inline char-loop is
   * faster (no per-call setup amortization) and structurally non-allocating; above it,
   * the intrinsified UTF-8 encoder plus Unsafe long-stride writes pull ahead. 128 chars
   * is the empirical crossover on JDK 25 — see IdHash JMH numbers.
   */
  static final int LONG_STRING_THRESHOLD = 128;

  /**
   * Update the hash with the specified character sequence value. The string is hashed
   * as its UTF-8 byte sequence. The result matches
   * {@code updateBytes(s.getBytes(UTF_8))} bit-for-bit, so output is portable to other
   * languages that hash UTF-8 bytes.
   *
   * <p>For short strings (length ≤ {@value #LONG_STRING_THRESHOLD}) and any non-String
   * {@code CharSequence}, encodes in-place via a char loop with no transient byte
   * array. For longer {@code String} inputs, falls back to {@code getBytes(UTF_8)}
   * followed by the Unsafe long-stride {@code updateBytes} path — that one allocates a
   * transient byte[] (typically JIT-scalar-replaced) but throughput is 2–3× higher on
   * long strings.</p>
   */
  public Hash64 updateString(CharSequence str) {
    final int len = str.length();
    if (len > LONG_STRING_THRESHOLD && str instanceof String) {
      return updateBytes(((String) str).getBytes(StandardCharsets.UTF_8));
    }
    int i = 0;
    while (i < len) {
      char c = str.charAt(i++);
      if (c < 0x80) {
        // 1-byte ASCII (hot path)
        writeByte((byte) c);
      } else if (c < 0x800) {
        // 2-byte encoding
        writeByte((byte) (0xC0 | (c >>> 6)));
        writeByte((byte) (0x80 | (c & 0x3F)));
      } else if (Character.isHighSurrogate(c)) {
        if (i < len && Character.isLowSurrogate(str.charAt(i))) {
          char low = str.charAt(i++);
          int cp = Character.toCodePoint(c, low);
          writeByte((byte) (0xF0 | (cp >>> 18)));
          writeByte((byte) (0x80 | ((cp >>> 12) & 0x3F)));
          writeByte((byte) (0x80 | ((cp >>> 6) & 0x3F)));
          writeByte((byte) (0x80 | (cp & 0x3F)));
        } else {
          // Unpaired high surrogate → '?'
          writeByte((byte) 0x3F);
        }
      } else if (Character.isLowSurrogate(c)) {
        // Unpaired low surrogate → '?'
        writeByte((byte) 0x3F);
      } else {
        // 3-byte encoding
        writeByte((byte) (0xE0 | (c >>> 12)));
        writeByte((byte) (0x80 | ((c >>> 6) & 0x3F)));
        writeByte((byte) (0x80 | (c & 0x3F)));
      }
    }
    return this;
  }

  // Tight check-and-write for a single byte. Used by updateString's hot loop directly
  // (so the JIT keeps the per-char path small) and by updateByte (which previously
  // duplicated this logic via checkSpace + updateByteImpl).
  private void writeByte(byte value) {
    if (bitPos == Long.SIZE) {
      if (++stripePos == stripe.length) {
        processStripe();
      }
      bitPos = 0;
      stripe[stripePos] = 0L;
    }
    stripe[stripePos] |= ((long) value & 0xFFL) << bitPos;
    bitPos += Byte.SIZE;
  }

  /** Update the hash with the specified byte value. */
  public Hash64 updateByte(byte value) {
    writeByte(value);
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

  private static long round(long acc, long lane) {
    acc += lane * PRIME64_2;
    acc = Long.rotateLeft(acc, 31);
    return acc * PRIME64_1;
  }

  private static long mergeAccumulator(long acc, long accN) {
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
    // bitPos is in bits; 32 bits = 4 bytes, 8 bits = 1 byte. Prior versions used
    // 4 and 1 here directly, which made the byte-by-byte loop run 8x too many
    // times on non-multiple-of-8 inputs and produced a deterministic but
    // non-spec-compliant hash. Cross-validated against net.openhft xxHash64.
    if (bitPos >= 32) {
      long lane = buffer & 0xFFFFFFFFL;
      acc ^= (lane * PRIME64_1);
      acc = Long.rotateLeft(acc, 23) * PRIME64_2;
      acc += PRIME64_3;
      buffer >>>= 32;
      bitPos -= 32;
    }

    while (bitPos >= 8) {
      long lane = buffer & 0xFFL;
      acc ^= (lane * PRIME64_5);
      acc = Long.rotateLeft(acc, 11) * PRIME64_1;
      buffer >>>= 8;
      bitPos -= 8;
    }

    return acc;
  }

  private static long avalanche(long acc) {
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

  // ------------------------------------------------------------------------
  // Allocation-free static helpers for hashing a single value.
  //
  // The instance API (new Hash64().updateX(...)...compute()) is the right fit
  // when composing the hash of multiple values, but for the common "hash one
  // long / string / byte[]" case the instance allocation and the per-call
  // ThreadLocal pattern that callers reach for are both undesirable —
  // ThreadLocals are particularly bad on virtual threads. These statics
  // compute the same xxHash64 output as the instance API with no allocation
  // (except hashString on inputs longer than LONG_STRING_THRESHOLD, which
  // routes through getBytes(UTF_8) + hashBytes for throughput).
  // ------------------------------------------------------------------------

  /** xxHash64 of a single {@code long} value with seed 0. Allocation-free. */
  public static long hashLong(long value) {
    return hashLong(value, 0L);
  }

  /** xxHash64 of a single {@code long} value with the given seed. Allocation-free. */
  public static long hashLong(long value, long seed) {
    long acc = seed + PRIME64_5 + 8L;
    acc ^= round(0L, value);
    acc = Long.rotateLeft(acc, 27) * PRIME64_1 + PRIME64_4;
    return avalanche(acc);
  }

  /** xxHash64 of a single {@code int} value with seed 0. Allocation-free. */
  public static long hashInt(int value) {
    return hashInt(value, 0L);
  }

  /** xxHash64 of a single {@code int} value with the given seed. Allocation-free. */
  public static long hashInt(int value, long seed) {
    long acc = seed + PRIME64_5 + 4L;
    acc ^= (((long) value) & 0xFFFFFFFFL) * PRIME64_1;
    acc = Long.rotateLeft(acc, 23) * PRIME64_2 + PRIME64_3;
    return avalanche(acc);
  }

  /** xxHash64 of {@code bytes} with seed 0. Allocation-free. */
  public static long hashBytes(byte[] bytes) {
    return hashBytes(bytes, 0, bytes.length, 0L);
  }

  /** xxHash64 of {@code bytes} with the given seed. Allocation-free. */
  public static long hashBytes(byte[] bytes, long seed) {
    return hashBytes(bytes, 0, bytes.length, seed);
  }

  /** xxHash64 of {@code bytes[offset, offset+length)} with seed 0. Allocation-free. */
  public static long hashBytes(byte[] bytes, int offset, int length) {
    return hashBytes(bytes, offset, length, 0L);
  }

  /**
   * xxHash64 of {@code bytes[offset, offset+length)} with the given seed.
   * Allocation-free.
   */
  public static long hashBytes(byte[] bytes, int offset, int length, long seed) {
    long acc;
    int i = offset;
    final int end = offset + length;

    if (length >= 32) {
      long acc1 = seed + PRIME64_1 + PRIME64_2;
      long acc2 = seed + PRIME64_2;
      long acc3 = seed;
      long acc4 = seed - PRIME64_1;
      final int stripeEnd = offset + (length & ~31);
      while (i < stripeEnd) {
        acc1 = round(acc1, readLongLE(bytes, i));
        acc2 = round(acc2, readLongLE(bytes, i + 8));
        acc3 = round(acc3, readLongLE(bytes, i + 16));
        acc4 = round(acc4, readLongLE(bytes, i + 24));
        i += 32;
      }
      acc = Long.rotateLeft(acc1, 1) + Long.rotateLeft(acc2, 7)
          + Long.rotateLeft(acc3, 12) + Long.rotateLeft(acc4, 18);
      acc = mergeAccumulator(acc, acc1);
      acc = mergeAccumulator(acc, acc2);
      acc = mergeAccumulator(acc, acc3);
      acc = mergeAccumulator(acc, acc4);
    } else {
      acc = seed + PRIME64_5;
    }

    acc += length;
    return avalanche(consumeBytesTail(acc, bytes, i, end));
  }

  // Consume up to 31 trailing bytes: as many 8-byte chunks as fit, then one
  // 4-byte chunk if possible, then byte-by-byte.
  private static long consumeBytesTail(long acc, byte[] bytes, int i, int end) {
    while (end - i >= 8) {
      acc ^= round(0L, readLongLE(bytes, i));
      acc = Long.rotateLeft(acc, 27) * PRIME64_1 + PRIME64_4;
      i += 8;
    }
    if (end - i >= 4) {
      acc ^= readIntLE(bytes, i) * PRIME64_1;
      acc = Long.rotateLeft(acc, 23) * PRIME64_2 + PRIME64_3;
      i += 4;
    }
    while (i < end) {
      acc ^= ((long) bytes[i] & 0xFFL) * PRIME64_5;
      acc = Long.rotateLeft(acc, 11) * PRIME64_1;
      ++i;
    }
    return acc;
  }

  private static long readLongLE(byte[] bytes, int offset) {
    if (UnsafeUtils.supported()) {
      return UnsafeUtils.getLong(bytes, offset);
    }
    return ((long) bytes[offset]     & 0xFFL)
        | (((long) bytes[offset + 1] & 0xFFL) << 8)
        | (((long) bytes[offset + 2] & 0xFFL) << 16)
        | (((long) bytes[offset + 3] & 0xFFL) << 24)
        | (((long) bytes[offset + 4] & 0xFFL) << 32)
        | (((long) bytes[offset + 5] & 0xFFL) << 40)
        | (((long) bytes[offset + 6] & 0xFFL) << 48)
        | (((long) bytes[offset + 7] & 0xFFL) << 56);
  }

  private static long readIntLE(byte[] bytes, int offset) {
    return ((long) bytes[offset]     & 0xFFL)
        | (((long) bytes[offset + 1] & 0xFFL) << 8)
        | (((long) bytes[offset + 2] & 0xFFL) << 16)
        | (((long) bytes[offset + 3] & 0xFFL) << 24);
  }

  /**
   * xxHash64 of the UTF-8 byte sequence of {@code str} with seed 0.
   *
   * <p>Allocation behavior: a {@code String} longer than {@value #LONG_STRING_THRESHOLD}
   * chars routes through {@code getBytes(UTF_8) + hashBytes} for throughput (allocates
   * a transient byte[], matches {@link #updateString(CharSequence)}'s hybrid). All other
   * inputs — short {@code String}s and any non-{@code String} {@link CharSequence}
   * regardless of length — take the inline UTF-8 encoder and allocate nothing. Long
   * non-{@code String} {@code CharSequence}s pay the inline path's throughput cost
   * since {@code getBytes} is not available on them.</p>
   */
  public static long hashString(CharSequence str) {
    return hashString(str, 0L);
  }

  /**
   * xxHash64 of the UTF-8 byte sequence of {@code str} with the given seed. See
   * {@link #hashString(CharSequence)} for allocation behavior.
   */
  public static long hashString(CharSequence str, long seed) {
    if (str.length() > LONG_STRING_THRESHOLD && str instanceof String) {
      return hashBytes(((String) str).getBytes(StandardCharsets.UTF_8), seed);
    }
    return hashStringInline(str, seed);
  }

  // Inline xxHash64 of the UTF-8 byte sequence of str with no byte[] allocation.
  // Buffers up to a 32-byte stripe in local state; full stripes are folded into
  // the four accumulators; partial trailing input is consumed at finalize.
  private static long hashStringInline(CharSequence str, long seed) {
    long acc1 = seed + PRIME64_1 + PRIME64_2;
    long acc2 = seed + PRIME64_2;
    long acc3 = seed;
    long acc4 = seed - PRIME64_1;
    // Stripe buffer kept as four named locals (not a long[4]) so the method is
    // structurally non-allocating regardless of JIT escape analysis.
    long s0 = 0L, s1 = 0L, s2 = 0L;
    long currentLane = 0L;
    int bitPos = 0;
    int laneIdx = 0;
    long inputLength = 0L;

    final int charLen = str.length();
    for (int ci = 0; ci < charLen; ++ci) {
      char c = str.charAt(ci);

      // Encode one char into 1-4 UTF-8 bytes packed into bytesPacked (low-to-high).
      int numBytes;
      long bytesPacked;
      if (c < 0x80) {
        bytesPacked = c;
        numBytes = 1;
      } else if (c < 0x800) {
        bytesPacked = (0xC0L | (c >>> 6))
            | ((0x80L | (c & 0x3F)) << 8);
        numBytes = 2;
      } else if (Character.isHighSurrogate(c)) {
        if (ci + 1 < charLen && Character.isLowSurrogate(str.charAt(ci + 1))) {
          int cp = Character.toCodePoint(c, str.charAt(++ci));
          bytesPacked = (0xF0L | (cp >>> 18))
              | ((0x80L | ((cp >>> 12) & 0x3F)) << 8)
              | ((0x80L | ((cp >>> 6) & 0x3F)) << 16)
              | ((0x80L | (cp & 0x3F)) << 24);
          numBytes = 4;
        } else {
          bytesPacked = 0x3F; // unpaired high surrogate → '?'
          numBytes = 1;
        }
      } else if (Character.isLowSurrogate(c)) {
        bytesPacked = 0x3F; // unpaired low surrogate → '?'
        numBytes = 1;
      } else {
        bytesPacked = (0xE0L | (c >>> 12))
            | ((0x80L | ((c >>> 6) & 0x3F)) << 8)
            | ((0x80L | (c & 0x3F)) << 16);
        numBytes = 3;
      }

      // Append bytes into the stripe buffer; on lane fill, advance laneIdx;
      // on full 32-byte stripe, apply rounds and reset.
      for (int bi = 0; bi < numBytes; ++bi) {
        currentLane |= (bytesPacked & 0xFFL) << bitPos;
        bytesPacked >>>= 8;
        bitPos += 8;
        if (bitPos == 64) {
          if (laneIdx == 3) {
            // 4th lane just filled — fold the whole stripe into the accumulators.
            acc1 = round(acc1, s0);
            acc2 = round(acc2, s1);
            acc3 = round(acc3, s2);
            acc4 = round(acc4, currentLane);
            inputLength += 32;
            laneIdx = 0;
          } else {
            switch (laneIdx) {
              case 0: s0 = currentLane; break;
              case 1: s1 = currentLane; break;
              default: s2 = currentLane; break;  // laneIdx == 2
            }
            ++laneIdx;
          }
          currentLane = 0L;
          bitPos = 0;
        }
      }
    }

    final long tailBytes = laneIdx * 8L + bitPos / 8L;
    final long totalBytes = inputLength + tailBytes;
    long acc;
    if (totalBytes < 32L) {
      acc = seed + PRIME64_5;
    } else {
      acc = Long.rotateLeft(acc1, 1) + Long.rotateLeft(acc2, 7)
          + Long.rotateLeft(acc3, 12) + Long.rotateLeft(acc4, 18);
      acc = mergeAccumulator(acc, acc1);
      acc = mergeAccumulator(acc, acc2);
      acc = mergeAccumulator(acc, acc3);
      acc = mergeAccumulator(acc, acc4);
    }
    acc += totalBytes;

    // Consume buffered complete lanes (laneIdx of them, in order) as 8-byte tail
    // chunks. Unrolled to keep the lane state in named locals.
    if (laneIdx >= 1) {
      acc ^= round(0L, s0);
      acc = Long.rotateLeft(acc, 27) * PRIME64_1 + PRIME64_4;
    }
    if (laneIdx >= 2) {
      acc ^= round(0L, s1);
      acc = Long.rotateLeft(acc, 27) * PRIME64_1 + PRIME64_4;
    }
    if (laneIdx >= 3) {
      acc ^= round(0L, s2);
      acc = Long.rotateLeft(acc, 27) * PRIME64_1 + PRIME64_4;
    }
    // Then partial-lane bytes from currentLane (bitPos in bits, multiple of 8).
    long buffer = currentLane;
    int remainingBits = bitPos;
    if (remainingBits >= 32) {
      long lane = buffer & 0xFFFFFFFFL;
      acc ^= lane * PRIME64_1;
      acc = Long.rotateLeft(acc, 23) * PRIME64_2 + PRIME64_3;
      buffer >>>= 32;
      remainingBits -= 32;
    }
    while (remainingBits >= 8) {
      acc ^= (buffer & 0xFFL) * PRIME64_5;
      acc = Long.rotateLeft(acc, 11) * PRIME64_1;
      buffer >>>= 8;
      remainingBits -= 8;
    }
    return avalanche(acc);
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
