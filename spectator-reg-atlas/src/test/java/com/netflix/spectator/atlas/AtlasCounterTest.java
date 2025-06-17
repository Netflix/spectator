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
package com.netflix.spectator.atlas;

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.ManualClock;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Statistic;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


public class AtlasCounterTest {

  private final ManualClock clock = new ManualClock();
  private final long step = 10000L;
  private final AtlasCounter counter = new AtlasCounter(Id.create("test"), clock, step, step);

  private void checkValue(double expected) {
    int count = 0;
    for (Measurement m : counter.measure()) {
      Assertions.assertEquals(counter.id().withTags(Statistic.count, DsType.rate), m.id());
      Assertions.assertEquals(expected / 10.0, m.value(), 1e-12);
      Assertions.assertEquals(expected, counter.actualCount(), 1e-12);
      ++count;
    }
    Assertions.assertEquals(1, count);
  }

  @Test
  public void measuredIdHasDsType() {
    checkValue(0);
  }

  @Test
  public void increment() {
    counter.increment();
    checkValue(0);

    clock.setWallTime(step + 1);
    checkValue(1);
  }

  @Test
  public void incrementAmount() {
    counter.increment(42);
    checkValue(0);

    clock.setWallTime(step + 1);
    checkValue(42);
  }

  @Test
  public void addAmount() {
    counter.add(42.1);
    clock.setWallTime(step + 1);
    checkValue(42.1);
  }

  @Test
  public void addNegativeAmount() {
    counter.add(-42.0);
    clock.setWallTime(step + 1);
    checkValue(0.0);
  }

  @Test
  public void addNaN() {
    counter.add(1.0);
    counter.add(Double.NaN);
    clock.setWallTime(step + 1);
    checkValue(1.0);
  }

  @Test
  public void addInfinity() {
    counter.add(Double.POSITIVE_INFINITY);
    clock.setWallTime(step + 1);
    checkValue(0.0);
  }

  @Test
  public void rollForward() {
    counter.increment(42);
    clock.setWallTime(step + 1);
    checkValue(42);
    clock.setWallTime(step + step + 1);
    checkValue(0);
  }

  @Test
  public void expiration() {
    long start = clock.wallTime();
    clock.setWallTime(start + step * 2);
    Assertions.assertTrue(counter.hasExpired());

    counter.increment();
    Assertions.assertFalse(counter.hasExpired());

    clock.setWallTime(start + step * 3 + 1);
    Assertions.assertTrue(counter.hasExpired());

    counter.increment(42L);
    Assertions.assertFalse(counter.hasExpired());
  }

  @Test
  public void preferStatisticFromTags() {
    Id id = Id.create("test").withTag(Statistic.percentile);
    AtlasCounter c = new AtlasCounter(id, clock, step, step);
    Id actual = c.measure().iterator().next().id();
    Assertions.assertEquals(id.withTag(DsType.rate), actual);
  }

  @Test
  public void batchUpdate() throws Exception {
    try (Counter.BatchUpdater b = counter.batchUpdater(2)) {
      b.increment();
      b.add(Double.POSITIVE_INFINITY);
      b.add(Double.NaN);
      b.add(-1.0);
      clock.setWallTime(step + 1);
      Assertions.assertEquals(0, counter.count());
      b.increment();
      clock.setWallTime(step * 2 + 1);
      Assertions.assertEquals(2, counter.count());
      b.increment(42);
      clock.setWallTime(step * 3 + 1);
      Assertions.assertEquals(0, counter.count());
    }
    clock.setWallTime(step * 4 + 1);
    Assertions.assertEquals(42, counter.count());
  }
}
