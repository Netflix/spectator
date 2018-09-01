/*
 * Copyright 2014-2018 Netflix, Inc.
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

import com.netflix.spectator.api.patterns.PolledMeter;
import com.netflix.spectator.impl.SwapMeter;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.DoubleSummaryStatistics;
import java.util.LinkedHashMap;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

@RunWith(JUnit4.class)
public class RegistryTest {

  private final ManualClock clock = new ManualClock();

  private DefaultRegistry newRegistry(boolean warnings, int numberOfMeters) {
    return new DefaultRegistry(clock, new TestRegistryConfig(warnings, numberOfMeters));
  }

  @Test
  public void testCreateIdArray() {
    Registry r = newRegistry(true, 10000);
    Id id1 = r.createId("foo", "bar", "baz", "k", "v");
    Id id2 = r.createId("foo", ArrayTagSet.create("k", "v").add(new BasicTag("bar", "baz")));
    Assert.assertEquals(id1, id2);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCreateIdArrayOdd() {
    Registry r = newRegistry(true, 10000);
    r.createId("foo", "bar", "baz", "k");
  }

  @Test
  public void testCreateIdMap() {
    Map<String, String> map = new LinkedHashMap<>();
    map.put("k1", "v1");
    map.put("k2", "v2");
    Registry r = newRegistry(true, 10000);
    Id id1 = r.createId("foo", map);
    Id id2 = r.createId("foo", "k1", "v1", "k2", "v2");
    Assert.assertEquals(id1, id2);
  }

  private Object unwrap(Object obj) {
    if (obj instanceof SwapMeter<?>) {
      return ((SwapMeter<?>) obj).get();
    }
    return obj;
  }

  @Test
  public void testCounterHelpers() {
    Registry r = newRegistry(true, 10000);
    Counter c1 = r.counter("foo", "bar", "baz", "k", "v");
    Counter c2 = r.counter("foo", ArrayTagSet.create("k", "v").add(new BasicTag("bar", "baz")));
    Counter c3 = r.counter("foo");
    Assert.assertSame(unwrap(c1), unwrap(c2));
    Assert.assertNotSame(unwrap(c1), unwrap(c3));
  }

  @Test
  public void testDistributionSummaryHelpers() {
    Registry r = newRegistry(true, 10000);
    DistributionSummary c1 = r.distributionSummary("foo", "bar", "baz", "k", "v");
    DistributionSummary c2 = r.distributionSummary("foo",
        ArrayTagSet.create("k", "v").add(new BasicTag("bar", "baz")));
    DistributionSummary c3 = r.distributionSummary("foo");
    Assert.assertSame(unwrap(c1), unwrap(c2));
    Assert.assertNotSame(unwrap(c1), unwrap(c3));
  }

  @Test
  public void testTimerHelpers() {
    Registry r = newRegistry(true, 10000);
    Timer c1 = r.timer("foo", "bar", "baz", "k", "v");
    Timer c2 = r.timer("foo", ArrayTagSet.create("k", "v").add(new BasicTag("bar", "baz")));
    Timer c3 = r.timer("foo");
    Assert.assertSame(unwrap(c1), unwrap(c2));
    Assert.assertNotSame(unwrap(c1), unwrap(c3));
  }

  private void assertLongTaskTimer(Registry r, Id id, long timestamp, int activeTasks, double duration) {
    PolledMeter.update(r);

    Gauge g = r.gauge(id.withTag(Statistic.activeTasks));
    Assert.assertEquals(timestamp, g.measure().iterator().next().timestamp());
    Assert.assertEquals(activeTasks, g.value(), 1.0e-12);

    g = r.gauge(id.withTag(Statistic.duration));
    Assert.assertEquals(timestamp, g.measure().iterator().next().timestamp());
    Assert.assertEquals(duration, g.value(), 1.0e-12);
  }

  @Test
  public void testLongTaskTimerHelpers() {
    ManualClock clock = new ManualClock();
    Registry r = new DefaultRegistry(clock);
    LongTaskTimer c1 = r.longTaskTimer("foo", "bar", "baz", "k", "v");
    assertLongTaskTimer(r, c1.id(), 0L, 0, 0L);

    LongTaskTimer c2 = r.longTaskTimer("foo", ArrayTagSet.create("k", "v").add(new BasicTag("bar", "baz")));
    Assert.assertEquals(c1.id(), c2.id());

    long t1 = c1.start();
    long t2 = c2.start();
    clock.setMonotonicTime(1000L);
    clock.setWallTime(1L);
    assertLongTaskTimer(r, c1.id(), 1L, 2, 2.0e-6);

    c1.stop(t1);
    assertLongTaskTimer(r, c1.id(), 1L, 1, 1.0e-6);

    c2.stop(t2);
    assertLongTaskTimer(r, c1.id(), 1L, 0, 0L);
  }

  private void assertGaugeValue(Registry r, Id id, double expected) {
    PolledMeter.update(r);
    Assert.assertEquals(expected, r.gauge(id).value(), 1e-12);
  }

  @Test
  public void testGaugeHelpers() {
    AtomicLong al1 = new AtomicLong(1L);
    AtomicLong al2 = new AtomicLong(2L);
    AtomicLong al4 = new AtomicLong(4L);
    Registry r = newRegistry(true, 10000);
    AtomicLong v1 = r.gauge(r.createId("foo", "bar", "baz", "k", "v"), al1);
    AtomicLong v2 = r.gauge("foo", ArrayTagSet.create("k", "v").add(new BasicTag("bar", "baz")), al2);
    AtomicLong v3 = r.gauge("foo", al4);
    Assert.assertSame(v1, al1);
    Assert.assertSame(v2, al2);
    Assert.assertSame(v3, al4);
    Id id1 = r.createId("foo", "bar", "baz", "k", "v");
    Id id2 = r.createId("foo");
    assertGaugeValue(r, id1, 3.0);
    assertGaugeValue(r, id2, 4.0);
  }

  @Test
  public void testGaugeHelpersWithFunction() {
    AtomicLong al1 = new AtomicLong(1L);
    Registry r = new DefaultRegistry(new ManualClock(40, 0));
    DoubleFunction<AtomicLong> f = Functions.age(r.clock());
    AtomicLong v1 = r.gauge("foo", al1, f);
    Assert.assertSame(v1, al1);
    Id id1 = r.createId("foo");
    assertGaugeValue(r, id1, 39.0 / 1000.0);
  }

  @Test
  public void testGaugeHelpersWithCustomFunction() {
    AtomicLong al1 = new AtomicLong(1L);
    Registry r = new DefaultRegistry(new ManualClock(40, 0));
    DoubleFunction<AtomicLong> f = new DoubleFunction<AtomicLong>() {
      @Override
      public double apply(double v) {
        return (r.clock().wallTime() - v) / 1000.0;
      }
    };
    AtomicLong v1 = r.gauge("foo", al1, f);
    Assert.assertSame(v1, al1);
    Id id1 = r.createId("foo");
    assertGaugeValue(r, id1, 39.0 / 1000.0);
  }

  @Test
  public void testGaugeHelpersWithCustomFunction2() {
    AtomicLong al1 = new AtomicLong(1L);
    Registry r = new DefaultRegistry(new ManualClock(40, 0));
    ToDoubleFunction<AtomicLong> f = (obj) -> (r.clock().wallTime() - obj.doubleValue()) / 1000.0;

    AtomicLong v1 = r.gauge("foo", al1, f);
    Assert.assertSame(v1, al1);
    Id id1 = r.createId("foo");
    assertGaugeValue(r, id1, 39.0 / 1000.0);
  }

  @Test
  public void testCollectionSizeHelpers() {
    Registry r = newRegistry(true, 10000);
    LinkedBlockingDeque<String> q1 = new LinkedBlockingDeque<>();
    LinkedBlockingDeque<String> q2 = r.collectionSize("queueSize", q1);
    Assert.assertSame(q1, q2);
    Id id = r.createId("queueSize");
    assertGaugeValue(r, id, 0.0);
    q2.push("foo");
    assertGaugeValue(r, id, 1.0);
  }

  @Test
  public void testMapSizeHelpers() {
    Registry r = newRegistry(true, 10000);
    ConcurrentHashMap<String, String> q1 = new ConcurrentHashMap<>();
    ConcurrentHashMap<String, String> q2 = r.mapSize("mapSize", q1);
    Assert.assertSame(q1, q2);
    Id id = r.createId("mapSize");
    assertGaugeValue(r, id, 0.0);
    q2.put("foo", "bar");
    assertGaugeValue(r, id, 1.0);
  }

  @Test
  public void testMethodValueHelpers() {
    Registry r = newRegistry(true, 10000);
    LinkedBlockingDeque<String> q1 = new LinkedBlockingDeque<>();
    r.methodValue("queueSize", q1, "size");
    Id id = r.createId("queueSize");
    assertGaugeValue(r, id, 0.0);
    q1.push("foo");
    assertGaugeValue(r, id, 1.0);
  }

  @Test(expected = ClassCastException.class)
  public void methodValueBadReturnType() {
    Registry r = newRegistry(true, 10000);
    r.methodValue("queueSize", this, "toString");
  }

  @Test
  public void methodValueBadReturnTypeNoPropagate() {
    Registry r = newRegistry(false, 10000);
    r.methodValue("queueSize", this, "toString");
    Assert.assertNull(r.get(r.createId("queueSize")));
  }

  @Test(expected = RuntimeException.class)
  public void methodValueUnknown() {
    Registry r = newRegistry(true, 10000);
    r.methodValue("queueSize", this, "unknownMethod");
  }

  @Test
  public void methodValueUnknownNoPropagate() {
    Registry r = newRegistry(false, 10000);
    r.methodValue("queueSize", this, "unknownMethod");
    Assert.assertNull(r.get(r.createId("queueSize")));
  }

  @Test
  public void monitorUsingLambda() {
    Registry r = newRegistry(true, 10000);
    GaugeUsingLambda g = new GaugeUsingLambda(r);
    assertGaugeValue(r, r.createId("test"), 84.0);
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
    Registry r = newRegistry(true, 10000);
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
    Registry r = newRegistry(true, 10000);
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
    Registry r = newRegistry(true, 10000);
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

  @Test
  public void gauges() {
    Registry r = newRegistry(true, 10000);
    r.gauge(r.createId("foo", "a", "1")).set(1.0);
    r.gauge(r.createId("foo", "a", "2")).set(2.0);
    r.gauge(r.createId("bar")).set(7.0);

    Assert.assertEquals(3, r.gauges().count());

    final DoubleSummaryStatistics valueSummary = r.gauges()
        .filter(Functions.nameEquals("foo"))
        .collect(Collectors.summarizingDouble(Gauge::value));
    Assert.assertEquals(2, valueSummary.getCount());
    Assert.assertEquals(3.0, valueSummary.getSum(), 1e-12);
    Assert.assertEquals(1.5, valueSummary.getAverage(), 1e-12);
  }

  @Test
  public void maxGauge() {
    Registry r = newRegistry(true, 10000);
    r.maxGauge("foo").set(1.0);
    r.maxGauge("foo").set(3.0);
    r.maxGauge("foo").set(2.0);

    final DoubleSummaryStatistics valueSummary = r.gauges()
        .filter(Functions.nameEquals("foo"))
        .collect(Collectors.summarizingDouble(Gauge::value));
    Assert.assertEquals(1, valueSummary.getCount());
    Assert.assertEquals(3.0, valueSummary.getSum(), 1e-12);
  }

  @Test
  public void propagateWarningsDefault() {
    RegistryConfig config = k -> null;
    Registry r = new DefaultRegistry(Clock.SYSTEM, config);
    r.propagate("foo", new RuntimeException("test"));
  }

  @Test
  public void propagateWarningsFalse() {
    RegistryConfig config = k -> "propagateWarnings".equals(k) ? "false" : null;
    Registry r = new DefaultRegistry(Clock.SYSTEM, config);
    r.propagate("foo", new RuntimeException("test"));
  }

  @Test(expected = RuntimeException.class)
  public void propagateWarningsTrue() {
    RegistryConfig config = k -> "propagateWarnings".equals(k) ? "true" : null;
    Registry r = new DefaultRegistry(Clock.SYSTEM, config);
    r.propagate("foo", new RuntimeException("test"));
  }

  @Test
  public void keepNonExpired() {
    ManualClock clock = new ManualClock();
    ExpiringRegistry registry = new ExpiringRegistry(clock);

    registry.counter("test").increment();
    registry.removeExpiredMeters();
    Assert.assertEquals(1, registry.counters().count());
  }

  @Test
  public void removeExpired() {
    ManualClock clock = new ManualClock();
    ExpiringRegistry registry = new ExpiringRegistry(clock);

    registry.counter("test").increment();
    clock.setWallTime(1);
    registry.removeExpiredMeters();
    Assert.assertEquals(0, registry.counters().count());
  }

  @Test
  public void resurrectExpiredUsingComposite() {
    ManualClock clock = new ManualClock();
    ExpiringRegistry registry = new ExpiringRegistry(clock);
    CompositeRegistry cr = Spectator.globalRegistry();
    cr.removeAll();
    cr.add(registry);

    cr.counter("test").increment();
    clock.setWallTime(60000 * 30);
    registry.removeExpiredMeters();
    Assert.assertEquals(0, registry.counters().count());

    cr.counter("test").increment();
    Assert.assertEquals(1, registry.counters().count());
  }

  @Test
  public void resurrectUsingCachedRef() {
    ManualClock clock = new ManualClock();
    ExpiringRegistry registry = new ExpiringRegistry(clock);
    Counter c = registry.counter("test");

    c.increment();
    clock.setWallTime(60000 * 30);
    registry.removeExpiredMeters();
    Assert.assertEquals(0, registry.counters().count());

    c.increment();
    Assert.assertEquals(1, registry.counters().count());
  }

  @Test
  public void resurrectUsingCachedRefInit() {
    ManualClock clock = new ManualClock();
    ExpiringRegistry registry = new ExpiringRegistry(clock);
    Counter c = registry.counter("test");

    clock.setWallTime(60000 * 30);
    registry.removeExpiredMeters();
    Assert.assertEquals(0, registry.counters().count());

    c.increment();
    Assert.assertEquals(1, registry.counters().count());
  }

  @Test
  public void resurrectUsingCachedRefInitTimer() {
    ManualClock clock = new ManualClock();
    ExpiringRegistry registry = new ExpiringRegistry(clock);
    Timer t = registry.timer("test");

    clock.setWallTime(60000 * 30);
    registry.removeExpiredMeters();
    Assert.assertEquals(0, registry.timers().count());

    t.record(42, TimeUnit.NANOSECONDS);
    Assert.assertEquals(1, registry.timers().count());
  }

  @Test
  public void resurrectExpiredUsingCompositeCachedRef() {
    ManualClock clock = new ManualClock();
    ExpiringRegistry registry = new ExpiringRegistry(clock);
    CompositeRegistry cr = Spectator.globalRegistry();
    cr.removeAll();
    cr.add(registry);
    Counter c = cr.counter("test");

    c.increment();
    clock.setWallTime(60000 * 30);
    registry.removeExpiredMeters();
    Assert.assertEquals(0, registry.counters().count());

    c.increment();
    Assert.assertEquals(1, registry.counters().count());
  }

  @Test
  public void resurrectUsingLambda() {
    ManualClock clock = new ManualClock();
    ExpiringRegistry registry = new ExpiringRegistry(clock);
    Timer t = registry.timer("test");

    t.record(() -> {
      // Force expiration in the body of the lambda
      clock.setWallTime(60000 * 30);
      registry.removeExpiredMeters();
      Assert.assertEquals(0, registry.timers().count());
    });

    Assert.assertEquals(1, registry.timers().count());
  }

  @Test
  public void expireAndResurrectLoop() {
    ManualClock clock = new ManualClock();
    ExpiringRegistry registry = new ExpiringRegistry(clock);
    Counter c = registry.counter("test");
    for (int i = 0; i < 1000; ++i) {
      clock.setWallTime(60000 * 30 * i);
      registry.removeExpiredMeters();
      c.increment();
      Assert.assertEquals(1, c.count());
      Assert.assertEquals(1, registry.counter("test").count());
    }
  }

  @Test(expected = RuntimeException.class)
  public void uncaughtExceptionFromGaugeFunction() {
    Registry registry = new DefaultRegistry();
    PolledMeter.using(registry)
        .withName("test")
        .monitorValue(new RuntimeException("failure"), value -> {
          throw value;
        });
    PolledMeter.update(registry);
  }
}
