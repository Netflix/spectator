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
package com.netflix.spectator.atlas;

import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.ManualClock;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Statistic;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;


@RunWith(JUnit4.class)
public class AtlasDoubleCounterTest {

  private ManualClock clock = new ManualClock();
  private Registry registry = new DefaultRegistry();
  private long step = 10000L;
  private AtlasDoubleCounter counter = new AtlasDoubleCounter(registry.createId("test"), clock, step, step);

  private void checkValue(double expected) {
    int count = 0;
    for (Measurement m : counter.measure()) {
      Assert.assertEquals(counter.id().withTags(Statistic.count, DsType.rate), m.id());
      Assert.assertEquals(expected / 10.0, m.value(), 1e-12);
      Assert.assertEquals(expected, counter.actualCount(), 1e-12);
      Assert.assertEquals((long) expected, counter.count());
      ++count;
    }
    Assert.assertEquals(1, count);
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
  public void add() {
    counter.add(42.2);
    checkValue(0);

    clock.setWallTime(step + 1);
    checkValue(42.2);
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
    Assert.assertTrue(counter.hasExpired());

    counter.increment();
    Assert.assertFalse(counter.hasExpired());

    clock.setWallTime(start + step * 3 + 1);
    Assert.assertTrue(counter.hasExpired());

    counter.increment(42L);
    Assert.assertFalse(counter.hasExpired());
  }
}
