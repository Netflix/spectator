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

import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.ManualClock;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Statistic;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


public class AtlasGaugeTest {

  private ManualClock clock = new ManualClock();
  private Registry registry = new DefaultRegistry();
  private long step = 10000L;
  private AtlasGauge gauge = new AtlasGauge(registry.createId("test"), clock, step);

  private void checkValue(long expected) {
    int count = 0;
    for (Measurement m : gauge.measure()) {
      Assertions.assertEquals(gauge.id().withTags(Statistic.gauge, DsType.gauge), m.id());
      Assertions.assertEquals(expected, m.value(), 1e-12);
      ++count;
    }
    Assertions.assertEquals(1, count);
  }

  @Test
  public void measuredIdHasDsType() {
    checkValue(0);
  }

  @Test
  public void set() {
    gauge.set(42);
    checkValue(42);

    clock.setWallTime(step + 1);
    checkValue(42);
  }

  @Test
  public void rollForward() {
    gauge.set(42);
    clock.setWallTime(step + 1);
    checkValue(42);
    clock.setWallTime(step + step + 1);
    checkValue(42);
  }

  @Test
  public void expiration() {
    long start = clock.wallTime();
    clock.setWallTime(start + step * 2);
    Assertions.assertTrue(gauge.hasExpired());

    gauge.set(1);
    Assertions.assertFalse(gauge.hasExpired());

    clock.setWallTime(start + step * 3 + 1);
    Assertions.assertTrue(gauge.hasExpired());

    gauge.set(1);
    Assertions.assertFalse(gauge.hasExpired());
  }
}
