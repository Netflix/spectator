/*
 * Copyright 2014-2022 Netflix, Inc.
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
package com.netflix.spectator.metrics5;

import io.dropwizard.metrics5.Gauge;
import io.dropwizard.metrics5.MetricName;
import io.dropwizard.metrics5.MetricRegistry;
import com.netflix.spectator.api.ManualClock;
import com.netflix.spectator.api.Meter;
import com.netflix.spectator.api.patterns.PolledMeter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class MetricsRegistryTest {

  private final ManualClock clock = new ManualClock();

  @Test
  public void metricName() {
    MetricRegistry dwRegistry = new MetricRegistry();
    MetricsRegistry r = new MetricsRegistry(clock, dwRegistry);
    r.counter("foo", "id", "bar", "a", "b", "a", "c").increment();
    final Map<String, String> expectedTags = new HashMap<>();
    expectedTags.put("a", "c");
    expectedTags.put("id", "bar");
    Assertions.assertTrue(dwRegistry.getMeters()
            .containsKey(new MetricName("foo", expectedTags)));
  }

  @Test
  public void counter() {
    MetricRegistry dwRegistry = new MetricRegistry();
    MetricsRegistry r = new MetricsRegistry(clock, dwRegistry);
    r.counter("foo").increment();
    Assertions.assertEquals(1, dwRegistry.getMeters().get(new MetricName("foo", Collections.EMPTY_MAP)).getCount());
    r.counter("foo").increment(15);
    Assertions.assertEquals(16, dwRegistry.getMeters().get(new MetricName("foo", Collections.EMPTY_MAP)).getCount());
  }

  @Test
  public void timer() {
    MetricRegistry dwRegistry = new MetricRegistry();
    MetricsRegistry r = new MetricsRegistry(clock, dwRegistry);
    r.timer("foo").record(1, TimeUnit.MILLISECONDS);
    Assertions.assertEquals(1, dwRegistry.getTimers().get(new MetricName("foo", Collections.EMPTY_MAP)).getCount());
  }

  @Test
  public void distributionSummary() {
    MetricRegistry dwRegistry = new MetricRegistry();
    MetricsRegistry r = new MetricsRegistry(clock, dwRegistry);
    r.distributionSummary("foo").record(1);
    Assertions.assertEquals(1, dwRegistry.getHistograms().get(new MetricName("foo", Collections.EMPTY_MAP)).getCount());
  }

  private void assertGaugeValue(
      MetricsRegistry r, MetricRegistry dwRegistry, String name, double expected) {
    PolledMeter.update(r);
    Assertions.assertEquals(expected, (Double) dwRegistry.getGauges().get(new MetricName(name, Collections.EMPTY_MAP)).getValue(), 1e-12);
  }

  @Test
  public void gaugeNumber() {
    MetricRegistry dwRegistry = new MetricRegistry();
    MetricsRegistry r = new MetricsRegistry(clock, dwRegistry);
    AtomicInteger num = r.gauge("foo", new AtomicInteger(42));
    assertGaugeValue(r, dwRegistry, "foo", 42.0);
  }

  @Test
  public void gaugeNumberDuplicate() {
    MetricRegistry dwRegistry = new MetricRegistry();
    MetricsRegistry r = new MetricsRegistry(clock, dwRegistry);
    AtomicInteger num1 = r.gauge("foo", new AtomicInteger(42));
    AtomicInteger num2 = r.gauge("foo", new AtomicInteger(21));
    assertGaugeValue(r, dwRegistry, "foo", 63.0);
  }

  @Test
  public void gaugeCollection() throws Exception {
    MetricRegistry dwRegistry = new MetricRegistry();
    MetricsRegistry r = new MetricsRegistry(clock, dwRegistry);
    final List<Integer> foo = r.collectionSize("foo", Arrays.asList(1, 2, 3, 4, 5));
    assertGaugeValue(r, dwRegistry, "foo", 5.0);
  }

  @Test
  public void gaugeMap() throws Exception {
    MetricRegistry dwRegistry = new MetricRegistry();
    MetricsRegistry r = new MetricsRegistry(clock, dwRegistry);
    Map<String, String> map = new HashMap<>();
    map.put("foo", "bar");
    r.mapSize("fooMap", map);
    assertGaugeValue(r, dwRegistry, "fooMap", 1.0);
  }

  @Test
  public void gaugeRegisteredDirectly() throws Exception {
    MetricRegistry dwRegistry = new MetricRegistry();
    MetricsRegistry r = new MetricsRegistry(clock, dwRegistry);

    // Directly register a gauge with metrics register
    dwRegistry.registerGauge("foo", () -> 42.0D);

    // Try to register the same gauge via spectator
    AtomicInteger num = r.gauge("foo", new AtomicInteger(42));

    // Should be registered with the Dropwizard registry
    Assertions.assertEquals(42.0, (Double) dwRegistry.getGauges().get(new MetricName("foo", Collections.EMPTY_MAP)).getValue(), 1e-12);

    // Should not be registered with spectator
    Assertions.assertNull(r.get(r.createId("foo")));
  }

  @Test
  public void iterator() {
    MetricRegistry dwRegistry = new MetricRegistry();
    MetricsRegistry r = new MetricsRegistry(clock, dwRegistry);

    r.counter("c");
    r.timer("t");
    r.distributionSummary("s");
    r.gauge("g");

    Set<String> actual = new HashSet<>();
    for (Meter m : r) {
      Assertions.assertFalse(m.hasExpired());
      actual.add(m.id().name());
    }

    Set<String> expected = new HashSet<>();
    expected.add("c");
    expected.add("t");
    expected.add("s");
    expected.add("g");
    Assertions.assertEquals(expected, actual);
  }
}
