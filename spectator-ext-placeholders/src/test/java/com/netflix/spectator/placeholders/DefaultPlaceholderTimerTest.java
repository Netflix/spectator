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
package com.netflix.spectator.placeholders;

import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.ManualClock;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Statistic;
import com.netflix.spectator.api.Timer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Unit tests for the DefaultPlaceholderTimer class.
 */
public class DefaultPlaceholderTimerTest {
  private final ManualClock clock = new ManualClock();
  private final Registry registry = new DefaultRegistry(clock);
  private final PlaceholderFactory factory = PlaceholderFactory.from(registry);

  @Test
  public void testInit() {
    Timer timer = new DefaultPlaceholderTimer(new DefaultPlaceholderId("testInit", registry), registry);

    Assertions.assertEquals(0L, timer.count());
    Assertions.assertEquals(0L, timer.totalTime());
  }

  @Test
  public void testRecord() {
    String[] tagValue = new String[] { "default" };
    Timer timer = factory.timer(factory.createId("testRecord",
            Collections.singleton(new TestTagFactory(tagValue))));

    timer.record(42, TimeUnit.MILLISECONDS);
    Assertions.assertEquals("testRecord:tag=default", timer.id().toString());
    Assertions.assertEquals(timer.count(), 1L);
    Assertions.assertEquals(42000000L, timer.totalTime());

    tagValue[0] = "value2";
    Assertions.assertEquals("testRecord:tag=value2", timer.id().toString());
    Assertions.assertEquals(0L, timer.count());
    Assertions.assertEquals(0L, timer.totalTime());
  }

  @Test
  public void testRecordNegative() {
    Timer timer = factory.timer(factory.createId("testRecordNegative"));
    timer.record(-42, TimeUnit.MILLISECONDS);
    Assertions.assertEquals(timer.count(), 0L);
    Assertions.assertEquals(0L, timer.totalTime());
  }

  @Test
  public void testRecordZero() {
    Timer timer = factory.timer(factory.createId("testRecordZero"));
    timer.record(0, TimeUnit.MILLISECONDS);
    Assertions.assertEquals(1L, timer.count(), 1L);
    Assertions.assertEquals(0L, timer.totalTime());
  }

  @Test
  public void testRecordCallable() throws Exception {
    int expected = 42;
    Timer timer = factory.timer(factory.createId("testRecordCallable"));
    clock.setMonotonicTime(100L);
    int actual = timer.recordCallable(() -> {
      clock.setMonotonicTime(500L);
      return expected;
    });
    Assertions.assertEquals(expected, actual);
    Assertions.assertEquals(1L, timer.count());
    Assertions.assertEquals(400L, timer.totalTime());
  }

  @Test
  public void testRecordCallableException() throws Exception {
    Timer timer = factory.timer(factory.createId("testRecordCallableException"));
    clock.setMonotonicTime(100L);
    boolean seen = false;
    try {
      timer.recordCallable(() -> {
        clock.setMonotonicTime(500L);
        throw new Exception("foo");
      });
    } catch (Exception e) {
      seen = true;
    }
    Assertions.assertTrue(seen);
    Assertions.assertEquals(1L, timer.count());
    Assertions.assertEquals(400L, timer.totalTime());
  }

  @Test
  public void testRecordRunnable() throws Exception {
    Timer timer = factory.timer(factory.createId("testRecordRunnable"));
    clock.setMonotonicTime(100L);
    timer.recordRunnable(() -> clock.setMonotonicTime(500L));
    Assertions.assertEquals(1L, timer.count());
    Assertions.assertEquals(timer.totalTime(), 400L);
  }

  @Test
  public void testRecordRunnableException() throws Exception {
    Timer timer = factory.timer(factory.createId("testRecordRunnableException"));
    clock.setMonotonicTime(100L);
    RuntimeException expectedExc = new RuntimeException("foo");
    Exception actualExc = null;
    try {
      timer.recordRunnable(() -> {
        clock.setMonotonicTime(500L);
        throw expectedExc;
      });
    } catch (Exception e) {
      actualExc = e;
    }
    Assertions.assertSame(expectedExc, actualExc);
    Assertions.assertEquals(1L, timer.count());
    Assertions.assertEquals(timer.totalTime(), 400L);
  }

  @Test
  public void testMeasure() {
    Timer timer = factory.timer(factory.createId("testMeasure"));
    timer.record(42, TimeUnit.MILLISECONDS);
    clock.setWallTime(3712345L);
    for (Measurement m : timer.measure()) {
      Assertions.assertEquals(m.timestamp(), 3712345L);
      if (m.id().equals(timer.id().withTag(Statistic.count))) {
        Assertions.assertEquals(1.0, m.value(), 0.1e-12);
      } else if (m.id().equals(timer.id().withTag(Statistic.totalTime))) {
        Assertions.assertEquals(42e6, m.value(), 0.1e-12);
      } else {
        Assertions.fail("unexpected id: " + m.id());
      }
    }
  }
}
