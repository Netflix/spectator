/**
 * Copyright 2017 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spectator.api.patterns;

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.ManualClock;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Statistic;
import com.netflix.spectator.api.Utils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;

@RunWith(JUnit4.class)
public class IntervalCounterTest {

  private final ManualClock clock = new ManualClock();
  private static final double EPSILON = 1e-12;

  @Before
  public void before() {
    clock.setWallTime(0L);
    clock.setMonotonicTime(0L);
  }

  @Test
  public void testInit() {
    Registry r = new DefaultRegistry(clock);
    clock.setWallTime(42 * 1000L);
    Id id = r.createId("test");
    IntervalCounter c = IntervalCounter.get(r, id);
    Assert.assertEquals(0L, c.count());
    Assert.assertEquals(42.0, c.secondsSinceLastUpdate(), EPSILON);
  }

  @Test
  public void testInterval() {
    Registry r = new DefaultRegistry(clock);
    Id id = r.createId("test");
    IntervalCounter c = IntervalCounter.get(r, id);
    Assert.assertEquals(c.secondsSinceLastUpdate(), 0.0, EPSILON);
    clock.setWallTime(1000);
    Assert.assertEquals(c.secondsSinceLastUpdate(), 1.0, EPSILON);
    c.increment();
    Assert.assertEquals(c.secondsSinceLastUpdate(), 0.0, EPSILON);
  }

  @Test
  public void testIncrement() {
    Registry r = new DefaultRegistry(clock);
    Id id = r.createId("test");
    Counter c = IntervalCounter.get(r, id);
    Assert.assertEquals(0, c.count());
    c.increment();
    Assert.assertEquals(1, c.count());
    c.increment(41);
    Assert.assertEquals(42, c.count());
  }

  private static List<Measurement> getAllMeasurements(Registry registry) {
    PolledMeter.update(registry);
    final List<Measurement> result = new ArrayList<>();
    registry.stream()
        .filter(meter -> !meter.hasExpired())
        .forEach(meter -> meter.measure().forEach(result::add));
    return result;
  }

  @Test
  public void testMeasure() {
    Registry r = new DefaultRegistry(clock);
    clock.setWallTime(61000L);
    Id id = r.createId("test");
    Counter c = IntervalCounter.get(r, id);

    // all meters should have the correct timestamp
    r.stream().forEach(meter -> {
      for (Measurement m : meter.measure()) {
        Assert.assertEquals(m.timestamp(), 61000L);
      }
    });

    final List<Measurement> measurements = getAllMeasurements(r);
    final double initAge = Utils.first(measurements, Statistic.duration).value();
    final double initCount = Utils.first(measurements, Statistic.count).value();
    Assert.assertEquals(61.0, initAge, EPSILON);
    Assert.assertEquals(0.0, initCount, EPSILON);

    clock.setWallTime(120000L);
    c.increment();
    final List<Measurement> afterMeasurements = getAllMeasurements(r);
    final double afterAge = Utils.first(afterMeasurements, Statistic.duration).value();
    final double afterCount = Utils.first(afterMeasurements, Statistic.count).value();
    Assert.assertEquals(0.0, afterAge, EPSILON);
    Assert.assertEquals(1.0, afterCount, EPSILON);
  }

  @Test
  public void testReusesInstance() {
    Registry r = new DefaultRegistry(clock);
    Id id = r.createId("test");
    Counter c1 = IntervalCounter.get(r, id);
    Counter c2 = IntervalCounter.get(r, id);
    Assert.assertSame(c1, c2);
  }
}
