/*
 * Copyright 2014-2022 Netflix, Inc.
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
package com.netflix.spectator.atlas;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Timer;
import com.netflix.spectator.api.Utils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;


public class AtlasTimerTest {

  private final CountingManualClock clock = new CountingManualClock();
  private final long step = 10000L;
  private final AtlasTimer dist = new AtlasTimer(Id.create("test"), clock, step, step);

  private void checkValue(long count, double amount, double square, long max) {
    int num = 0;
    for (Measurement m : dist.measure()) {
      String stat = Utils.getTagValue(m.id(), "statistic");
      DsType ds = "max".equals(stat) ? DsType.gauge : DsType.rate;
      Id expectedId = dist.id().withTag(ds).withTag("statistic", stat);
      Assertions.assertEquals(expectedId, m.id());
      switch (stat) {
        case "count":
          Assertions.assertEquals(count / 10.0, m.value(), 1e-12);
          break;
        case "totalTime":
          Assertions.assertEquals(amount / 10.0e9, m.value(), 1e-12);
          break;
        case "totalOfSquares":
          Assertions.assertEquals(square / 10.0e18, m.value(), 1e-12);
          break;
        case "max":
          Assertions.assertEquals(max / 1e9, m.value(), 1e-12);
          break;
        default:
          throw new IllegalArgumentException("unknown stat: " + stat);
      }
      Assertions.assertEquals(count, dist.count());
      // This method cannot handle overflows
      if (amount < Long.MAX_VALUE) {
        Assertions.assertEquals(amount, dist.totalTime());
      }
      ++num;
    }
    Assertions.assertEquals(4, num);
  }

  @Test
  public void measuredIdHasDsType() {
    checkValue(0, 0, 0, 0);
  }

  @Test
  public void recordOne() {
    dist.record(1, TimeUnit.NANOSECONDS);
    checkValue(0, 0, 0, 0);

    clock.setWallTime(step + 1);
    checkValue(1, 1, 1, 1);
  }

  @Test
  public void recordTwo() {
    dist.record(2, TimeUnit.NANOSECONDS);
    checkValue(0, 0, 0, 0);

    clock.setWallTime(step + 1);
    checkValue(1, 2, 4, 2);
  }

  @Test
  public void recordMillis() {
    dist.record(2, TimeUnit.MILLISECONDS);
    clock.setWallTime(step + 1);
    checkValue(1, 2000000L, 4000000000000L, 2000000L);
  }

  @Test
  public void recordOverflow() {
    // Simulate case where we have old items in a queue and when processing again we record
    // the ages of many of those items in a short span
    long amount = TimeUnit.DAYS.toNanos(14);
    double square = 0.0;
    for (int i = 0; i < 10000; ++i) {
      dist.record(amount, TimeUnit.NANOSECONDS);
      square += (double) amount * amount;
    }
    clock.setWallTime(step + 1);
    checkValue(10000, 10e3 * amount, square, amount);
  }

  @Test
  public void recordSquaresOverflow() {
    long v = (long) (Math.sqrt(Long.MAX_VALUE) / 1e9) + 1;
    dist.record(v, TimeUnit.SECONDS);
    clock.setWallTime(step + 1);
    double square = 1e18 * v * v;
    checkValue(1, v * 1000000000L, square, v * 1000000000L);
  }

  @Test
  public void recordZero() {
    dist.record(0, TimeUnit.NANOSECONDS);
    clock.setWallTime(step + 1);
    checkValue(1, 0, 0, 0);
  }

  @Test
  public void recordNegativeValue() {
    dist.record(-2, TimeUnit.NANOSECONDS);
    clock.setWallTime(step + 1);
    checkValue(1, 0, 0, 0);
  }

  @Test
  public void recordSeveralValues() {
    dist.record(1, TimeUnit.NANOSECONDS);
    dist.record(2, TimeUnit.NANOSECONDS);
    dist.record(3, TimeUnit.NANOSECONDS);
    dist.record(1, TimeUnit.NANOSECONDS);
    clock.setWallTime(step + 1);
    checkValue(4, 1 + 2 + 3 + 1, 1 + 4 + 9 + 1, 3);
  }

  public void recordSeveralValuesBatch(int batchSize) throws Exception {
    try (Timer.BatchUpdater b = dist.batchUpdater(batchSize)) {
      b.record(1, TimeUnit.NANOSECONDS);
      b.record(2, TimeUnit.NANOSECONDS);
      b.record(3, TimeUnit.NANOSECONDS);
      b.record(1, TimeUnit.NANOSECONDS);
    }
    clock.setWallTime(step + 1);
    checkValue(4, 1 + 2 + 3 + 1, 1 + 4 + 9 + 1, 3);
  }

  @Test
  public void recordSeveralValuesBatch() throws Exception {
    recordSeveralValuesBatch(1);
    recordSeveralValuesBatch(2);
    recordSeveralValuesBatch(3);
    recordSeveralValuesBatch(4);
    recordSeveralValuesBatch(5);
  }

  @Test
  public void recordBatchMismatchedLengths() {
    dist.record(new long[0], 1, TimeUnit.NANOSECONDS);
    clock.setWallTime(1 * step + 1);
    checkValue(0, 0, 0, 0);

    dist.record(new long[1], 0, TimeUnit.NANOSECONDS);
    clock.setWallTime(2 * step + 1);
    checkValue(0, 0, 0, 0);

    dist.record(new long[1], -1, TimeUnit.NANOSECONDS);
    clock.setWallTime(3 * step + 1);
    checkValue(0, 0, 0, 0);

    dist.record(new long[]{ 0, 0 }, 2, TimeUnit.NANOSECONDS);
    clock.setWallTime(4 * step + 1);
    checkValue(2, 0, 0, 0);
  }

  @Test
  public void recordBatchZero() {
    dist.record(new long[]{ 0 }, 1, TimeUnit.NANOSECONDS);
    checkValue(0, 0, 0, 0);

    clock.setWallTime(step + 1);
    checkValue(1, 0, 0, 0);
  }

  @Test
  public void recordBatchOne() {
    dist.record(new long[]{ 1 }, 1, TimeUnit.NANOSECONDS);
    checkValue(0, 0, 0, 0);

    clock.setWallTime(step + 1);
    checkValue(1, 1, 1, 1);
  }

  @Test
  public void recordBatchTwo() {
    dist.record(new long[]{ 2 }, 1, TimeUnit.NANOSECONDS);
    checkValue(0, 0, 0, 0);

    clock.setWallTime(step + 1);
    checkValue(1, 2, 4, 2);
  }


  @Test
  public void recordBatchOverflowSingle() {
    // Simulate case where we have old items in a queue and when processing again we record
    // the ages of many of those items in a short span
    long amount = TimeUnit.DAYS.toNanos(14);
    double square = 0.0;
    for (int i = 0; i < 10000; ++i) {
      dist.record(new long[]{ amount }, 1, TimeUnit.NANOSECONDS);
      square += (double) amount * amount;
    }
    clock.setWallTime(step + 1);
    checkValue(10000, 10e3 * amount, square, amount);
  }

  @Test
  public void recordBatchOverflowBatch() {
    // Simulate case where we have old items in a queue and when processing again we record
    // the ages of many of those items in a short span
    long amount = TimeUnit.DAYS.toNanos(14);
    double square = 0.0;

    int COUNT = 10000;
    long[] amounts = new long[COUNT];
    Arrays.fill(amounts, amount);

    dist.record(amounts, COUNT, TimeUnit.NANOSECONDS);

    for (int i = 0; i < 10000; i++) {
      square += (double) amount * amount;
    }

    clock.setWallTime(step + 1);
    checkValue(10000, 10e3 * amount, square, amount);
  }

  @Test
  public void recordBatchMixedPositiveNegativeValues() {
    dist.record(new long[]{ 1, 0, 2, -1, 3, -4, 1}, 7, TimeUnit.NANOSECONDS);
    clock.setWallTime(step + 1);
    checkValue(7, 1 + 2 + 3 + 1, 1 + 4 + 9 + 1, 3);
  }

  @Test
  public void recordBatchSeveralValues() {
    dist.record(new long[]{ 1, 2, 3, 1}, 4, TimeUnit.NANOSECONDS);
    clock.setWallTime(step + 1);
    checkValue(4, 1 + 2 + 3 + 1, 1 + 4 + 9 + 1, 3);
  }

  @Test
  public void recordBatchPollsClockOnce() {
    long[] amounts = new long[10000];
    Arrays.fill(amounts, 1L);

    long countPollsBefore = clock.countPolled();
    dist.record(amounts, amounts.length, TimeUnit.NANOSECONDS);
    long actualPolls = clock.countPolled() - countPollsBefore;

    Assertions.assertEquals(1, actualPolls);
  }

  @Test
  public void recordRunnable() {
    dist.record(() -> clock.setMonotonicTime(clock.monotonicTime() + 2));
    clock.setWallTime(step + 1);
    checkValue(1, 2, 4, 2);
  }

  @Test
  public void recordCallable() throws Exception {
    String s = dist.record(() -> {
      clock.setMonotonicTime(clock.monotonicTime() + 2);
      return "foo";
    });
    Assertions.assertEquals("foo", s);
    clock.setWallTime(step + 1);
    checkValue(1, 2, 4, 2);
  }

  @Test
  public void rollForward() {
    dist.record(42, TimeUnit.NANOSECONDS);
    clock.setWallTime(step + 1);
    checkValue(1, 42, 42 * 42, 42);
    clock.setWallTime(step + step + 1);
    checkValue(0, 0, 0, 0);
  }

  @Test
  public void expiration() {
    long start = clock.wallTime();
    clock.setWallTime(start + step * 2);
    Assertions.assertTrue(dist.hasExpired());

    dist.record(42, TimeUnit.MICROSECONDS);
    Assertions.assertFalse(dist.hasExpired());

    clock.setWallTime(start + step * 3 + 1);
    Assertions.assertTrue(dist.hasExpired());
  }
}
