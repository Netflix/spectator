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
package com.netflix.spectator.api;

import com.netflix.spectator.api.patterns.PolledMeter;
import com.netflix.spectator.impl.SwapMeter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.DoubleSummaryStatistics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

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
    Assertions.assertEquals(id1, id2);
  }

  @Test
  public void testCreateIdArrayOdd() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> {
      Registry r = newRegistry(true, 10000);
      r.createId("foo", "bar", "baz", "k");
    });
  }

  @Test
  public void testCreateIdMap() {
    Map<String, String> map = new LinkedHashMap<>();
    map.put("k1", "v1");
    map.put("k2", "v2");
    Registry r = newRegistry(true, 10000);
    Id id1 = r.createId("foo", map);
    Id id2 = r.createId("foo", "k1", "v1", "k2", "v2");
    Assertions.assertEquals(id1, id2);
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
    Assertions.assertSame(unwrap(c1), unwrap(c2));
    Assertions.assertNotSame(unwrap(c1), unwrap(c3));
  }

  @Test
  public void testDistributionSummaryHelpers() {
    Registry r = newRegistry(true, 10000);
    DistributionSummary c1 = r.distributionSummary("foo", "bar", "baz", "k", "v");
    DistributionSummary c2 = r.distributionSummary("foo",
        ArrayTagSet.create("k", "v").add(new BasicTag("bar", "baz")));
    DistributionSummary c3 = r.distributionSummary("foo");
    Assertions.assertSame(unwrap(c1), unwrap(c2));
    Assertions.assertNotSame(unwrap(c1), unwrap(c3));
  }

  @Test
  public void testTimerHelpers() {
    Registry r = newRegistry(true, 10000);
    Timer c1 = r.timer("foo", "bar", "baz", "k", "v");
    Timer c2 = r.timer("foo", ArrayTagSet.create("k", "v").add(new BasicTag("bar", "baz")));
    Timer c3 = r.timer("foo");
    Assertions.assertSame(unwrap(c1), unwrap(c2));
    Assertions.assertNotSame(unwrap(c1), unwrap(c3));
  }

  private void assertLongTaskTimer(Registry r, Id id, long timestamp, int activeTasks, double duration) {
    PolledMeter.update(r);

    Gauge g = r.gauge(id.withTag(Statistic.activeTasks));
    Assertions.assertEquals(timestamp, g.measure().iterator().next().timestamp());
    Assertions.assertEquals(activeTasks, g.value(), 1.0e-12);

    g = r.gauge(id.withTag(Statistic.duration));
    Assertions.assertEquals(timestamp, g.measure().iterator().next().timestamp());
    Assertions.assertEquals(duration, g.value(), 1.0e-12);
  }

  @Test
  public void testLongTaskTimerHelpers() {
    ManualClock clock = new ManualClock();
    Registry r = new DefaultRegistry(clock);
    LongTaskTimer c1 = r.longTaskTimer("foo", "bar", "baz", "k", "v");
    assertLongTaskTimer(r, c1.id(), 0L, 0, 0L);

    LongTaskTimer c2 = r.longTaskTimer("foo", ArrayTagSet.create("k", "v").add(new BasicTag("bar", "baz")));
    Assertions.assertEquals(c1.id(), c2.id());

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
    Assertions.assertEquals(expected, r.gauge(id).value(), 1e-12);
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
    Assertions.assertSame(v1, al1);
    Assertions.assertSame(v2, al2);
    Assertions.assertSame(v3, al4);
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
    Assertions.assertSame(v1, al1);
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
    Assertions.assertSame(v1, al1);
    Id id1 = r.createId("foo");
    assertGaugeValue(r, id1, 39.0 / 1000.0);
  }

  @Test
  public void testGaugeHelpersWithCustomFunction2() {
    AtomicLong al1 = new AtomicLong(1L);
    Registry r = new DefaultRegistry(new ManualClock(40, 0));
    ToDoubleFunction<AtomicLong> f = (obj) -> (r.clock().wallTime() - obj.doubleValue()) / 1000.0;

    AtomicLong v1 = r.gauge("foo", al1, f);
    Assertions.assertSame(v1, al1);
    Id id1 = r.createId("foo");
    assertGaugeValue(r, id1, 39.0 / 1000.0);
  }

  @Test
  public void testCollectionSizeHelpers() {
    Registry r = newRegistry(true, 10000);
    LinkedBlockingDeque<String> q1 = new LinkedBlockingDeque<>();
    LinkedBlockingDeque<String> q2 = r.collectionSize("queueSize", q1);
    Assertions.assertSame(q1, q2);
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
    Assertions.assertSame(q1, q2);
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

  @Test
  public void methodValueBadReturnType() {
    Assertions.assertThrows(ClassCastException.class, () -> {
      Registry r = newRegistry(true, 10000);
      r.methodValue("queueSize", this, "toString");
    });
  }

  @Test
  public void methodValueBadReturnTypeNoPropagate() {
    Registry r = newRegistry(false, 10000);
    r.methodValue("queueSize", this, "toString");
    Assertions.assertNull(r.get(r.createId("queueSize")));
  }

  @Test
  public void methodValueUnknown() {
    Assertions.assertThrows(RuntimeException.class, () -> {
      Registry r = newRegistry(true, 10000);
      r.methodValue("queueSize", this, "unknownMethod");
    });
  }

  @Test
  public void methodValueUnknownNoPropagate() {
    Registry r = newRegistry(false, 10000);
    r.methodValue("queueSize", this, "unknownMethod");
    Assertions.assertNull(r.get(r.createId("queueSize")));
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

    Assertions.assertEquals(4, r.counters().count());
    final LongSummaryStatistics summary = r.counters()
        .filter(Functions.nameEquals("foo"))
        .collect(Collectors.summarizingLong(Counter::count));
    Assertions.assertEquals(3L, summary.getCount());
    Assertions.assertEquals(16L, summary.getSum());
    Assertions.assertEquals(13L, summary.getMax());
  }

  @Test
  public void timers() {
    Registry r = newRegistry(true, 10000);
    r.timer("foo").record(1L, TimeUnit.NANOSECONDS);
    r.timer("foo", "a", "1", "b", "2").record(1L, TimeUnit.NANOSECONDS);
    r.timer("foo", "a", "1", "b", "3").record(13L, TimeUnit.NANOSECONDS);
    r.timer("foo", "a", "1", "b", "2").record(1L, TimeUnit.NANOSECONDS);
    r.timer("bar", "a", "1", "b", "2").record(1L, TimeUnit.NANOSECONDS);

    Assertions.assertEquals(4, r.timers().count());

    final LongSummaryStatistics countSummary = r.timers()
        .filter(Functions.nameEquals("foo"))
        .collect(Collectors.summarizingLong(Timer::count));
    Assertions.assertEquals(3L, countSummary.getCount());
    Assertions.assertEquals(4L, countSummary.getSum());
    Assertions.assertEquals(2L, countSummary.getMax());

    final LongSummaryStatistics totalSummary = r.timers()
        .filter(Functions.nameEquals("foo"))
        .collect(Collectors.summarizingLong(Timer::totalTime));
    Assertions.assertEquals(3L, totalSummary.getCount());
    Assertions.assertEquals(16L, totalSummary.getSum());
    Assertions.assertEquals(13L, totalSummary.getMax());
  }

  @Test
  public void distributionSummaries() {
    Registry r = newRegistry(true, 10000);
    r.distributionSummary("foo").record(1L);
    r.distributionSummary("foo", "a", "1", "b", "2").record(1L);
    r.distributionSummary("foo", "a", "1", "b", "3").record(13L);
    r.distributionSummary("foo", "a", "1", "b", "2").record(1L);
    r.distributionSummary("bar", "a", "1", "b", "2").record(1L);

    Assertions.assertEquals(4, r.distributionSummaries().count());

    final LongSummaryStatistics countSummary = r.distributionSummaries()
        .filter(Functions.nameEquals("foo"))
        .collect(Collectors.summarizingLong(DistributionSummary::count));
    Assertions.assertEquals(3L, countSummary.getCount());
    Assertions.assertEquals(4L, countSummary.getSum());
    Assertions.assertEquals(2L, countSummary.getMax());

    final LongSummaryStatistics totalSummary = r.distributionSummaries()
        .filter(Functions.nameEquals("foo"))
        .collect(Collectors.summarizingLong(DistributionSummary::totalAmount));
    Assertions.assertEquals(3L, totalSummary.getCount());
    Assertions.assertEquals(16L, totalSummary.getSum());
    Assertions.assertEquals(13L, totalSummary.getMax());
  }

  @Test
  public void gauges() {
    Registry r = newRegistry(true, 10000);
    r.gauge(r.createId("foo", "a", "1")).set(1.0);
    r.gauge(r.createId("foo", "a", "2")).set(2.0);
    r.gauge(r.createId("bar")).set(7.0);

    Assertions.assertEquals(3, r.gauges().count());

    final DoubleSummaryStatistics valueSummary = r.gauges()
        .filter(Functions.nameEquals("foo"))
        .collect(Collectors.summarizingDouble(Gauge::value));
    Assertions.assertEquals(2, valueSummary.getCount());
    Assertions.assertEquals(3.0, valueSummary.getSum(), 1e-12);
    Assertions.assertEquals(1.5, valueSummary.getAverage(), 1e-12);
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
    Assertions.assertEquals(1, valueSummary.getCount());
    Assertions.assertEquals(3.0, valueSummary.getSum(), 1e-12);
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

  @Test
  public void propagateWarningsTrue() {
    Assertions.assertThrows(RuntimeException.class, () -> {
      RegistryConfig config = k -> "propagateWarnings".equals(k) ? "true" : null;
      Registry r = new DefaultRegistry(Clock.SYSTEM, config);
      r.propagate("foo", new RuntimeException("test"));
    });
  }

  @Test
  public void keepNonExpired() {
    ManualClock clock = new ManualClock();
    ExpiringRegistry registry = new ExpiringRegistry(clock);

    registry.counter("test").increment();
    registry.removeExpiredMeters();
    Assertions.assertEquals(1, registry.counters().count());
  }

  @Test
  public void removeExpired() {
    ManualClock clock = new ManualClock();
    ExpiringRegistry registry = new ExpiringRegistry(clock);

    registry.counter("test").increment();
    clock.setWallTime(1);
    registry.removeExpiredMeters();
    Assertions.assertEquals(0, registry.counters().count());
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
    Assertions.assertEquals(0, registry.counters().count());

    cr.counter("test").increment();
    Assertions.assertEquals(1, registry.counters().count());
  }

  @Test
  public void resurrectUsingCachedRef() {
    ManualClock clock = new ManualClock();
    ExpiringRegistry registry = new ExpiringRegistry(clock);
    Counter c = registry.counter("test");

    c.increment();
    clock.setWallTime(60000 * 30);
    registry.removeExpiredMeters();
    Assertions.assertEquals(0, registry.counters().count());

    c.increment();
    Assertions.assertEquals(1, registry.counters().count());
  }

  @Test
  public void resurrectUsingCachedRefInit() {
    ManualClock clock = new ManualClock();
    ExpiringRegistry registry = new ExpiringRegistry(clock);
    Counter c = registry.counter("test");

    clock.setWallTime(60000 * 30);
    registry.removeExpiredMeters();
    Assertions.assertEquals(0, registry.counters().count());

    c.increment();
    Assertions.assertEquals(1, registry.counters().count());
  }

  @Test
  public void resurrectUsingCachedRefInitTimer() {
    ManualClock clock = new ManualClock();
    ExpiringRegistry registry = new ExpiringRegistry(clock);
    Timer t = registry.timer("test");

    clock.setWallTime(60000 * 30);
    registry.removeExpiredMeters();
    Assertions.assertEquals(0, registry.timers().count());

    t.record(42, TimeUnit.NANOSECONDS);
    Assertions.assertEquals(1, registry.timers().count());
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
    Assertions.assertEquals(0, registry.counters().count());

    c.increment();
    Assertions.assertEquals(1, registry.counters().count());
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
      Assertions.assertEquals(0, registry.timers().count());
    });

    Assertions.assertEquals(1, registry.timers().count());
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
      Assertions.assertEquals(1, c.count());
      Assertions.assertEquals(1, registry.counter("test").count());
    }
  }

  @Test
  public void uncaughtExceptionFromGaugeFunction() {
    Assertions.assertThrows(RuntimeException.class, () -> {
      Registry registry = new DefaultRegistry();
      PolledMeter.using(registry)
          .withName("test")
          .monitorValue(new RuntimeException("failure"), value -> {
            throw value;
          });
      PolledMeter.update(registry);
    });
  }

  @Test
  public void defaultResetRemovesMeters() {
    DefaultRegistry r = newRegistry(true, 10000);
    r.counter("test").increment();
    Assertions.assertEquals(1, r.stream().count());
    r.reset();
    Assertions.assertEquals(0, r.stream().count());
  }

  @Test
  public void defaultResetRemovesState() {
    DefaultRegistry r = newRegistry(true, 10000);
    AtomicInteger v = new AtomicInteger();
    PolledMeter.using(r).withName("test").monitorValue(v);
    PolledMeter.update(r);
    Assertions.assertEquals(1, r.stream().count());
    Assertions.assertEquals(1, r.state().size());
    r.reset();
    PolledMeter.update(r);
    Assertions.assertEquals(0, r.stream().count());
    Assertions.assertEquals(0, r.state().size());
  }

  @Test
  public void customIdTags() {
    DefaultRegistry r = newRegistry(false, 10000);
    Id id = new CustomId("test").withTags("b", "2", "a", "1");
    r.counter(id).increment();
    Assertions.assertEquals(1, r.counter("test", "a", "1", "b", "2").count());
  }

  @Test
  public void customIdCounter() {
    DefaultRegistry r = newRegistry(false, 10000);
    Id id = new CustomId("test");
    r.counter(id).increment();
    Assertions.assertEquals(1, r.counter("test").count());
  }

  @Test
  public void customIdGauge() {
    DefaultRegistry r = newRegistry(false, 10000);
    Id id = new CustomId("test");
    r.gauge(id).set(42.0);
    Assertions.assertEquals(42.0, r.gauge("test").value(), 1e-12);
  }

  @Test
  public void customIdMaxGauge() {
    DefaultRegistry r = newRegistry(false, 10000);
    Id id = new CustomId("test");
    r.maxGauge(id).set(42.0);
    Assertions.assertEquals(42.0, r.maxGauge("test").value(), 1e-12);
  }

  @Test
  public void customIdTimer() {
    DefaultRegistry r = newRegistry(false, 10000);
    Id id = new CustomId("test");
    r.timer(id).record(42, TimeUnit.NANOSECONDS);
    Assertions.assertEquals(42L, r.timer("test").totalTime());
  }

  @Test
  public void customIdDistributionSummary() {
    DefaultRegistry r = newRegistry(false, 10000);
    Id id = new CustomId("test");
    r.distributionSummary(id).record(42);
    Assertions.assertEquals(42L, r.distributionSummary("test").totalAmount());
  }

  @Test
  public void customIdGetKey() {
    Id id = new CustomId("test").withTags("b", "2", "a", "1");
    Assertions.assertEquals("name", id.getKey(0));
    Assertions.assertEquals("b", id.getKey(1));
    Assertions.assertEquals("a", id.getKey(2));
  }

  @Test
  public void customIdGetValue() {
    Id id = new CustomId("test").withTags("b", "2", "a", "1");
    Assertions.assertEquals("test", id.getValue(0));
    Assertions.assertEquals("2", id.getValue(1));
    Assertions.assertEquals("1", id.getValue(2));
  }

  @Test
  public void customIdSize() {
    Id id = new CustomId("test").withTags("b", "2", "a", "1");
    Assertions.assertEquals(3, id.size());
  }

  // Used to test that custom Id implementations will get normalized correctly and that
  // backwards compatibility for inheritance is maintained.
  public static class CustomId implements Id {

    private final String name;
    private final List<Tag> tags;

    CustomId(String name) {
      this(name, Collections.emptyList());
    }

    CustomId(String name, List<Tag> tags) {
      this.name = name;
      this.tags = Collections.unmodifiableList(tags);
    }

    private List<Tag> append(Tag tag) {
      List<Tag> tmp = new ArrayList<>(tags);
      tmp.add(tag);
      return tmp;
    }

    @Override public String name() {
      return name;
    }

    @Override public Iterable<Tag> tags() {
      return tags;
    }

    @Override public Id withTag(String k, String v) {
      return new CustomId(name, append(Tag.of(k, v)));
    }

    @Override public Id withTag(Tag t) {
      return new CustomId(name, append(t));
    }
  }
}
