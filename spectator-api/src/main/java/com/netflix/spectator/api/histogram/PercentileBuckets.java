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
package com.netflix.spectator.api.histogram;

import com.netflix.spectator.impl.Preconditions;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.function.Function;

/**
 * Bucket values for estimating a percentile from a set of non-negative long values. This class
 * acts as an immutable array of the buckets along with providing some helper functions.
 */
public final class PercentileBuckets {

  private PercentileBuckets() {
  }

  /** Returns a copy of the bucket values array. */
  public static long[] asArray() {
    long[] values = new long[BUCKET_VALUES.length];
    System.arraycopy(BUCKET_VALUES, 0, values, 0, BUCKET_VALUES.length);
    return values;
  }

  /** Map the bucket values to a new array of a different type. */
  public static <T> T[] map(Class<T> c, Function<Long, T> f) {
    @SuppressWarnings("unchecked")
    T[] values = (T[]) Array.newInstance(c, BUCKET_VALUES.length);
    for (int i = 0; i < BUCKET_VALUES.length; ++i) {
      values[i] = f.apply(BUCKET_VALUES[i]);
    }
    return values;
  }

  /** Return the value of the bucket at index {@code i}. */
  public static long get(int i) {
    return BUCKET_VALUES[i];
  }

  /** Returns the number of buckets. */
  public static int length() {
    return BUCKET_VALUES.length;
  }

  /**
   * Returns the value the index of the bucket that should be used for {@code v}. The bucket value
   * can be retrieved using {@link #get(int)}.
   */
  public static int indexOf(long v) {
    if (v <= 0) {
      return 0;
    } else if (v <= 4) {
      return (int) v;
    } else {
      int lz = Long.numberOfLeadingZeros(v);
      int shift = 64 - lz - 1;
      long prevPowerOf2 = (v >> shift) << shift;
      long prevPowerOf4 = prevPowerOf2;
      if (shift % 2 != 0) {
        shift--;
        prevPowerOf4 = prevPowerOf2 >> 1;
      }

      long base = prevPowerOf4;
      long delta = base / 3;
      int offset = (int) ((v - base) / delta);
      int pos = offset + POWER_OF_4_INDEX[shift / 2];
      return (pos >= BUCKET_VALUES.length - 1) ? BUCKET_VALUES.length - 1 : pos + 1;
    }
  }

  /** Returns the value of the bucket that should be used for {@code v}. */
  public static long bucket(long v) {
    return BUCKET_VALUES[indexOf(v)];
  }

  /**
   * Compute a set of percentiles based on the counts for the buckets.
   *
   * @param counts
   *     Counts for each of the buckets. The size must be the same as {@link #length()} and the
   *     positions must correspond to the positions of the bucket values.
   * @param pcts
   *     Array with the requested percentile values. The length must be at least 1 and the
   *     array should be sorted. Each value, {@code v}, should adhere to {@code 0.0 <= v <= 100.0}.
   * @param results
   *     The calculated percentile values will be written to the results array. It should have the
   *     same length as {@code pcts}.
   */
  public static void percentiles(long[] counts, double[] pcts, double[] results) {
    Preconditions.checkArg(counts.length == BUCKET_VALUES.length,
        "counts is not the same size as buckets array");
    Preconditions.checkArg(pcts.length > 0, "pct array cannot be empty");
    Preconditions.checkArg(pcts.length == results.length,
        "pcts is not the same size as results array");

    long total = 0L;
    for (long c : counts) {
      total += c;
    }

    int pctIdx = 0;

    long prev = 0;
    double prevP = 0.0;
    long prevB = 0;
    for (int i = 0; i < BUCKET_VALUES.length; ++i) {
      long next = prev + counts[i];
      double nextP = 100.0 * next / total;
      long nextB = BUCKET_VALUES[i];
      while (pctIdx < pcts.length && nextP >= pcts[pctIdx]) {
        double f = (pcts[pctIdx] - prevP) / (nextP - prevP);
        if (Double.isNaN(f))
          results[pctIdx] = 0.0;
        else
          results[pctIdx] = f * (nextB - prevB) + prevB;
        ++pctIdx;
      }
      if (pctIdx >= pcts.length) break;
      prev = next;
      prevP = nextP;
      prevB = nextB;
    }

    double nextP = 100.0;
    long nextB = Long.MAX_VALUE;
    while (pctIdx < pcts.length) {
      double f = (pcts[pctIdx] - prevP) / (nextP - prevP);
      results[pctIdx] = f * (nextB - prevB) + prevB;
      ++pctIdx;
    }
  }

  /**
   * Compute a percentile based on the counts for the buckets.
   *
   * @param counts
   *     Counts for each of the buckets. The size must be the same as {@link #length()} and the
   *     positions must correspond to the positions of the bucket values.
   * @param p
   *     Percentile to compute, the value should be {@code 0.0 <= p <= 100.0}.
   * @return
   *     The calculated percentile value.
   */
  public static double percentile(long[] counts, double p) {
    double[] pcts = {p};
    double[] results = new double[1];
    percentiles(counts, pcts, results);
    return results[0];
  }

  /**
   * Compute a set of percentiles based on the counts for the buckets.
   *
   * @param counts
   *     Counts for each of the buckets. The values should be a non-negative finite double
   *     indicating the relative amount for that bucket. The size must be the same as
   *     {@link #length()} and the positions must correspond to the positions of the bucket values.
   * @param pcts
   *     Array with the requested percentile values. The length must be at least 1 and the
   *     array should be sorted. Each value, {@code v}, should adhere to {@code 0.0 <= v <= 100.0}.
   * @param results
   *     The calculated percentile values will be written to the results array. It should have the
   *     same length as {@code pcts}.
   */
  public static void percentiles(double[] counts, double[] pcts, double[] results) {
    Preconditions.checkArg(counts.length == BUCKET_VALUES.length,
        "counts is not the same size as buckets array");
    Preconditions.checkArg(pcts.length > 0, "pct array cannot be empty");
    Preconditions.checkArg(pcts.length == results.length,
        "pcts is not the same size as results array");

    int lastNonZeroIdx = 0;
    double total = 0.0;
    for (int i = 0; i < counts.length; ++i) {
      double c = counts[i];
      if (c > 0.0 && Double.isFinite(c)) {
        total += c;
        lastNonZeroIdx = i;
      }
    }

    int pctIdx = 0;

    double prev = 0.0;
    double prevP = 0.0;
    long prevB = 0;
    for (int i = 0; i <= lastNonZeroIdx; ++i) {
      double next = prev + counts[i];
      double nextP = 100.0 * next / total;
      long nextB = BUCKET_VALUES[i];
      while (pctIdx < pcts.length && nextP >= pcts[pctIdx]) {
        double f = (pcts[pctIdx] - prevP) / (nextP - prevP);
        if (Double.isNaN(f))
          results[pctIdx] = 0.0;
        else
          results[pctIdx] = f * (nextB - prevB) + prevB;
        ++pctIdx;
      }
      if (pctIdx >= pcts.length) break;
      prev = next;
      prevP = nextP;
      prevB = nextB;
    }

    double nextP = 100.0;
    long nextB = BUCKET_VALUES[lastNonZeroIdx];
    while (pctIdx < pcts.length) {
      double f = (pcts[pctIdx] - prevP) / (nextP - prevP);
      results[pctIdx] = f * (nextB - prevB) + prevB;
      ++pctIdx;
    }
  }

  /**
   * Compute a percentile based on the counts for the buckets.
   *
   * @param counts
   *     Counts for each of the buckets. The values should be a non-negative finite double
   *     indicating the relative amount for that bucket. The size must be the same as
   *     {@link #length()} and the positions must correspond to the positions of the bucket values.
   * @param p
   *     Percentile to compute, the value should be {@code 0.0 <= p <= 100.0}.
   * @return
   *     The calculated percentile value.
   */
  public static double percentile(double[] counts, double p) {
    double[] pcts = {p};
    double[] results = new double[1];
    percentiles(counts, pcts, results);
    return results[0];
  }

  // Number of positions of base-2 digits to shift when iterating over the long space.
  private static final int DIGITS = 2;

  // Bucket values to use, see static block for initialization.
  private static final long[] BUCKET_VALUES;

  // Keeps track of the positions for even powers of 4 within BUCKET_VALUES. This is used to
  // quickly compute the offset for a long without traversing the array.
  private static final int[] POWER_OF_4_INDEX;

  // The set of buckets is generated by using powers of 4 and incrementing by one-third of the
  // previous power of 4 in between as long as the value is less than the next power of 4 minus
  // the delta.
  //
  // <pre>
  // Base: 1, 2, 3
  //
  // 4 (4^1), delta = 1
  //     5, 6, 7, ..., 14,
  //
  // 16 (4^2), delta = 5
  //    21, 26, 31, ..., 56,
  //
  // 64 (4^3), delta = 21
  // ...
  // </pre>
  static {
    ArrayList<Integer> powerOf4Index = new ArrayList<>();
    powerOf4Index.add(0);

    ArrayList<Long> buckets = new ArrayList<>();
    buckets.add(1L);
    buckets.add(2L);
    buckets.add(3L);

    int exp = DIGITS;
    while (exp < 64) {
      long current = 1L << exp;
      long delta = current / 3;
      long next = (current << DIGITS) - delta;

      powerOf4Index.add(buckets.size());
      while (current < next) {
        buckets.add(current);
        current += delta;
      }
      exp += DIGITS;
    }
    buckets.add(Long.MAX_VALUE);

    BUCKET_VALUES = new long[buckets.size()];
    for (int i = 0; i < buckets.size(); ++i) {
      BUCKET_VALUES[i] = buckets.get(i);
    }

    POWER_OF_4_INDEX = new int[powerOf4Index.size()];
    for (int i = 0; i < powerOf4Index.size(); ++i) {
      POWER_OF_4_INDEX[i] = powerOf4Index.get(i);
    }
  }
}
