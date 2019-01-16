/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spectator.controllers;

import com.netflix.spectator.api.ManualClock;
import com.netflix.spectator.api.patterns.PolledMeter;
import com.netflix.spectator.controllers.model.TestId;

import com.netflix.spectator.api.BasicTag;
import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Meter;
import com.netflix.spectator.api.Measurement;

import com.netflix.spectator.controllers.model.DataPoint;
import com.netflix.spectator.controllers.model.MetricValues;
import com.netflix.spectator.controllers.model.TaggedDataPoints;
import com.netflix.spectator.controllers.model.TestMeter;

import java.util.function.Predicate;
import java.util.Arrays;
import java.util.List;
import java.util.HashMap;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MetricsControllerTest {
  private Clock clock = new ManualClock(12345L, 0L);

  MetricsController controller = new MetricsController();
  Id idA = new TestId("idA");
  Id idB = new TestId("idB");
  Id idAXY = idA.withTag("tagA", "X").withTag("tagB", "Y");
  Id idAYX = idA.withTag("tagA", "Y").withTag("tagB", "X");
  Id idAXZ = idA.withTag("tagA", "X").withTag("tagZ", "Z");
  Id idBXY = idB.withTag("tagA", "X").withTag("tagB", "Y");

  Measurement measureAXY = new Measurement(idAXY, 11, 11.11);
  Measurement measureAYX = new Measurement(idAYX, 12, 12.12);
  Measurement measureAXZ = new Measurement(idAXZ, 13, 13.13);
  Measurement measureBXY = new Measurement(idBXY, 50, 50.50);

  static final Predicate<Measurement> allowAll
      = MetricsController.ALL_MEASUREMENTS_FILTER;

  Meter meterA = new TestMeter("ignoreA", Arrays.asList(measureAXY, measureAYX, measureAXZ));
  Meter meterB = new TestMeter("ignoreB", measureBXY);

  @Test
  public void testMeterToKind() {
    DefaultRegistry r = new DefaultRegistry(clock);

    Assertions.assertEquals(
        "Counter", MetricsController.meterToKind(r, r.counter(idAXY)));
    Assertions.assertEquals(
        "Timer", MetricsController.meterToKind(r, r.timer(idBXY)));
    Assertions.assertEquals(
        "TestMeter", MetricsController.meterToKind(r, meterA));
  }

  @Test
  public void testEncodeSimpleRegistry() {
    DefaultRegistry registry = new DefaultRegistry(clock);
    Counter counterA = registry.counter(idAXY);
    Counter counterB = registry.counter(idBXY);
    counterA.increment(4);
    counterB.increment(10);

    List<TaggedDataPoints> expectedTaggedDataPointsA
      = Arrays.asList(
          new TaggedDataPoints(
              Arrays.asList(new BasicTag("tagA", "X"),
                            new BasicTag("tagB", "Y")),
              Arrays.asList(new DataPoint(clock.wallTime(), 4))));

    List<TaggedDataPoints> expectedTaggedDataPointsB
      = Arrays.asList(
          new TaggedDataPoints(
              Arrays.asList(new BasicTag("tagA", "X"),
                            new BasicTag("tagB", "Y")),
              Arrays.asList(new DataPoint(clock.wallTime(), 10))));

    HashMap<String, MetricValues> expect = new HashMap<String, MetricValues>();
    expect.put("idA", new MetricValues("Counter", expectedTaggedDataPointsA));
    expect.put("idB", new MetricValues("Counter", expectedTaggedDataPointsB));
    Assertions.assertEquals(expect, controller.encodeRegistry(registry, allowAll));
  }

  @Test
  public void testEncodeCombinedRegistry() {
    // Multiple occurrences of measurements in the same registry
    // (confirm these are handled within the registry itself).
    Measurement measureBXY2 = new Measurement(idBXY, 5, 5.5);
    Meter meterB2 = new TestMeter("ignoreB", measureBXY2);

    DefaultRegistry registry = new DefaultRegistry(clock);
    registry.register(meterB);
    registry.register(meterB2);

    List<TaggedDataPoints> expectedTaggedDataPoints = Arrays.asList(
       new TaggedDataPoints(
             Arrays.asList(new BasicTag("tagA", "X"),
                           new BasicTag("tagB", "Y")),
             Arrays.asList(new DataPoint(clock.wallTime(), 50.5 + 5.5))));

    HashMap<String, MetricValues> expect = new HashMap<>();
    expect.put("idB", new MetricValues("Counter", expectedTaggedDataPoints));

    PolledMeter.update(registry);
    Assertions.assertEquals(expect, controller.encodeRegistry(registry, allowAll));
  }

  @Test
  public void testIgnoreNan() {
    Id id = idB.withTag("tagA", "Z");
    Measurement measure = new Measurement(id, 100, Double.NaN);
    Meter meter = new TestMeter("ignoreZ", measure);

    DefaultRegistry registry = new DefaultRegistry(clock);
    registry.register(meter);

    HashMap<String, MetricValues> expect = new HashMap<>();
    PolledMeter.update(registry);
    Assertions.assertEquals(expect, controller.encodeRegistry(registry, allowAll));
  }

  @Test
  public void testEncodeCompositeRegistry() {
    // Multiple occurrences of measurements in the same registry
    // (confirm these are handled within the registry itself).
    // Here measurements are duplicated but meters have different sets.
    Measurement measureAXY2 = new Measurement(idAXY, 20, 20.20);
    Meter meterA2 = new TestMeter("ignoreA", measureAXY2);

    DefaultRegistry registry = new DefaultRegistry(clock);
    registry.register(meterA);
    registry.register(meterA2);

    List<TaggedDataPoints> expected_tagged_data_points
        = Arrays.asList(
               new TaggedDataPoints(Arrays.asList(new BasicTag("tagA", "Y"),
                                                  new BasicTag("tagB", "X")),
                                    Arrays.asList(new DataPoint(clock.wallTime(), 12.12))),
               new TaggedDataPoints(Arrays.asList(new BasicTag("tagA", "X"),
                                                  new BasicTag("tagB", "Y")),
                                    // This should be 20, but AggrMeter keeps first time,
                                    // which happens to be the 11th, not the most recent time.
                                    Arrays.asList(new DataPoint(clock.wallTime(), 11.11 + 20.20))),
               new TaggedDataPoints(Arrays.asList(new BasicTag("tagA", "X"),
                                                  new BasicTag("tagZ", "Z")),
                                    Arrays.asList(new DataPoint(clock.wallTime(), 13.13))));

    HashMap<String, MetricValues> expect = new HashMap<String, MetricValues>();
    expect.put("idA", new MetricValues("Counter", expected_tagged_data_points));

    PolledMeter.update(registry);
    Assertions.assertEquals(expect,
                        controller.encodeRegistry(registry, allowAll));
  }
};

