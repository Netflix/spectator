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
package com.netflix.spectator.micrometer;

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DistributionSummary;
import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Meter;
import com.netflix.spectator.api.Statistic;
import com.netflix.spectator.api.Tag;
import com.netflix.spectator.api.Timer;
import com.netflix.spectator.api.Utils;
import com.netflix.spectator.api.patterns.LongTaskTimer;
import com.netflix.spectator.api.patterns.PolledMeter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;


public class MicrometerRegistryTest {

  private MockClock clock;
  private MeterRegistry meterRegistry;
  private MicrometerRegistry registry;

  @BeforeEach
  public void before() {
    clock = new MockClock();
    meterRegistry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);
    registry = new MicrometerRegistry(meterRegistry);
  }

  @Test
  public void createIdName() {
    Id id = registry.createId("foo");
    Assertions.assertEquals("foo", id.name());
    Assertions.assertFalse(id.tags().iterator().hasNext());
  }

  @Test
  public void createIdNameAndTags() {
    Id id = registry.createId("foo", "a", "1", "b", "2");
    Assertions.assertEquals("foo", id.name());

    Map<String, String> tags = new HashMap<>();
    tags.put("a", "1");
    tags.put("b", "2");

    for (Tag t : id.tags()) {
      Assertions.assertEquals(tags.remove(t.key()), t.value());
    }
    Assertions.assertTrue(tags.isEmpty());
  }

  @Test
  public void notPresentGet() {
    Assertions.assertNull(registry.get(registry.createId("foo")));
  }

  @Test
  public void counterIncrement() {
    Counter c = registry.counter("foo");
    Assertions.assertEquals(0, c.count());
    c.increment();
    Assertions.assertEquals(1, c.count());
  }

  @Test
  public void counterAdd() {
    Counter c = registry.counter("foo");
    Assertions.assertEquals(0.0, c.actualCount(), 1e-12);
    c.add(1.5);
    Assertions.assertEquals(1.5, c.actualCount(), 1e-12);
  }

  @Test
  public void counterMeasure() {
    Counter c = registry.counter("foo");
    c.increment();
    int i = 0;
    for (Measurement m : c.measure()) {
      ++i;
      Assertions.assertEquals("foo", m.id().name());
      Assertions.assertEquals(1.0, m.value(), 1e-12);
    }
    Assertions.assertEquals(1, i);
  }

  @Test
  public void counterWithTags() {
    Counter c1 = registry.counter("foo", "a", "1", "b", "2");
    c1.increment();

    Counter c2 = registry.counter(registry.createId("foo", "a", "1", "b", "2"));
    Assertions.assertEquals(1, c2.count());
  }

  @Test
  public void counterGet() {
    Counter c1 = registry.counter("foo");
    c1.increment();

    Counter c2 = (Counter) registry.get(registry.createId("foo"));
    Assertions.assertEquals(1, c2.count());
  }

  @Test
  public void timerRecord() {
    Timer t = registry.timer("foo");
    Assertions.assertEquals(0, t.count());
    Assertions.assertEquals(0, t.totalTime());
    t.record(42, TimeUnit.SECONDS);
    Assertions.assertEquals(1, t.count());
    Assertions.assertEquals(TimeUnit.SECONDS.toNanos(42), t.totalTime());
  }

  @Test
  public void timerRecordRunnable() {
    Timer t = registry.timer("foo");
    t.record((Runnable) () -> clock.add(42, TimeUnit.SECONDS));
    Assertions.assertEquals(1, t.count());
    Assertions.assertEquals(TimeUnit.SECONDS.toNanos(42), t.totalTime());
  }

  @Test
  public void timerRecordCallable() throws Exception {
    Timer t = registry.timer("foo");
    t.record(() -> clock.add(42, TimeUnit.SECONDS));
    Assertions.assertEquals(1, t.count());
    Assertions.assertEquals(TimeUnit.SECONDS.toNanos(42), t.totalTime());
  }

  @Test
  public void timerMeasure() {
    Timer t = registry.timer("foo");
    t.record(42, TimeUnit.SECONDS);
    int i = 0;
    for (Measurement m : t.measure()) {
      ++i;
      Assertions.assertEquals("foo", m.id().name());
      switch (Utils.getTagValue(m.id(), "statistic")) {
        case "count":
          Assertions.assertEquals(1.0, m.value(), 1e-12);
          break;
        case "total":
          Assertions.assertEquals(42.0, m.value(), 1e-12);
          break;
        case "max":
          Assertions.assertEquals(42.0, m.value(), 1e-12);
          break;
        default:
          Assertions.fail("invalid statistic for measurment: " + m);
      }
    }
    Assertions.assertEquals(3, i);
  }

  @Test
  public void timerGet() {
    Timer t1 = registry.timer("foo");
    t1.record(1, TimeUnit.SECONDS);

    Timer t2 = (Timer) registry.get(registry.createId("foo"));
    Assertions.assertEquals(1, t2.count());
  }

  @Test
  public void summaryRecord() {
    DistributionSummary s = registry.distributionSummary("foo");
    Assertions.assertEquals(0, s.count());
    Assertions.assertEquals(0, s.totalAmount());
    s.record(42);
    Assertions.assertEquals(1, s.count());
    Assertions.assertEquals(42, s.totalAmount());
  }

  @Test
  public void summaryMeasure() {
    DistributionSummary s = registry.distributionSummary("foo");
    s.record(42);
    int i = 0;
    for (Measurement m : s.measure()) {
      ++i;
      Assertions.assertEquals("foo", m.id().name());
      switch (Utils.getTagValue(m.id(), "statistic")) {
        case "count":
          Assertions.assertEquals(1.0, m.value(), 1e-12);
          break;
        case "total":
          Assertions.assertEquals(42.0, m.value(), 1e-12);
          break;
        case "max":
          Assertions.assertEquals(42.0, m.value(), 1e-12);
          break;
        default:
          Assertions.fail("invalid statistic for measurment: " + m);
      }
    }
    Assertions.assertEquals(3, i);
  }

  @Test
  public void summaryGet() {
    DistributionSummary s1 = registry.distributionSummary("foo");
    s1.record(1);

    DistributionSummary s2 = (DistributionSummary) registry.get(registry.createId("foo"));
    Assertions.assertEquals(1, s2.count());
  }

  @Test
  public void gaugeSet() {
    Gauge g = registry.gauge("foo");
    Assertions.assertTrue(Double.isNaN(g.value()));
    g.set(42.0);
    Assertions.assertEquals(42.0, g.value(), 1e-12);
    g.set(20.0);
    Assertions.assertEquals(20.0, g.value(), 1e-12);
  }

  @Test
  public void gaugeMeasure() {
    Gauge g = registry.gauge("foo");
    g.set(42.0);
    int i = 0;
    for (Measurement m : g.measure()) {
      ++i;
      Assertions.assertEquals("foo", m.id().name());
      Assertions.assertEquals(42.0, m.value(), 1e-12);
    }
    Assertions.assertEquals(1, i);
  }

  @Test
  public void gaugeGet() {
    Gauge g1 = registry.gauge("foo");
    g1.set(1.0);

    Gauge g2 = (Gauge) registry.get(registry.createId("foo"));
    Assertions.assertEquals(1.0, g2.value(), 1e-12);
  }

  @Test
  public void maxGaugeSet() {
    Gauge g = registry.maxGauge("foo");
    Assertions.assertTrue(Double.isNaN(g.value()));
    g.set(42.0);
    g.set(20.0);
    clock.addSeconds(60);
    Assertions.assertEquals(42.0, g.value(), 1e-12);
  }

  @Test
  public void unknownGet() {
    meterRegistry.more().longTaskTimer("foo").record(() -> clock.addSeconds(60));
    Assertions.assertNull(registry.get(registry.createId("foo")));
  }

  @Test
  public void iterator() {
    registry.counter("c");
    registry.timer("t");
    registry.distributionSummary("s");
    registry.gauge("g");

    Set<String> actual = new HashSet<>();
    for (Meter m : registry) {
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

  @Test
  public void patternUsingState() {
    LongTaskTimer t = LongTaskTimer.get(registry, registry.createId("foo"));
    long tid = t.start();
    clock.addSeconds(60);
    PolledMeter.update(registry);

    Gauge g = registry.gauge(registry.createId("foo").withTag(Statistic.duration));
    Assertions.assertEquals(60.0, g.value(), 1e-12);

    t.stop(tid);
    PolledMeter.update(registry);
    Assertions.assertEquals(0.0, g.value(), 1e-12);
  }
}
