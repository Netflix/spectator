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

import com.netflix.spectator.controllers.filter.PrototypeMeasurementFilterTest;

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Meter;
import com.netflix.spectator.api.Measurement;

import com.netflix.spectator.controllers.model.DataPoint;
import com.netflix.spectator.controllers.filter.MeasurementFilter;
import com.netflix.spectator.controllers.filter.TagMeasurementFilter;
import com.netflix.spectator.controllers.model.MetricValues;
import com.netflix.spectator.controllers.model.TagValue;
import com.netflix.spectator.controllers.model.TaggedDataPoints;

import java.util.Arrays;
import java.util.List;
import java.util.HashMap;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MetricsControllerTest {
    static class TestMeter implements Meter {
      private Id myId;
      private List<Measurement> myMeasurements;
      private boolean expired = false;

      public TestMeter(String name, Measurement[] measures) {
        this(name, Arrays.asList(measures));
      }
      public TestMeter(String name, List<Measurement> measures) {
        myId = new PrototypeMeasurementFilterTest.TestId(name);
        myMeasurements = measures;
      }
      public Id id() { return myId; }
      public Iterable<Measurement> measure() {
        return myMeasurements;
      }
      public boolean hasExpired() {
        return expired;
      }
    };

  private long millis = 12345L;
  private Clock clock = new Clock() {
    public long  wallTime() { return millis; }
    public long monotonicTime() { return millis; }
  };

  HashMap<Id, List<Measurement>> collection;

  @Before
  public void setup() {
    collection = new HashMap<Id, List<Measurement>>();
  }
    
  MetricsController controller = new MetricsController();
  Id idA = new PrototypeMeasurementFilterTest.TestId("idA");
  Id idB = new PrototypeMeasurementFilterTest.TestId("idB");
  Id idAXY = idA.withTag("tagA", "X").withTag("tagB", "Y");
  Id idAYX = idA.withTag("tagA", "Y").withTag("tagB", "X");
  Id idAXZ = idA.withTag("tagA", "X").withTag("tagZ", "Z");
  Id idBXY = idB.withTag("tagA", "X").withTag("tagB", "Y");

  Measurement measureAXY = new Measurement(idAXY, 11, 11.11);
  Measurement measureAXY2 = new Measurement(idAXY, 20, 20.20);
  Measurement measureAYX = new Measurement(idAYX, 12, 12.12);
  Measurement measureAXZ = new Measurement(idAXZ, 13, 13.13);
  Measurement measureBXY = new Measurement(idBXY, 50, 50.50);
  Measurement measureBXY2 = new Measurement(idBXY, 5, 5.5);

  MeasurementFilter allowAll = new MeasurementFilter() {
    public boolean keep(Meter meter, Measurement measurement) {
      return true;
    }
  };

  Meter meterA = new TestMeter("ignoreA", Arrays.asList(measureAXY, measureAYX, measureAXZ));
  Meter meterA2 = new TestMeter("ignoreA", Arrays.asList(measureAXY2));
  Meter meterB = new TestMeter("ignoreB", Arrays.asList(measureBXY));
  Meter meterB2 = new TestMeter("ignoreB", Arrays.asList(measureBXY2));

  private static List<Measurement> toMeasurementList(Measurement[] array) {
    return Arrays.asList(array);
  }

  @Test
  public void collectDisjointValues() {
    HashMap<Id, List<Measurement>> expect = new HashMap<Id, List<Measurement>>();
    expect.put(idAXY, Arrays.asList(measureAXY));
    expect.put(idAYX, Arrays.asList(measureAYX));
    expect.put(idAXZ, Arrays.asList(measureAXZ));

    MetricsController.collectValues(collection, meterA, allowAll);

    Assert.assertEquals(collection, expect);
  }

  @Test
  public void collectRepeatedValues() {
    MetricsController.collectValues(collection, meterA, allowAll);
    MetricsController.collectValues(collection, meterA2, allowAll);

    HashMap<Id, List<Measurement>> expect = new HashMap<Id, List<Measurement>>();
    expect.put(idAXY, Arrays.asList(measureAXY, measureAXY2));
    expect.put(idAYX, Arrays.asList(measureAYX));
    expect.put(idAXZ, Arrays.asList(measureAXZ));

    Assert.assertEquals(collection, expect);
  }

  @Test
  public void collectSimilarMetrics() {
    MetricsController.collectValues(collection, meterA, allowAll);
    MetricsController.collectValues(collection, meterB, allowAll);

    HashMap<Id, List<Measurement>> expect = new HashMap<Id, List<Measurement>>();
    expect.put(idAXY, Arrays.asList(measureAXY));
    expect.put(idBXY, Arrays.asList(measureBXY));
    expect.put(idAYX, Arrays.asList(measureAYX));
    expect.put(idAXZ, Arrays.asList(measureAXZ));

    Assert.assertEquals(collection, expect);
  }

  @Test
  public void collectFilteredName() {
    MeasurementFilter filter = new TagMeasurementFilter("idA", null, null);
    MetricsController.collectValues(collection, meterA, filter);
    MetricsController.collectValues(collection, meterB, filter);

    HashMap<Id, List<Measurement>> expect = new HashMap<Id, List<Measurement>>();
    expect.put(idAXY, Arrays.asList(measureAXY));
    expect.put(idAYX, Arrays.asList(measureAYX));
    expect.put(idAXZ, Arrays.asList(measureAXZ));

    Assert.assertEquals(collection, expect);
  }

  @Test
  public void collectFilteredTagName() {
    MeasurementFilter filter = new TagMeasurementFilter(null, "tagZ", null);
    MetricsController.collectValues(collection, meterA, filter);

    HashMap<Id, List<Measurement>> expect = new HashMap<Id, List<Measurement>>();
    expect.put(idAXZ, Arrays.asList(measureAXZ));

    Assert.assertEquals(collection, expect);
  }

  @Test
  public void collectFilteredTagValue() {
    MeasurementFilter filter = new TagMeasurementFilter(null, null, "X");
    MetricsController.collectValues(collection, meterA, filter);

    HashMap<Id, List<Measurement>> expect = new HashMap<Id, List<Measurement>>();
    expect.put(idAXY, Arrays.asList(measureAXY));
    expect.put(idAYX, Arrays.asList(measureAYX));
    expect.put(idAXZ, Arrays.asList(measureAXZ));

    Assert.assertEquals(collection, expect);
  }

  @Test
  public void collectNotFound() {
    MeasurementFilter filter = new TagMeasurementFilter(null, "tagZ", "X");
    MetricsController.collectValues(collection, meterA, filter);

    Assert.assertEquals(collection, new HashMap<Id, List<Measurement>>());
  }

  @Test
  public void meterToKind() {
    DefaultRegistry r = new DefaultRegistry(clock);

    Assert.assertEquals(
        "Counter", MetricsController.meterToKind(r.counter(idAXY)));
    Assert.assertEquals(
        "Timer", MetricsController.meterToKind(r.timer(idBXY)));
    Assert.assertEquals(
        "TestMeter", MetricsController.meterToKind(meterA));
  }

  @Test
  public void encodeSimpleRegistry() {
    DefaultRegistry registry = new DefaultRegistry(clock);
    Counter counterA = registry.counter(idAXY);
    Counter counterB = registry.counter(idBXY);
    counterA.increment(4);
    counterB.increment(10);

    List<TaggedDataPoints> expectedTaggedDataPointsA
      = Arrays.asList(
          new TaggedDataPoints(
              Arrays.asList(new TagValue("tagA", "X"),
                            new TagValue("tagB", "Y")),
              Arrays.asList(new DataPoint(millis, 4))));

    List<TaggedDataPoints> expectedTaggedDataPointsB
      = Arrays.asList(
          new TaggedDataPoints(
              Arrays.asList(new TagValue("tagA", "X"),
                            new TagValue("tagB", "Y")),
              Arrays.asList(new DataPoint(millis, 10))));

    HashMap<String, MetricValues> expect = new HashMap<String, MetricValues>();
    expect.put("idA", new MetricValues("Counter", expectedTaggedDataPointsA));
    expect.put("idB", new MetricValues("Counter", expectedTaggedDataPointsB));
    Assert.assertEquals(expect, controller.encodeRegistry(registry, allowAll));
  }

  @Test
  public void encodeCombinedRegistry() {
    DefaultRegistry registry = new DefaultRegistry(clock);
    registry.register(meterB);
    registry.register(meterB2);

    List<TaggedDataPoints> expectedTaggedDataPoints = Arrays.asList(
       new TaggedDataPoints(
             Arrays.asList(new TagValue("tagA", "X"),
                           new TagValue("tagB", "Y")),
             Arrays.asList(new DataPoint(50, 50.5 + 5.5))));

    HashMap<String, MetricValues> expect = new HashMap<String, MetricValues>();
    expect.put("idB", new MetricValues("Counter", expectedTaggedDataPoints));

    Assert.assertEquals(expect, controller.encodeRegistry(registry, allowAll));
  }

  @Test
  public void encodeCompositeRegistry() {
    DefaultRegistry registry = new DefaultRegistry(clock);
    registry.register(meterA);
    registry.register(meterA2);

    List<TaggedDataPoints> expected_tagged_data_points
        = Arrays.asList(
               new TaggedDataPoints(Arrays.asList(new TagValue("tagA", "Y"),
                                                  new TagValue("tagB", "X")),
                                    Arrays.asList(new DataPoint(12, 12.12))),
               new TaggedDataPoints(Arrays.asList(new TagValue("tagA", "X"),
                                                  new TagValue("tagB", "Y")),
                                    // This should be 20, but AggrMeter keeps first time,
                                    // which happens to be the 11th, not the most recent time.
                                    Arrays.asList(new DataPoint(11, 11.11 + 20.20))),
               new TaggedDataPoints(Arrays.asList(new TagValue("tagA", "X"),
                                                  new TagValue("tagZ", "Z")),
                                    Arrays.asList(new DataPoint(13, 13.13))));

    HashMap<String, MetricValues> expect = new HashMap<String, MetricValues>();
    expect.put("idA", new MetricValues("Counter", expected_tagged_data_points));

    Assert.assertEquals(expect,
                        controller.encodeRegistry(registry, allowAll));
  }
};

