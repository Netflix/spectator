/**
 * Copyright 2014 Netflix, Inc.
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
package com.netflix.spectator.api;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DefaultLongTaskTimerTest {
  private final ManualClock clock = new ManualClock();

  @Test
  public void testInit() {
    LongTaskTimer t = new DefaultLongTaskTimer(clock, NoopId.INSTANCE);
    Assert.assertEquals(t.duration(), 0L);
    Assert.assertEquals(t.activeTasks(), 0L);
  }


  @Test
  public void testStart() {
    LongTaskTimer t = new DefaultLongTaskTimer(clock, NoopId.INSTANCE);

    long task1 = t.start();
    long task2 = t.start();

    Assert.assertFalse(task1 == task2);
    Assert.assertEquals(t.activeTasks(), 2);
    Assert.assertEquals(t.duration(), 0L);
  }

  @Test
  public void testStop() {
    LongTaskTimer t = new DefaultLongTaskTimer(clock, NoopId.INSTANCE);

    long task1 = t.start();
    long task2 = t.start();

    Assert.assertEquals(t.activeTasks(), 2);
    clock.setMonotonicTime(5L);
    Assert.assertEquals(t.duration(), 10L);

    long elapsed1 = t.stop(task1);
    Assert.assertEquals(elapsed1, 5L);
    Assert.assertEquals(t.duration(task2), 5L);
    Assert.assertEquals(t.duration(task1), -1L); // task is gone, should return default
    Assert.assertEquals(t.duration(), 5L);
  }

  static void assertLongTaskTimer(Meter t, long timestamp, int activeTasks, double duration) {
    for (Measurement m : t.measure()) {
      Assert.assertEquals(m.timestamp(), timestamp);
      if (m.id().equals(t.id().withTag("statistic", "activeTasks"))) {
        Assert.assertEquals(m.value(), activeTasks, 1.0e-12);
      } else if (m.id().equals(t.id().withTag("statistic", "duration"))) {
        Assert.assertEquals(m.value(), duration, 1.0e-12);
      } else {
        Assert.fail("unexpected id: " + m.id());
      }
    }
  }

  @Test
  public void testMeasure() {
    LongTaskTimer t = new DefaultLongTaskTimer(clock, new DefaultId("foo"));
    long task1 = t.start();
    clock.setMonotonicTime(1_000_000_000L);
    clock.setWallTime(1L);
    assertLongTaskTimer(t, 1L, 1, 1.0);

    long task2 = t.start();
    assertLongTaskTimer(t, 1L, 2, 1.0);

    t.stop(task1);
    assertLongTaskTimer(t, 1L, 1, 0.0);

    t.stop(task2);
    assertLongTaskTimer(t, 1L, 0, 0.0);
  }
}
