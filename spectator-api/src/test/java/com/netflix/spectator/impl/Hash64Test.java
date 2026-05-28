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

import net.openhft.hashing.LongHashFunction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class Hash64Test {

  private final Random random = new Random(42L);


  private boolean[] randomBooleanArray(int length) {
    boolean[] vs = new boolean[length];
    for (int i = 0; i < length; ++i) {
      vs[i] = random.nextBoolean();
    }
    return vs;
  }

  private long checkBooleans(boolean[] vs) {
    Hash64 h64 = new Hash64();
    for (boolean v : vs)
      h64.updateBoolean(v);
    long a = h64.computeAndReset();
    long b = h64.updateBooleans(vs).compute();
    Assertions.assertEquals(a, b);
    return a;
  }

  @Test
  public void hashBooleans() {
    Set<Long> hashes = new HashSet<>();
    for (int i = 0; i < 1000; ++i) {
      boolean[] vs = randomBooleanArray(i);
      long h = checkBooleans(vs);
      Assertions.assertFalse(hashes.contains(h));
      hashes.add(h);
    }
  }

  private String randomString(int length) {
    StringBuilder builder = new StringBuilder(length);
    for (int i = 0; i < length; ++i) {
      char c = (char) random.nextInt(Character.MAX_VALUE);
      builder.append(c);
    }
    return builder.toString();
  }

  private long checkBytes(String str) {
    byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
    Hash64 h64 = new Hash64();
    for (byte b : bytes)
      h64.updateByte(b);
    long a = h64.computeAndReset();
    long b = h64.updateBytes(bytes).computeAndReset();
    Assertions.assertEquals(a, b, "input: " + str+ ", " + bytes.length);

    for (int i = 0; i < bytes.length; i += 7) {
      int length = Math.min(7, bytes.length - i);
      h64.updateBytes(bytes, i, length);
    }
    long c = h64.compute();
    Assertions.assertEquals(a, c);

    return a;
  }

  @Test
  public void hashBytes() {
    Map<Long, String> hashes = new HashMap<>();
    for (int i = 0; i < 1000; ++i) {
      String str = randomString(i);
      long h = checkBytes(str);
      Assertions.assertFalse(hashes.containsKey(h), () ->
          "[" + str + "] and [" + hashes.get(h) +  "] have same hash value, h = " + h
      );
      hashes.put(h, str);
    }
  }

  private long checkString(String str) {
    long a = new Hash64().updateString(str).compute();
    long b = new Hash64().updateBytes(str.getBytes(StandardCharsets.UTF_8)).compute();
    long c = new Hash64()
        .updateString(new StringBuilder().append(str))
        .compute();
    Assertions.assertEquals(a, b, str);
    Assertions.assertEquals(a, c, str);
    return a;
  }

  @Test
  public void hashString() {
    Map<Long, String> hashes = new HashMap<>();
    for (int i = 0; i < 1000; ++i) {
      String str = randomString(i);
      long h = checkString(str);
      Assertions.assertFalse(hashes.containsKey(h), () ->
        "[" + str + "] and [" + hashes.get(h) +  "] have same hash value, h = " + h
      );
      hashes.put(h, str);
    }
  }

  private short[] randomShortArray(int length) {
    short[] vs = new short[length];
    for (int i = 0; i < length; ++i) {
      vs[i] = (short) random.nextInt(Short.MAX_VALUE);
    }
    return vs;
  }

  private long checkShorts(short[] vs) {
    Hash64 h64 = new Hash64();
    for (short v : vs)
      h64.updateShort(v);
    long a = h64.computeAndReset();
    long b = h64.updateShorts(vs).compute();
    Assertions.assertEquals(a, b);
    return a;
  }

  @Test
  public void hashShorts() {
    Set<Long> hashes = new HashSet<>();
    for (int i = 0; i < 1000; ++i) {
      short[] vs = randomShortArray(i);
      long h = checkShorts(vs);
      Assertions.assertFalse(hashes.contains(h));
      hashes.add(h);
    }
  }

  private int[] randomIntArray(int length) {
    int[] vs = new int[length];
    for (int i = 0; i < length; ++i) {
      vs[i] = random.nextInt();
    }
    return vs;
  }

  private long checkInts(int[] vs) {
    Hash64 h64 = new Hash64();
    for (int v : vs)
      h64.updateInt(v);
    long a = h64.computeAndReset();
    long b = h64.updateInts(vs).compute();
    Assertions.assertEquals(a, b);
    return a;
  }

  @Test
  public void hashInts() {
    Set<Long> hashes = new HashSet<>();
    for (int i = 0; i < 1000; ++i) {
      int[] vs = randomIntArray(i);
      long h = checkInts(vs);
      Assertions.assertFalse(hashes.contains(h));
      hashes.add(h);
    }
  }

  @Test
  public void hashLong() {
    // Sanity check implementation with openhft version
    final LongHashFunction xx64 = LongHashFunction.xx();
    final Hash64 h64 = new Hash64();
    for (int i = 0; i < 1; ++i) {
      long v = random.nextLong();
      long h = h64.updateLong(v).computeAndReset();
      Assertions.assertEquals(xx64.hashLong(v), h);
    }
  }

  private long[] randomLongArray(int length) {
    long[] vs = new long[length];
    for (int i = 0; i < length; ++i) {
      vs[i] = random.nextLong();
    }
    return vs;
  }

  @Test
  public void hashLongs() {
    // Sanity check implementation with openhft version
    final LongHashFunction xx64 = LongHashFunction.xx();
    final Hash64 h64 = new Hash64();
    for (int i = 0; i < 1000; ++i) {
      long[] vs = randomLongArray(i);
      long h = h64.updateLongs(vs).computeAndReset();
      Assertions.assertEquals(xx64.hashLongs(vs), h);
    }
  }

  private float[] randomFloatArray(int length) {
    float[] vs = new float[length];
    for (int i = 0; i < length; ++i) {
      vs[i] = random.nextFloat();
    }
    return vs;
  }

  private long checkFloats(float[] vs) {
    Hash64 h64 = new Hash64();
    for (float v : vs)
      h64.updateFloat(v);
    long a = h64.computeAndReset();
    long b = h64.updateFloats(vs).compute();
    Assertions.assertEquals(a, b);
    return a;
  }

  @Test
  public void hashFloats() {
    Set<Long> hashes = new HashSet<>();
    for (int i = 0; i < 1000; ++i) {
      float[] vs = randomFloatArray(i);
      long h = checkFloats(vs);
      Assertions.assertFalse(hashes.contains(h));
      hashes.add(h);
    }
  }

  private double[] randomDoubleArray(int length) {
    double[] vs = new double[length];
    for (int i = 0; i < length; ++i) {
      vs[i] = random.nextDouble();
    }
    return vs;
  }

  private long checkDoubles(double[] vs) {
    Hash64 h64 = new Hash64();
    for (double v : vs)
      h64.updateDouble(v);
    long a = h64.computeAndReset();
    long b = h64.updateDoubles(vs).compute();
    Assertions.assertEquals(a, b);
    return a;
  }

  @Test
  public void hashDoubles() {
    Set<Long> hashes = new HashSet<>();
    for (int i = 0; i < 1000; ++i) {
      double[] vs = randomDoubleArray(i);
      long h = checkDoubles(vs);
      Assertions.assertFalse(hashes.contains(h));
      hashes.add(h);
    }
  }

  @Test
  public void reduce() {
    int n = 10;
    int[] counts = new int[n];
    Random r = new Random();
    for (int i = 0; i < 100_000; ++i) {
      long hash = r.nextLong();
      ++counts[Hash64.reduce(hash, n)];
    }
    for (int count : counts) {
      int delta = Math.abs(10_000 - count);
      Assertions.assertTrue(delta < 1000);
    }
  }

  @Test
  public void hashBytesMatchesXxHashSpec() {
    // Cross-validate against the openhft xxHash64 reference across input lengths
    // 0..256. Prior versions of consumeRemainingInput produced non-spec output
    // for any length that wasn't a multiple of 8 bytes; this guards against
    // regressions to that bug and verifies cross-language portability of the
    // hash values.
    final LongHashFunction xx64 = LongHashFunction.xx();
    final byte[] data = new byte[256];
    for (int i = 0; i < data.length; ++i) {
      data[i] = (byte) i;
    }
    for (int len = 0; len <= data.length; ++len) {
      long expected = xx64.hashBytes(data, 0, len);
      long actual = new Hash64().updateBytes(data, 0, len).compute();
      Assertions.assertEquals(expected, actual, "length=" + len);
    }
  }

  @Test
  public void hashStringMatchesXxHashSpec() {
    // updateString hashes the UTF-8 byte sequence; openhft.hashBytes on the
    // same UTF-8 bytes should match exactly for any length and any character
    // (1-, 2-, 3-, or 4-byte UTF-8 sequences).
    final LongHashFunction xx64 = LongHashFunction.xx();
    final String[] samples = {
        "", "a", "ab", "abc", "abcd", "abcde", "abcdef", "abcdefg",
        "abcdefgh", "abcdefghi", "abcdefghijklmno", "abcdefghijklmnop",
        "héllo", "世界", "user-42-héllo-世界",
        repeat("x", 127), repeat("x", 128), repeat("x", 129),
        repeat("x", 1024)
    };
    for (String s : samples) {
      byte[] utf8 = s.getBytes(StandardCharsets.UTF_8);
      long expected = xx64.hashBytes(utf8);
      long actual = new Hash64().updateString(s).compute();
      Assertions.assertEquals(expected, actual, "input=\"" + s + "\" (utf8 length=" + utf8.length + ")");
    }
  }

  private static String repeat(String s, int n) {
    StringBuilder sb = new StringBuilder(s.length() * n);
    for (int i = 0; i < n; ++i) sb.append(s);
    return sb.toString();
  }

  // ------------------------------------------------------------------------
  // Static-helper tests: assert the allocation-free static methods produce
  // identical output to (a) the instance builder API and (b) openhft xxHash64.
  // ------------------------------------------------------------------------

  @Test
  public void staticHashLong() {
    LongHashFunction xx = LongHashFunction.xx();
    long[] values = {0L, 1L, -1L, Long.MIN_VALUE, Long.MAX_VALUE, 42L, 0xDEADBEEFCAFEBABEL};
    for (long v : values) {
      long fromBuilder = new Hash64().updateLong(v).compute();
      long fromStatic = Hash64.hashLong(v);
      long fromXx = xx.hashLong(v);
      Assertions.assertEquals(fromBuilder, fromStatic, "builder vs static, value=" + v);
      Assertions.assertEquals(fromXx, fromStatic, "openhft vs static, value=" + v);
    }
  }

  @Test
  public void staticHashLongWithSeed() {
    long[] values = {0L, 1L, -1L, 42L};
    long[] seeds = {0L, 1L, -1L, 0xCAFEBABECAFEBABEL};
    for (long seed : seeds) {
      LongHashFunction xx = LongHashFunction.xx(seed);
      for (long v : values) {
        long fromStatic = Hash64.hashLong(v, seed);
        long fromXx = xx.hashLong(v);
        Assertions.assertEquals(fromXx, fromStatic,
            "openhft vs static, value=" + v + ", seed=" + seed);
      }
    }
  }

  @Test
  public void staticHashInt() {
    LongHashFunction xx = LongHashFunction.xx();
    int[] values = {0, 1, -1, Integer.MIN_VALUE, Integer.MAX_VALUE, 42};
    for (int v : values) {
      long fromBuilder = new Hash64().updateInt(v).compute();
      long fromStatic = Hash64.hashInt(v);
      long fromXx = xx.hashInt(v);
      Assertions.assertEquals(fromBuilder, fromStatic, "builder vs static, value=" + v);
      Assertions.assertEquals(fromXx, fromStatic, "openhft vs static, value=" + v);
    }
  }

  @Test
  public void staticHashBytes() {
    LongHashFunction xx = LongHashFunction.xx();
    byte[] data = new byte[256];
    for (int i = 0; i < data.length; ++i) {
      data[i] = (byte) i;
    }
    for (int len = 0; len <= data.length; ++len) {
      long fromBuilder = new Hash64().updateBytes(data, 0, len).compute();
      long fromStatic = Hash64.hashBytes(data, 0, len);
      long fromXx = xx.hashBytes(data, 0, len);
      Assertions.assertEquals(fromBuilder, fromStatic, "builder vs static, length=" + len);
      Assertions.assertEquals(fromXx, fromStatic, "openhft vs static, length=" + len);
    }
  }

  @Test
  public void staticHashBytesWithSeed() {
    long seed = 0xCAFEBABEL;
    LongHashFunction xx = LongHashFunction.xx(seed);
    byte[] data = new byte[128];
    for (int i = 0; i < data.length; ++i) {
      data[i] = (byte) (i * 7);
    }
    for (int len = 0; len <= data.length; ++len) {
      long fromStatic = Hash64.hashBytes(data, 0, len, seed);
      long fromXx = xx.hashBytes(data, 0, len);
      Assertions.assertEquals(fromXx, fromStatic, "length=" + len);
    }
  }

  @Test
  public void staticHashBytesOffset() {
    // Same payload viewed via different offsets must produce the same hash.
    byte[] padded = new byte[300];
    byte[] payload = "hello world this is a test string".getBytes(StandardCharsets.UTF_8);
    System.arraycopy(payload, 0, padded, 50, payload.length);

    long fromPayload = Hash64.hashBytes(payload);
    long fromOffset = Hash64.hashBytes(padded, 50, payload.length);
    Assertions.assertEquals(fromPayload, fromOffset);
  }

  @Test
  public void staticHashString() {
    LongHashFunction xx = LongHashFunction.xx();
    String[] samples = {
        "", "a", "ab", "abc", "abcdefgh", "abcdefghi",
        "héllo", "世界", "user-42-héllo-世界",
        repeat("x", 31), repeat("x", 32), repeat("x", 33),
        repeat("x", 127), repeat("x", 128), repeat("x", 129),
        repeat("abc", 100)
    };
    for (String s : samples) {
      byte[] utf8 = s.getBytes(StandardCharsets.UTF_8);
      long fromBuilder = new Hash64().updateString(s).compute();
      long fromStatic = Hash64.hashString(s);
      long fromXx = xx.hashBytes(utf8);
      Assertions.assertEquals(fromBuilder, fromStatic,
          "builder vs static, input=\"" + s + "\" (utf8 len=" + utf8.length + ")");
      Assertions.assertEquals(fromXx, fromStatic,
          "openhft vs static, input=\"" + s + "\" (utf8 len=" + utf8.length + ")");
    }
  }

  @Test
  public void staticHashStringCharSequence() {
    // Non-String CharSequence takes the inline path even for long inputs;
    // verify the same byte sequence still produces the same hash.
    String base = repeat("abc", 100);
    StringBuilder sb = new StringBuilder(base);
    long fromString = Hash64.hashString(base);
    long fromBuilder = Hash64.hashString(sb);
    Assertions.assertEquals(fromString, fromBuilder);
  }

  @Test
  public void staticHashStringCharSequenceMultibyte() {
    // Long non-String CharSequence (which stays on the inline encoder regardless
    // of length) with 2-, 3-, and 4-byte UTF-8 code points, sized to cross 8-byte
    // lane and 32-byte stripe boundaries in the inline encoder.
    LongHashFunction xx = LongHashFunction.xx();
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 50; ++i) {
      sb.append("a")        // 1-byte
        .append("é")       // 2-byte (Latin-1)
        .append("世")       // 3-byte (CJK)
        .append("😀");  // 4-byte (surrogate pair, U+1F600 grinning face)
    }
    byte[] utf8 = sb.toString().getBytes(StandardCharsets.UTF_8);
    long fromStatic = Hash64.hashString(sb);
    long fromXx = xx.hashBytes(utf8);
    Assertions.assertEquals(fromXx, fromStatic, "utf8 length=" + utf8.length);
  }

  @Test
  public void staticHashStringWithSeed() {
    long seed = 0xC0FFEEL;
    LongHashFunction xx = LongHashFunction.xx(seed);
    String[] samples = {"", "a", "abcdefgh", "héllo", repeat("x", 200)};
    for (String s : samples) {
      long fromStatic = Hash64.hashString(s, seed);
      long fromXx = xx.hashBytes(s.getBytes(StandardCharsets.UTF_8));
      Assertions.assertEquals(fromXx, fromStatic, "input=\"" + s + "\"");
    }
  }
}
