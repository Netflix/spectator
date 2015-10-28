/**
 * Copyright 2015 Netflix, Inc.
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

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Unit tests for the DefaultDynamicTimer class.
 */
@RunWith(JUnit4.class)
public class DefaultDynamicTimerTest {
  private final ManualClock clock = new ManualClock();
  private final Registry registry = new DefaultRegistry(clock);

  @Test
  public void testInit() {
    Timer timer = new DefaultDynamicTimer(new DefaultDynamicId("testInit"), registry);

    Assert.assertEquals(0L, timer.count());
    Assert.assertEquals(0L, timer.totalTime());
  }

  @Test
  public void testRecord() {
    String[] tagValue = new String[] { "default" };
    Timer timer = registry.timer(registry.createDynamicId("testRecord",
            Collections.singleton(new TestTagFactory(tagValue))));

    timer.record(42, TimeUnit.MILLISECONDS);
    Assert.assertEquals("testRecord:tag=default", timer.id().toString());
    Assert.assertEquals(timer.count(), 1L);
    Assert.assertEquals(42000000L, timer.totalTime());

    tagValue[0] = "value2";
    Assert.assertEquals("testRecord:tag=value2", timer.id().toString());
    Assert.assertEquals(0L, timer.count());
    Assert.assertEquals(0L, timer.totalTime());
  }

  @Test
  public void testRecordNegative() {
    Timer timer = registry.timer(registry.createDynamicId("testRecordNegative"));
    timer.record(-42, TimeUnit.MILLISECONDS);
    Assert.assertEquals(timer.count(), 0L);
    Assert.assertEquals(0L, timer.totalTime());
  }

  @Test
  public void testRecordZero() {
    Timer timer = registry.timer(registry.createDynamicId("testRecordZero"));
    timer.record(0, TimeUnit.MILLISECONDS);
    Assert.assertEquals(1L, timer.count(), 1L);
    Assert.assertEquals(0L, timer.totalTime());
  }

  @Test
  public void testRecordCallable() throws Exception {
    int expected = 42;
    Timer timer = registry.timer(registry.createDynamicId("testRecordCallable"));
    clock.setMonotonicTime(100L);
    int actual = timer.record(() -> {
      clock.setMonotonicTime(500L);
      return expected;
    });
    Assert.assertEquals(expected, actual);
    Assert.assertEquals(1L, timer.count());
    Assert.assertEquals(400L, timer.totalTime());
  }

  @Test
  public void testRecordCallableException() throws Exception {
    Timer timer = registry.timer(registry.createDynamicId("testRecordCallableException"));
    clock.setMonotonicTime(100L);
    boolean seen = false;
    try {
      timer.record(() -> {
        clock.setMonotonicTime(500L);
        throw new Exception("foo");
      });
    } catch (Exception e) {
      seen = true;
    }
    Assert.assertTrue(seen);
    Assert.assertEquals(1L, timer.count());
    Assert.assertEquals(400L, timer.totalTime());
  }

  @Test
  public void testRecordRunnable() throws Exception {
    Timer timer = registry.timer(registry.createDynamicId("testRecordRunnable"));
    clock.setMonotonicTime(100L);
    timer.record(() -> clock.setMonotonicTime(500L));
    Assert.assertEquals(1L, timer.count());
    Assert.assertEquals(timer.totalTime(), 400L);
  }

  @Test
  public void testRecordRunnableException() throws Exception {
    Timer timer = registry.timer(registry.createDynamicId("testRecordRunnableException"));
    clock.setMonotonicTime(100L);
    Exception expectedExc = new RuntimeException("foo");
    Exception actualExc = null;
    try {
      timer.record(() -> {
        clock.setMonotonicTime(500L);
        throw expectedExc;
      });
    } catch (Exception e) {
      actualExc = e;
    }
    Assert.assertSame(expectedExc, actualExc);
    Assert.assertEquals(1L, timer.count());
    Assert.assertEquals(timer.totalTime(), 400L);
  }

  @Test
  public void testMeasure() {
    Timer timer = registry.timer(registry.createDynamicId("testMeasure"));
    timer.record(42, TimeUnit.MILLISECONDS);
    clock.setWallTime(3712345L);
    for (Measurement m : timer.measure()) {
      Assert.assertEquals(m.timestamp(), 3712345L);
      if (m.id().equals(timer.id().withTag(Statistic.count))) {
        Assert.assertEquals(1.0, m.value(), 0.1e-12);
      } else if (m.id().equals(timer.id().withTag(Statistic.totalTime))) {
        Assert.assertEquals(42e6, m.value(), 0.1e-12);
      } else {
        Assert.fail("unexpected id: " + m.id());
      }
    }
  }
}
