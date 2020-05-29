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
package com.netflix.spectator.servo;

import com.netflix.servo.monitor.Monitor;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.ManualClock;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ServoCounterTest {

  private final ManualClock clock = new ManualClock();

  private Counter newCounter(String name) {
    final ServoRegistry r = Servo.newRegistry(clock);
    return r.newCounter(r.createId(name));
  }

  @BeforeEach
  public void before() {
    clock.setWallTime(0L);
    clock.setMonotonicTime(0L);
  }

  @Test
  public void testInit() {
    Counter c = newCounter("foo");
    Assertions.assertEquals(c.count(), 0L);
    c.increment();
    Assertions.assertEquals(c.count(), 1L);
  }

  @Test
  public void expiration() {
    final long initTime = TimeUnit.MINUTES.toMillis(30);
    final long fifteenMinutes = TimeUnit.MINUTES.toMillis(15);

    // Not expired on init, wait for activity to mark as active
    clock.setWallTime(initTime);
    Counter c = newCounter("foo");
    Assertions.assertFalse(c.hasExpired());
    c.increment(42);
    Assertions.assertFalse(c.hasExpired());

    // Expires with inactivity, total count in memory is maintained
    clock.setWallTime(initTime + fifteenMinutes);
    Assertions.assertFalse(c.hasExpired());

    // Expires with inactivity, total count in memory is maintained
    clock.setWallTime(initTime + fifteenMinutes + 1);
    Assertions.assertEquals(c.count(), 42);
    Assertions.assertTrue(c.hasExpired());

    // Activity brings it back
    c.increment();
    Assertions.assertEquals(c.count(), 43);
    Assertions.assertFalse(c.hasExpired());
  }

  @Test
  public void hasStatistic() {
    List<Monitor<?>> ms = new ArrayList<>();
    Counter c = newCounter("foo");
    ((ServoCounter) c).addMonitors(ms);
    Assertions.assertEquals(1, ms.size());
    Assertions.assertEquals("count", ms.get(0).getConfig().getTags().getValue("statistic"));
  }

}
