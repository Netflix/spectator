/*
 * Copyright 2014-2016 Netflix, Inc.
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
package com.netflix.spectator.metrics3;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.netflix.spectator.api.AbstractRegistry;
import com.netflix.spectator.api.ManualClock;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(JUnit4.class)
public class MetricsRegistryTest {

  private final ManualClock clock = new ManualClock();

  @Test
  public void metricName() {
    MetricRegistry codaRegistry = new MetricRegistry();
    MetricsRegistry r = new MetricsRegistry(clock, codaRegistry);
    r.counter("foo", "id", "bar", "a", "b", "a", "c").increment();
    Assert.assertTrue(codaRegistry.getMeters().containsKey("foo.a-c.id-bar"));
  }

  @Test
  public void counter() {
    MetricRegistry codaRegistry = new MetricRegistry();
    MetricsRegistry r = new MetricsRegistry(clock, codaRegistry);
    r.counter("foo").increment();
    Assert.assertEquals(1, codaRegistry.getMeters().get("foo").getCount());
    r.counter("foo").increment(15);
    Assert.assertEquals(16, codaRegistry.getMeters().get("foo").getCount());
  }

  @Test
  public void timer() {
    MetricRegistry codaRegistry = new MetricRegistry();
    MetricsRegistry r = new MetricsRegistry(clock, codaRegistry);
    r.timer("foo").record(1, TimeUnit.MILLISECONDS);
    Assert.assertEquals(1, codaRegistry.getTimers().get("foo").getCount());
  }

  @Test
  public void distributionSummary() {
    MetricRegistry codaRegistry = new MetricRegistry();
    MetricsRegistry r = new MetricsRegistry(clock, codaRegistry);
    r.distributionSummary("foo").record(1);
    Assert.assertEquals(1, codaRegistry.getHistograms().get("foo").getCount());
  }

  private void assertGaugeValue(
      MetricsRegistry r, MetricRegistry codaRegistry, String name, double expected) {
    r.iterator(); // To force polling of gauges
    Assert.assertEquals(expected, (Double) codaRegistry.getGauges().get(name).getValue(), 1e-12);
  }

  @Test
  public void gaugeNumber() {
    MetricRegistry codaRegistry = new MetricRegistry();
    MetricsRegistry r = new MetricsRegistry(clock, codaRegistry);
    AtomicInteger num = r.gauge("foo", new AtomicInteger(42));
    assertGaugeValue(r, codaRegistry, "foo", 42.0);
  }

  @Test
  public void monitorNumber() {
    MetricRegistry codaRegistry = new MetricRegistry();
    MetricsRegistry r = new MetricsRegistry(clock, codaRegistry);
    AtomicInteger num = r.monitorNumber("foo", new AtomicInteger(42));
    assertGaugeValue(r, codaRegistry, "foo", 42.0);
  }

  @Test
  public void gaugeNumberDuplicate() {
    MetricRegistry codaRegistry = new MetricRegistry();
    MetricsRegistry r = new MetricsRegistry(clock, codaRegistry);
    AtomicInteger num1 = r.gauge("foo", new AtomicInteger(42));
    AtomicInteger num2 = r.gauge("foo", new AtomicInteger(21));
    assertGaugeValue(r, codaRegistry, "foo", 63.0);
  }

  @Test
  public void monitorNumberDuplicate() {
    MetricRegistry codaRegistry = new MetricRegistry();
    MetricsRegistry r = new MetricsRegistry(clock, codaRegistry);
    AtomicInteger num1 = r.monitorNumber("foo", new AtomicInteger(42));
    AtomicInteger num2 = r.monitorNumber("foo", new AtomicInteger(21));
    assertGaugeValue(r, codaRegistry, "foo", 63.0);
  }

  @Test
  public void gaugeCollection() throws Exception {
    MetricRegistry codaRegistry = new MetricRegistry();
    MetricsRegistry r = new MetricsRegistry(clock, codaRegistry);
    final List<Integer> foo = r.collectionSize("foo", Arrays.asList(1, 2, 3, 4, 5));
    assertGaugeValue(r, codaRegistry, "foo", 5.0);
  }

  @Test
  public void gaugeMap() throws Exception {
    MetricRegistry codaRegistry = new MetricRegistry();
    MetricsRegistry r = new MetricsRegistry(clock, codaRegistry);
    Map<String, String> map = new HashMap<>();
    map.put("foo", "bar");
    r.mapSize("fooMap", map);
    assertGaugeValue(r, codaRegistry, "fooMap", 1.0);
  }

  @Test
  public void gaugeRegisteredDirectly() throws Exception {
    MetricRegistry codaRegistry = new MetricRegistry();
    MetricsRegistry r = new MetricsRegistry(clock, codaRegistry);

    // Directly register a gauge with metrics register
    codaRegistry.register("foo", (Gauge<Double>) () -> 42.0D);

    // Try to register the same gauge via spectator
    AtomicInteger num = r.monitorNumber("foo", new AtomicInteger(42));

    // Should be registered with the coda
    Assert.assertEquals(42.0, (Double) codaRegistry.getGauges().get("foo").getValue(), 1e-12);

    // Should not be registered with spectator
    Assert.assertNull(r.get(r.createId("foo")));
  }
}
