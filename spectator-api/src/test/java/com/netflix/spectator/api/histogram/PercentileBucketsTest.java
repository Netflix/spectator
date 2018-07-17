/*
 * Copyright 2014-2018 Netflix, Inc.
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

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@RunWith(JUnit4.class)
public class PercentileBucketsTest {

  @Test
  public void indexOf() {
    Assert.assertEquals(0, PercentileBuckets.indexOf(-1));
    Assert.assertEquals(0, PercentileBuckets.indexOf(0));
    Assert.assertEquals(1, PercentileBuckets.indexOf(1));
    Assert.assertEquals(2, PercentileBuckets.indexOf(2));
    Assert.assertEquals(3, PercentileBuckets.indexOf(3));
    Assert.assertEquals(4, PercentileBuckets.indexOf(4));

    Assert.assertEquals(25, PercentileBuckets.indexOf(87));

    Assert.assertEquals(PercentileBuckets.length() - 1, PercentileBuckets.indexOf(Long.MAX_VALUE));
  }

  @Test
  public void indexOfSanityCheck() {
    Random r = new Random(42);
    for (int i = 0; i < 10000; ++i) {
      long v = r.nextLong();
      if (v < 0) {
        Assert.assertEquals(0, PercentileBuckets.indexOf(v));
      } else {
        long b = PercentileBuckets.get(PercentileBuckets.indexOf(v));
        Assert.assertTrue(String.format("%d > %d", v, b), v <= b);
      }
    }
  }

  @Test
  public void bucketSanityCheck() {
    Random r = new Random(42);
    for (int i = 0; i < 10000; ++i) {
      long v = r.nextLong();
      if (v < 0) {
        Assert.assertEquals(1, PercentileBuckets.bucket(v));
      } else {
        long b = PercentileBuckets.bucket(v);
        Assert.assertTrue(String.format("%d > %d", v, b), v <= b);
      }
    }
  }

  @Test
  public void asArray() {
    long[] values = PercentileBuckets.asArray();
    Assert.assertEquals(PercentileBuckets.length(), values.length);
    for (int i = 0; i < values.length; ++i) {
      Assert.assertEquals(PercentileBuckets.get(i), values[i]);
    }
  }

  @Test
  public void asArrayIsCopy() {
    long[] values = PercentileBuckets.asArray();
    values[0] = 42;
    Assert.assertEquals(1, PercentileBuckets.get(0));
  }

  @Test
  public void map() {
    String[] values = PercentileBuckets.map(String.class, v -> String.format("%016X", v));
    Assert.assertEquals(PercentileBuckets.length(), values.length);
    for (int i = 0; i < values.length; ++i) {
      Assert.assertEquals(PercentileBuckets.get(i), Long.parseLong(values[i], 16));
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
    Assert.assertArrayEquals(expected, results, threshold);

    // Further check each value is within 10% of actual percentile
    for (int i = 0 ; i < results.length; ++i) {
      threshold = 0.1 * expected[i];
      Assert.assertEquals(expected[i], results[i], threshold);
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
      double threshold = 0.1 * expected;
      Assert.assertEquals(expected, PercentileBuckets.percentile(counts, pcts[i]), threshold);
    }
  }

  @Test
  public void foo() {
    int start = PercentileBuckets.indexOf(TimeUnit.MILLISECONDS.toNanos(10));
    int end = PercentileBuckets.indexOf(TimeUnit.SECONDS.toNanos(60));
    System.out.println("start = " + start + ", end = " + end + ", delta = " + (end - start + 1));
  }
}
