/*
 * Copyright 2014-2017 Netflix, Inc.
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
import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.ManualClock;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Utils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Collections;
import java.util.List;

@RunWith(JUnit4.class)
public class DefaultPlaceholderGaugeTest {
  private final ManualClock clock = new ManualClock();
  private final Registry registry = new DefaultRegistry(clock);
  private final PlaceholderFactory factory = PlaceholderFactory.from(registry);

  @Test
  public void testInit() {
    Gauge g = new DefaultPlaceholderGauge(new DefaultPlaceholderId("unused", registry), registry);
    Assert.assertEquals(g.value(), Double.NaN, 1e-12);
  }

  @Test
  public void testIncrement() {
    String[] tagValue = new String[] { "default" };
    Gauge g = factory.gauge(factory.createId("testIncrement",
        Collections.singleton(new TestTagFactory(tagValue))));
    Assert.assertEquals(Double.NaN, g.value(), 1e-12);
    Assert.assertEquals("testIncrement:tag=default", g.id().toString());
    g.set(1);
    Assert.assertEquals(1.0, g.value(), 1e-12);
    g.set(3);
    Assert.assertEquals(3.0, g.value(), 1e-12);

    tagValue[0] = "value2";
    Assert.assertEquals("testIncrement:tag=value2", g.id().toString());
    g.set(1);
    Assert.assertEquals(1.0, g.value(), 1e-12);

    tagValue[0] = "default";
    Assert.assertEquals("testIncrement:tag=default", g.id().toString());
    g.set(4);
    Assert.assertEquals(4.0, g.value(), 1e-12);
  }

  @Test
  public void testIncrementAmount() {
    String[] tagValue = new String[] { "default" };
    Gauge g = factory.gauge(factory.createId("testIncrementAmount",
        Collections.singleton(new TestTagFactory(tagValue))));

    g.set(42);
    Assert.assertEquals(42.0, g.value(), 1e-12);

    tagValue[0] = "value2";
    g.set(54);
    Assert.assertEquals(54.0, g.value(), 1e-12);
  }

  @Test
  public void testMeasure() {
    String[] tagValue = new String[] { "default" };
    Gauge g = factory.gauge(factory.createId("testMeasure",
        Collections.singleton(new TestTagFactory(tagValue))));

    doMeasurementTest(g, 42, 3712345L);
    tagValue[0] = "value2";
    doMeasurementTest(g, 54, 3712346L);
  }

  private void doMeasurementTest(Gauge g, int expectedValue, long expectedTime) {
    g.set(expectedValue);
    clock.setWallTime(expectedTime);
    List<Measurement> measurements = Utils.toList(g.measure());

    Assert.assertEquals(1, measurements.size());

    Measurement m = measurements.get(0);
    Assert.assertEquals(g.id(), m.id());
    Assert.assertEquals(expectedTime, m.timestamp());
    Assert.assertEquals(expectedValue, m.value(), 0.1e-12);
  }

  @Test
  public void testHasExpired() {
    String[] tagValue = new String[] { "default" };
    Gauge g = factory.gauge(factory.createId("testHasExpired",
        Collections.singleton(new TestTagFactory(tagValue))));

    Assert.assertFalse(g.hasExpired());
  }
}
