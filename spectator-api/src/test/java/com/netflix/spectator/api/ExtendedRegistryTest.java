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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.LinkedHashMap;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@RunWith(JUnit4.class)
public class ExtendedRegistryTest {

  @Before
  public void init() {
    System.setProperty("spectator.api.propagateWarnings", "true");
    System.setProperty("spectator.api.maxNumberOfMeters", "10000");
  }

  @Test
  public void testCreateIdArray() {
    Registry r = new DefaultRegistry();
    Id id1 = r.createId("foo", "bar", "baz", "k", "v");
    Id id2 = r.createId("foo", new TagList("k", "v").mergeTag(new TagList("bar", "baz")));
    Assert.assertEquals(id1, id2);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCreateIdArrayOdd() {
    Registry r = new DefaultRegistry();
    r.createId("foo", "bar", "baz", "k");
  }

  @Test
  public void testCreateIdMap() {
    Map<String, String> map = new LinkedHashMap<>();
    map.put("k1", "v1");
    map.put("k2", "v2");
    Registry r = new DefaultRegistry();
    Id id1 = r.createId("foo", map);
    Id id2 = r.createId("foo", "k1", "v1", "k2", "v2");
    Assert.assertEquals(id1, id2);
  }

  @Test
  public void testCounterHelpers() {
    Registry r = new DefaultRegistry();
    Counter c1 = r.counter("foo", "bar", "baz", "k", "v");
    Counter c2 = r.counter("foo", new TagList("k", "v").mergeTag(new TagList("bar", "baz")));
    Counter c3 = r.counter("foo");
    Assert.assertSame(c1, c2);
    Assert.assertNotSame(c1, c3);
  }

  @Test
  public void testDistributionSummaryHelpers() {
    Registry r = new DefaultRegistry();
    DistributionSummary c1 = r.distributionSummary("foo", "bar", "baz", "k", "v");
    DistributionSummary c2 = r.distributionSummary("foo",
        new TagList("k", "v").mergeTag(new TagList("bar", "baz")));
    DistributionSummary c3 = r.distributionSummary("foo");
    Assert.assertSame(c1, c2);
    Assert.assertNotSame(c1, c3);
  }

  @Test
  public void testTimerHelpers() {
    Registry r = new DefaultRegistry();
    Timer c1 = r.timer("foo", "bar", "baz", "k", "v");
    Timer c2 = r.timer("foo", new TagList("k", "v").mergeTag(new TagList("bar", "baz")));
    Timer c3 = r.timer("foo");
    Assert.assertSame(c1, c2);
    Assert.assertNotSame(c1, c3);
  }

  @Test
  public void testLongTaskTimerHelpers() {
    ManualClock clock = new ManualClock();
    Registry r = new DefaultRegistry(clock);
    LongTaskTimer c1 = r.longTaskTimer("foo", "bar", "baz", "k", "v");
    Meter m1 = r.get(c1.id());
    Assert.assertEquals(c1.id(), m1.id()); // registration

    LongTaskTimer c2 = r.longTaskTimer("foo", new TagList("k", "v").mergeTag(new TagList("bar", "baz")));
    Assert.assertEquals(c1.id(), c2.id());

    long t1 = c1.start();
    long t2 = c2.start();
    clock.setMonotonicTime(1000L);
    clock.setWallTime(1L);
    DefaultLongTaskTimerTest.assertLongTaskTimer(r.get(c1.id()), 1L, 2, 2.0e-6);

    c1.stop(t1);
    DefaultLongTaskTimerTest.assertLongTaskTimer(r.get(c1.id()), 1L, 1, 1.0e-6);

    c2.stop(t2);
    DefaultLongTaskTimerTest.assertLongTaskTimer(r.get(c1.id()), 1L, 0, 0L);
  }

  @Test
  public void testGaugeHelpers() {
    AtomicLong al1 = new AtomicLong(1L);
    AtomicLong al2 = new AtomicLong(2L);
    AtomicLong al4 = new AtomicLong(4L);
    Registry r = new DefaultRegistry();
    AtomicLong v1 = r.gauge(r.createId("foo", "bar", "baz", "k", "v"), al1);
    AtomicLong v2 = r.gauge("foo", new TagList("k", "v").mergeTag(new TagList("bar", "baz")), al2);
    AtomicLong v3 = r.gauge("foo", al4);
    Assert.assertSame(v1, al1);
    Assert.assertSame(v2, al2);
    Assert.assertSame(v3, al4);
    Id id1 = r.createId("foo", "bar", "baz", "k", "v");
    Id id2 = r.createId("foo");
    Assert.assertEquals(r.get(id1).measure().iterator().next().value(), 3.0, 1e-12);
    Assert.assertEquals(r.get(id2).measure().iterator().next().value(), 4.0, 1e-12);
  }

  @Test
  public void testGaugeHelpersWithFunction() {
    AtomicLong al1 = new AtomicLong(1L);
    Registry r = new DefaultRegistry(new ManualClock(40, 0));
    DoubleFunction f = Functions.age(r.clock());
    AtomicLong v1 = r.gauge("foo", al1, f);
    Assert.assertSame(v1, al1);
    Id id1 = r.createId("foo");
    Assert.assertEquals(r.get(id1).measure().iterator().next().value(), 39.0 / 1000.0, 1e-12);
  }

  @Test
  public void testGaugeHelpersWithCustomFunction() {
    AtomicLong al1 = new AtomicLong(1L);
    Registry r = new DefaultRegistry(new ManualClock(40, 0));
    DoubleFunction f = new DoubleFunction() {
      @Override
      public double apply(double v) {
        return (r.clock().wallTime() - v) / 1000.0;
      }
    };
    AtomicLong v1 = r.gauge("foo", al1, f);
    Assert.assertSame(v1, al1);
    Id id1 = r.createId("foo");
    Assert.assertEquals(r.get(id1).measure().iterator().next().value(), 39.0 / 1000.0, 1e-12);
  }

  @Test
  public void testGaugeHelpersWithCustomFunction2() {
    AtomicLong al1 = new AtomicLong(1L);
    Registry r = new DefaultRegistry(new ManualClock(40, 0));
    ValueFunction f = new ValueFunction() {
      @Override
      public double apply(Object obj) {
        double v = ((Number) obj).doubleValue();
        return (r.clock().wallTime() - v) / 1000.0;
      }
    };
    AtomicLong v1 = r.gauge("foo", al1, f);
    Assert.assertSame(v1, al1);
    Id id1 = r.createId("foo");
    Assert.assertEquals(r.get(id1).measure().iterator().next().value(), 39.0 / 1000.0, 1e-12);
  }

  @Test
  public void testCollectionSizeHelpers() {
    Registry r = new DefaultRegistry();
    LinkedBlockingDeque<String> q1 = new LinkedBlockingDeque<>();
    LinkedBlockingDeque<String> q2 = r.collectionSize("queueSize", q1);
    Assert.assertSame(q1, q2);
    Id id = r.createId("queueSize");
    Assert.assertEquals(r.get(id).measure().iterator().next().value(), 0.0, 1e-12);
    q2.push("foo");
    Assert.assertEquals(r.get(id).measure().iterator().next().value(), 1.0, 1e-12);
  }

  @Test
  public void testMapSizeHelpers() {
    Registry r = new DefaultRegistry();
    ConcurrentHashMap<String, String> q1 = new ConcurrentHashMap<>();
    ConcurrentHashMap<String, String> q2 = r.mapSize("mapSize", q1);
    Assert.assertSame(q1, q2);
    Id id = r.createId("mapSize");
    Assert.assertEquals(r.get(id).measure().iterator().next().value(), 0.0, 1e-12);
    q2.put("foo", "bar");
    Assert.assertEquals(r.get(id).measure().iterator().next().value(), 1.0, 1e-12);
  }

  @Test
  public void testMethodValueHelpers() {
    Registry r = new DefaultRegistry();
    LinkedBlockingDeque<String> q1 = new LinkedBlockingDeque<>();
    r.methodValue("queueSize", q1, "size");
    Id id = r.createId("queueSize");
    Assert.assertEquals(r.get(id).measure().iterator().next().value(), 0.0, 1e-12);
    q1.push("foo");
    Assert.assertEquals(r.get(id).measure().iterator().next().value(), 1.0, 1e-12);
  }

  @Test(expected = ClassCastException.class)
  public void methodValueBadReturnType() {
    System.setProperty("spectator.api.propagateWarnings", "true");
    Registry r = new DefaultRegistry();
    r.methodValue("queueSize", this, "toString");
  }

  @Test
  public void methodValueBadReturnTypeNoPropagate() {
    System.setProperty("spectator.api.propagateWarnings", "false");
    Registry r = new DefaultRegistry();
    r.methodValue("queueSize", this, "toString");
    Assert.assertNull(r.get(r.createId("queueSize")));
  }

  @Test(expected = RuntimeException.class)
  public void methodValueUnknown() {
    System.setProperty("spectator.api.propagateWarnings", "true");
    Registry r = new DefaultRegistry();
    r.methodValue("queueSize", this, "unknownMethod");
  }

  @Test
  public void methodValueUnknownNoPropagate() {
    System.setProperty("spectator.api.propagateWarnings", "false");
    Registry r = new DefaultRegistry();
    r.methodValue("queueSize", this, "unknownMethod");
    Assert.assertNull(r.get(r.createId("queueSize")));
  }

  @Test
  public void gaugeUsingLambda() {
    Registry r = new DefaultRegistry();
    GaugeUsingLambda g = new GaugeUsingLambda(r);
    for (Measurement m : r.get(r.createId("test")).measure()) {
      Assert.assertEquals(84.0, m.value(), 1e-12);
    }
  }

  public static class GaugeUsingLambda {

    public GaugeUsingLambda(Registry r) {
      r.gauge("test", this, (obj) -> obj.getValue());
      r.gauge("test", this, GaugeUsingLambda::getValue);
    }

    private int getValue() {
      return 42;
    }
  }

  @Test
  public void counters() {
    Registry r = new DefaultRegistry();
    r.counter("foo").increment();
    r.counter("foo", "a", "1", "b", "2").increment();
    r.counter("foo", "a", "1", "b", "3").increment(13L);
    r.counter("foo", "a", "1", "b", "2").increment();
    r.counter("bar", "a", "1", "b", "2").increment();

    Assert.assertEquals(4, r.counters().count());
    final LongSummaryStatistics summary = r.counters()
        .filter(Functions.nameEquals("foo"))
        .collect(Collectors.summarizingLong(Counter::count));
    Assert.assertEquals(3L, summary.getCount());
    Assert.assertEquals(16L, summary.getSum());
    Assert.assertEquals(13L, summary.getMax());
  }

  @Test
  public void timers() {
    Registry r = new DefaultRegistry();
    r.timer("foo").record(1L, TimeUnit.NANOSECONDS);
    r.timer("foo", "a", "1", "b", "2").record(1L, TimeUnit.NANOSECONDS);
    r.timer("foo", "a", "1", "b", "3").record(13L, TimeUnit.NANOSECONDS);
    r.timer("foo", "a", "1", "b", "2").record(1L, TimeUnit.NANOSECONDS);
    r.timer("bar", "a", "1", "b", "2").record(1L, TimeUnit.NANOSECONDS);

    Assert.assertEquals(4, r.timers().count());

    final LongSummaryStatistics countSummary = r.timers()
        .filter(Functions.nameEquals("foo"))
        .collect(Collectors.summarizingLong(Timer::count));
    Assert.assertEquals(3L, countSummary.getCount());
    Assert.assertEquals(4L, countSummary.getSum());
    Assert.assertEquals(2L, countSummary.getMax());

    final LongSummaryStatistics totalSummary = r.timers()
        .filter(Functions.nameEquals("foo"))
        .collect(Collectors.summarizingLong(Timer::totalTime));
    Assert.assertEquals(3L, totalSummary.getCount());
    Assert.assertEquals(16L, totalSummary.getSum());
    Assert.assertEquals(13L, totalSummary.getMax());
  }

  @Test
  public void distributionSummaries() {
    Registry r = new DefaultRegistry();
    r.distributionSummary("foo").record(1L);
    r.distributionSummary("foo", "a", "1", "b", "2").record(1L);
    r.distributionSummary("foo", "a", "1", "b", "3").record(13L);
    r.distributionSummary("foo", "a", "1", "b", "2").record(1L);
    r.distributionSummary("bar", "a", "1", "b", "2").record(1L);

    Assert.assertEquals(4, r.distributionSummaries().count());

    final LongSummaryStatistics countSummary = r.distributionSummaries()
        .filter(Functions.nameEquals("foo"))
        .collect(Collectors.summarizingLong(DistributionSummary::count));
    Assert.assertEquals(3L, countSummary.getCount());
    Assert.assertEquals(4L, countSummary.getSum());
    Assert.assertEquals(2L, countSummary.getMax());

    final LongSummaryStatistics totalSummary = r.distributionSummaries()
        .filter(Functions.nameEquals("foo"))
        .collect(Collectors.summarizingLong(DistributionSummary::totalAmount));
    Assert.assertEquals(3L, totalSummary.getCount());
    Assert.assertEquals(16L, totalSummary.getSum());
    Assert.assertEquals(13L, totalSummary.getMax());
  }
}
