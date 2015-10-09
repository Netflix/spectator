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
import java.util.List;

/**
 * Unit tests for DefaultDynamicCounter class.
 *
 * Created on 10/8/15.
 */
@RunWith(JUnit4.class)
public class DefaultDynamicCounterTest {
  private static class TestTagFactory implements TagFactory {
    private final String name;
    private final String[] valueHolder;

    TestTagFactory(String[] valueHolder) {
      this("tag", valueHolder);
    }

    TestTagFactory(String name, String[] valueHolder) {
      this.name = name;
      this.valueHolder = valueHolder;
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public Tag createTag() {
      return new BasicTag(name, valueHolder[0]);
    }
  }

  private final ManualClock clock = new ManualClock();
  private final Registry registry = new DefaultRegistry(clock);

  @Test
  public void testInit() {
    Counter c = new DefaultCounter(clock, NoopId.INSTANCE);
    Assert.assertEquals(c.count(), 0L);
  }

  @Test
  public void testIncrement() {
    String[] tagValue = new String[] { "default" };
    Counter c = registry.counter(registry.createDynamicId("testIncrement",
            Collections.singleton(new TestTagFactory(tagValue))));
    Assert.assertEquals(0L, c.count());
    Assert.assertEquals("testIncrement:tag=default", c.id().toString());
    c.increment();
    Assert.assertEquals(1L, c.count());
    c.increment();
    c.increment();
    Assert.assertEquals(3L, c.count());

    tagValue[0] = "value2";
    Assert.assertEquals("testIncrement:tag=value2", c.id().toString());
    c.increment();
    Assert.assertEquals(1L, c.count());

    tagValue[0] = "default";
    Assert.assertEquals("testIncrement:tag=default", c.id().toString());
    c.increment();
    Assert.assertEquals(4L, c.count());
  }

  @Test
  public void testIncrementAmount() {
    String[] tagValue = new String[] { "default" };
    Counter c = registry.counter(registry.createDynamicId("testIncrementAmount",
            Collections.singleton(new TestTagFactory(tagValue))));

    c.increment(42);
    Assert.assertEquals(42L, c.count());

    tagValue[0] = "value2";
    c.increment(54);
    Assert.assertEquals(54L, c.count());
  }

  @Test
  public void testMeasure() {
    String[] tagValue = new String[] { "default" };
    Counter c = registry.counter(registry.createDynamicId("testMeasure",
            Collections.singleton(new TestTagFactory(tagValue))));

    doMeasurementTest(c, 42, 3712345L);
    tagValue[0] = "value2";
    doMeasurementTest(c, 54, 3712346L);
  }

  private void doMeasurementTest(Counter c, int expectedValue, long expectedTime) {
    c.increment(expectedValue);
    clock.setWallTime(expectedTime);
    List<Measurement> measurements = Utils.toList(c.measure());

    Assert.assertEquals(1, measurements.size());

    Measurement m = measurements.get(0);
    Assert.assertEquals(c.id(), m.id());
    Assert.assertEquals(expectedTime, m.timestamp());
    Assert.assertEquals(expectedValue, m.value(), 0.1e-12);
  }

  @Test
  public void testHasExpired() {
    String[] tagValue = new String[] { "default" };
    Counter c = registry.counter(registry.createDynamicId("testHasExpired",
            Collections.singleton(new TestTagFactory(tagValue))));

    Assert.assertFalse(c.hasExpired());
  }
}
