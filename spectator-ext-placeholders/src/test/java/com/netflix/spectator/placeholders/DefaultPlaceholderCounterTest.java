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

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.ManualClock;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Utils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

/**
 * Unit tests for DefaultPlaceholderCounter class.
 *
 * Created on 10/8/15.
 */
public class DefaultPlaceholderCounterTest {
  private final ManualClock clock = new ManualClock();
  private final Registry registry = new DefaultRegistry(clock);
  private final PlaceholderFactory factory = PlaceholderFactory.from(registry);

  @Test
  public void testInit() {
    Counter c = new DefaultPlaceholderCounter(new DefaultPlaceholderId("unused", registry), registry);
    Assertions.assertEquals(c.count(), 0L);
  }

  @Test
  public void testIncrement() {
    String[] tagValue = new String[] { "default" };
    Counter c = factory.counter(factory.createId("testIncrement",
            Collections.singleton(new TestTagFactory(tagValue))));
    Assertions.assertEquals(0L, c.count());
    Assertions.assertEquals("testIncrement:tag=default", c.id().toString());
    c.increment();
    Assertions.assertEquals(1L, c.count());
    c.increment();
    c.increment();
    Assertions.assertEquals(3L, c.count());

    tagValue[0] = "value2";
    Assertions.assertEquals("testIncrement:tag=value2", c.id().toString());
    c.increment();
    Assertions.assertEquals(1L, c.count());

    tagValue[0] = "default";
    Assertions.assertEquals("testIncrement:tag=default", c.id().toString());
    c.increment();
    Assertions.assertEquals(4L, c.count());
  }

  @Test
  public void testIncrementAmount() {
    String[] tagValue = new String[] { "default" };
    Counter c = factory.counter(factory.createId("testIncrementAmount",
            Collections.singleton(new TestTagFactory(tagValue))));

    c.increment(42);
    Assertions.assertEquals(42L, c.count());

    tagValue[0] = "value2";
    c.increment(54);
    Assertions.assertEquals(54L, c.count());
  }

  @Test
  public void testMeasure() {
    String[] tagValue = new String[] { "default" };
    Counter c = factory.counter(factory.createId("testMeasure",
            Collections.singleton(new TestTagFactory(tagValue))));

    doMeasurementTest(c, 42, 3712345L);
    tagValue[0] = "value2";
    doMeasurementTest(c, 54, 3712346L);
  }

  private void doMeasurementTest(Counter c, int expectedValue, long expectedTime) {
    c.increment(expectedValue);
    clock.setWallTime(expectedTime);
    List<Measurement> measurements = Utils.toList(c.measure());

    Assertions.assertEquals(1, measurements.size());

    Measurement m = measurements.get(0);
    Assertions.assertEquals(c.id(), m.id());
    Assertions.assertEquals(expectedTime, m.timestamp());
    Assertions.assertEquals(expectedValue, m.value(), 0.1e-12);
  }

  @Test
  public void testHasExpired() {
    String[] tagValue = new String[] { "default" };
    Counter c = factory.counter(factory.createId("testHasExpired",
            Collections.singleton(new TestTagFactory(tagValue))));

    Assertions.assertFalse(c.hasExpired());
  }
}
