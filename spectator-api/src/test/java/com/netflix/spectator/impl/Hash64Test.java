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
    long b = new Hash64().updateChars(str.toCharArray()).compute();
    long c = new Hash64()
        .updateString(new StringBuilder().append(str))
        .compute();
    Assertions.assertEquals(a, b);
    Assertions.assertEquals(a, c);
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
}
