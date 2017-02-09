package com.netflix.spectator.api;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.Assert;

import java.util.Optional;
import java.util.stream.Collectors;

@RunWith(JUnit4.class)
public class ActivityCounterTest {

  private final ManualClock clock = new ManualClock();
  private static final double EPSILON = 1e-12;

  private static Gauge getInterval(Registry r, Id baseId) {
    final Id intervalId = baseId.withTag("statistic", "interval");
    Optional<Meter> headOption = r.stream().filter(meter -> meter.id().equals(intervalId)).findFirst();
    Assert.assertTrue("No interval meter found", headOption.isPresent());
    return (Gauge) headOption.get();
  }

  private static void assertGaugeValue(Registry r, Id id, double expected) {
    ((AbstractRegistry) r).pollGauges();
    Assert.assertEquals(expected, r.gauge(id).value(), EPSILON);
  }

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
    Counter c = ActivityCounter.get(r, id);
    Assert.assertEquals(0L, c.count());
    Gauge interval = getInterval(r, id);
    assertGaugeValue(r, interval.id(), 42.0);

    Assert.assertEquals(2, r.stream().collect(Collectors.toList()).size());
  }


  @Test
  public void testInterval() {
    Registry r = new DefaultRegistry(clock);
    Id id = r.createId("test");
    Counter c = ActivityCounter.get(r, id);
    Gauge interval = getInterval(r, id);
    assertGaugeValue(r, interval.id(), 0.0);
    clock.setWallTime(1000);
    assertGaugeValue(r, interval.id(), 1.0);
    c.increment();
    assertGaugeValue(r, interval.id(), 0.0);
  }


  @Test
  public void testIncrement() {
    Registry r = new DefaultRegistry(clock);
    Id id = r.createId("test");
    Counter c = ActivityCounter.get(r, id);
    Assert.assertEquals(0, c.count());
    c.increment();
    Assert.assertEquals(1, c.count());
    c.increment(41);
    Assert.assertEquals(42, c.count());
  }
}
