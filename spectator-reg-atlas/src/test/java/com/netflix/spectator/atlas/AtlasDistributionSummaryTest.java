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
package com.netflix.spectator.atlas;

import java.util.Arrays;

import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.ManualClock;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Utils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


public class AtlasDistributionSummaryTest {

  private final ManualClock clock = new ManualClock();
  private final Registry registry = new DefaultRegistry();
  private final long step = 10000L;
  private final AtlasDistributionSummary dist = new AtlasDistributionSummary(registry.createId("test"), clock, step, step);

  private void checkValue(long count, long amount, long square, long max) {
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
        case "totalAmount":
          Assertions.assertEquals(amount / 10.0, m.value(), 1e-12);
          break;
        case "totalOfSquares":
          Assertions.assertEquals(square / 10.0, m.value(), 1e-12);
          break;
        case "max":
          Assertions.assertEquals(max, m.value(), 1e-12);
          break;
        default:
          throw new IllegalArgumentException("unknown stat: " + stat);
      }
      Assertions.assertEquals(count, dist.count());
      Assertions.assertEquals(amount, dist.totalAmount());
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
    dist.record(1);
    checkValue(0, 0, 0, 0);

    clock.setWallTime(step + 1);
    checkValue(1, 1, 1, 1);
  }

  @Test
  public void recordTwo() {
    dist.record(2);
    checkValue(0, 0, 0, 0);

    clock.setWallTime(step + 1);
    checkValue(1, 2, 4, 2);
  }

  @Test
  public void recordZero() {
    dist.record(0);
    clock.setWallTime(step + 1);
    checkValue(1, 0, 0, 0);
  }

  @Test
  public void recordNegativeValue() {
    dist.record(-2);
    clock.setWallTime(step + 1);
    checkValue(1, 0, 0, 0);
  }

  @Test
  public void recordSeveralValues() {
    dist.record(1);
    dist.record(2);
    dist.record(3);
    dist.record(1);
    clock.setWallTime(step + 1);
    checkValue(4, 1 + 2 + 3 + 1, 1 + 4 + 9 + 1, 3);
  }

  @Test
  public void recordBatchMismatchedLengths() {
    dist.record(new long[0], 1);
    clock.setWallTime(1 * step + 1);
    checkValue(0, 0, 0, 0);

    dist.record(new long[1], 0);
    clock.setWallTime(2 * step + 1);
    checkValue(0, 0, 0, 0);

    dist.record(new long[1], -1);
    clock.setWallTime(3 * step + 1);
    checkValue(0, 0, 0, 0);

    dist.record(new long[]{ 0, 0 }, 2);
    clock.setWallTime(4 * step + 1);
    checkValue(2, 0, 0, 0);
  }

  @Test
  public void recordBatchOne() {
    dist.record(new long[]{ 1 }, 1);
    checkValue(0, 0, 0, 0);

    clock.setWallTime(step + 1);
    checkValue(1, 1, 1, 1);
  }

  @Test
  public void recordBatchTwo() {
    dist.record(new long[]{ 2 }, 1);
    checkValue(0, 0, 0, 0);

    clock.setWallTime(step + 1);
    checkValue(1, 2, 4, 2);
  }

  @Test
  public void recordBatchSeveralValues() {
    dist.record(new long[]{ 1, 2, 3, 1 }, 4);
    checkValue(0, 0, 0, 0);

    clock.setWallTime(step + 1);
    checkValue(4, 1 + 2 + 3 + 1, 1 + 4 + 9 + 1, 3);
  }

  @Test
  public void recordBatchWithIgnoredValuesMixed() {
    dist.record(new long[]{ 1, -1, 0, 2, -1, 0, 3, 0, -1, 1 }, 10);
    checkValue(0, 0, 0, 0);

    clock.setWallTime(step + 1);
    checkValue(10, 1 + 2 + 3 + 1, 1 + 4 + 9 + 1, 3);
  }

  @Test
  public void recordBatchPollsClockOnce() {
    long[] amounts = new long[10000];
    Arrays.fill(amounts, 1L);

    long countPollsBefore = clock.countPolled();
    dist.record(amounts, amounts.length);
    long actualPolls = clock.countPolled() - countPollsBefore;

    Assertions.assertEquals(1, actualPolls);
  }

  @Test
  public void rollForward() {
    dist.record(42);
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

    dist.record(42);
    Assertions.assertFalse(dist.hasExpired());

    clock.setWallTime(start + step * 3 + 1);
    Assertions.assertTrue(dist.hasExpired());
  }
}
