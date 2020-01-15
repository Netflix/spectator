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
package com.netflix.spectator.api.patterns;

import com.netflix.spectator.api.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class LongTaskTimerTest {
  private final ManualClock clock = new ManualClock();
  private final Registry registry = new DefaultRegistry(clock);
  private final Id id = registry.createId("test");

  @Test
  public void testInit() {
    com.netflix.spectator.api.LongTaskTimer t = LongTaskTimer.get(registry, id);
    Assertions.assertEquals(t.duration(), 0L);
    Assertions.assertEquals(t.activeTasks(), 0L);
  }

  @Test
  public void testStart() {
    com.netflix.spectator.api.LongTaskTimer t = LongTaskTimer.get(registry, id);

    long task1 = t.start();
    long task2 = t.start();

    Assertions.assertNotEquals(task1, task2);
    Assertions.assertEquals(t.activeTasks(), 2);
    Assertions.assertEquals(t.duration(), 0L);
  }

  @Test
  public void testStop() {
    com.netflix.spectator.api.LongTaskTimer t = LongTaskTimer.get(registry, id);

    long task1 = t.start();
    long task2 = t.start();

    Assertions.assertEquals(t.activeTasks(), 2);
    clock.setMonotonicTime(5L);
    Assertions.assertEquals(t.duration(), 10L);

    long elapsed1 = t.stop(task1);

    Assertions.assertEquals(-1L, t.stop(task1));  // second call to stop should return an error
    Assertions.assertEquals(elapsed1, 5L);
    Assertions.assertEquals(t.duration(task2), 5L);
    Assertions.assertEquals(t.duration(task1), -1L); // task is gone, should return default
    Assertions.assertEquals(t.duration(), 5L);
  }

  @Test
  public void stateIsPreservedAcrossGets() {
    long t1 = LongTaskTimer.get(registry, id).start();
    long t2 = LongTaskTimer.get(registry, id).start();
    Assertions.assertNotEquals(t1, t2);

    Assertions.assertEquals(LongTaskTimer.get(registry, id).activeTasks(), 2);
    clock.setMonotonicTime(5L);
    Assertions.assertEquals(LongTaskTimer.get(registry, id).duration(), 10L);
    LongTaskTimer.get(registry, id).stop(t1);
    Assertions.assertEquals(LongTaskTimer.get(registry, id).duration(), 5L);
  }

  private void assertLongTaskTimer(Meter t, long timestamp, int activeTasks, double duration) {
    for (Measurement m : t.measure()) {
      Assertions.assertEquals(m.timestamp(), timestamp);
      if (m.id().equals(t.id().withTag(Statistic.activeTasks))) {
        Assertions.assertEquals(m.value(), activeTasks, 1.0e-12);
      } else if (m.id().equals(t.id().withTag(Statistic.duration))) {
        Assertions.assertEquals(m.value(), duration, 1.0e-12);
      } else {
        Assertions.fail("unexpected id: " + m.id());
      }
    }
  }

  @Test
  public void testMeasure() {
    com.netflix.spectator.api.LongTaskTimer t = LongTaskTimer.get(registry, id);
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

  // https://github.com/Netflix/spectator/issues/503
  @Test
  public void usingNoopRegistry() {
    System.setProperty("spectator.api.propagateWarnings", "true");
    Registry noop = new NoopRegistry();
    LongTaskTimer.get(noop, noop.createId("task"));
  }
}
