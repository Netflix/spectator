/*
 * Copyright 2014-2019 Netflix, Inc.
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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.concurrent.TimeUnit;

public class PercentileBucketsTest {

  @Test
  public void indexOf() {
    Assertions.assertEquals(0, PercentileBuckets.indexOf(-1));
    Assertions.assertEquals(0, PercentileBuckets.indexOf(0));
    Assertions.assertEquals(1, PercentileBuckets.indexOf(1));
    Assertions.assertEquals(2, PercentileBuckets.indexOf(2));
    Assertions.assertEquals(3, PercentileBuckets.indexOf(3));
    Assertions.assertEquals(4, PercentileBuckets.indexOf(4));

    Assertions.assertEquals(25, PercentileBuckets.indexOf(87));

    Assertions.assertEquals(PercentileBuckets.length() - 1, PercentileBuckets.indexOf(Long.MAX_VALUE));
  }

  @Test
  public void indexOfSanityCheck() {
    Random r = new Random(42);
    for (int i = 0; i < 10000; ++i) {
      long v = r.nextLong();
      if (v < 0) {
        Assertions.assertEquals(0, PercentileBuckets.indexOf(v));
      } else {
        long b = PercentileBuckets.get(PercentileBuckets.indexOf(v));
        Assertions.assertTrue(v <= b, String.format("%d > %d", v, b));
      }
    }
  }

  @Test
  public void bucketSanityCheck() {
    Random r = new Random(42);
    for (int i = 0; i < 10000; ++i) {
      long v = r.nextLong();
      if (v < 0) {
        Assertions.assertEquals(1, PercentileBuckets.bucket(v));
      } else {
        long b = PercentileBuckets.bucket(v);
        Assertions.assertTrue(v <= b, String.format("%d > %d", v, b));
      }
    }
  }

  @Test
  public void asArray() {
    long[] values = PercentileBuckets.asArray();
    Assertions.assertEquals(PercentileBuckets.length(), values.length);
    for (int i = 0; i < values.length; ++i) {
      Assertions.assertEquals(PercentileBuckets.get(i), values[i]);
    }
  }

  @Test
  public void asArrayIsCopy() {
    long[] values = PercentileBuckets.asArray();
    values[0] = 42;
    Assertions.assertEquals(1, PercentileBuckets.get(0));
  }

  @Test
  public void map() {
    String[] values = PercentileBuckets.map(String.class, v -> String.format("%016X", v));
    Assertions.assertEquals(PercentileBuckets.length(), values.length);
    for (int i = 0; i < values.length; ++i) {
      Assertions.assertEquals(PercentileBuckets.get(i), Long.parseLong(values[i], 16));
    }
  }

  @Test
  public void percentiles() {
    long[] counts = new long[PercentileBuckets.length()];
    for (int i = 0; i < 100_000; ++i) {
      ++counts[PercentileBuckets.indexOf(i)];
    }

    double[] pcts = new double[] {0.0, 25.0, 50.0, 75.0, 90.0, 95.0, 98.0, 99.0, 99.5, 100.0};
    double[] results = new double[pcts.length];

    PercentileBuckets.percentiles(counts, pcts, results);

    double[] expected = new double[] {0.0, 25e3, 50e3, 75e3, 90e3, 95e3, 98e3, 99e3, 99.5e3, 100e3};
    double threshold = 0.1 * 100_000; // quick check, should be within 10% of total
    Assertions.assertArrayEquals(expected, results, threshold);

    // Further check each value is within 10% of actual percentile
    for (int i = 0 ; i < results.length; ++i) {
      threshold = 0.1 * expected[i] + 1e-12;
      Assertions.assertEquals(expected[i], results[i], threshold);
    }
  }

  @Test
  public void percentile() {
    long[] counts = new long[PercentileBuckets.length()];
    for (int i = 0; i < 100_000; ++i) {
      ++counts[PercentileBuckets.indexOf(i)];
    }

    double[] pcts = new double[] {0.0, 25.0, 50.0, 75.0, 90.0, 95.0, 98.0, 99.0, 99.5, 100.0};
    for (int i = 0 ; i < pcts.length; ++i) {
      double expected = pcts[i] * 1e3;
      double threshold = 0.1 * expected + 1e-12;
      Assertions.assertEquals(expected, PercentileBuckets.percentile(counts, pcts[i]), threshold);
    }
  }
}
