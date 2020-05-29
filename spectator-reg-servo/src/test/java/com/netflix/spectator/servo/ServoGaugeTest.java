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
package com.netflix.spectator.servo;

import com.netflix.servo.monitor.Monitor;
import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.ManualClock;
import com.netflix.spectator.api.Measurement;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ServoGaugeTest {

  private final ManualClock clock = new ManualClock();

  private Gauge newGauge(String name) {
    final ServoRegistry r = Servo.newRegistry(clock);
    return r.newGauge(r.createId(name));
  }

  @BeforeEach
  public void before() {
    clock.setWallTime(0L);
    clock.setMonotonicTime(0L);
  }

  @Test
  public void testInit() {
    Gauge g = newGauge("foo");
    Assertions.assertEquals(g.value(), Double.NaN, 1e-12);
    g.set(1.0);
    Assertions.assertEquals(g.value(), 1.0, 1e-12);
  }

  @Test
  public void testGet() {
    final ServoRegistry r = Servo.newRegistry(clock);
    Gauge g = r.gauge(r.createId("foo"));
    g.set(1.0);

    Assertions.assertEquals(1, r.getMonitors().size());
    Assertions.assertEquals(1.0, (Double) r.getMonitors().get(0).getValue(0), 1e-12);
  }

  @Test
  public void expiration() {
    final long initTime = TimeUnit.MINUTES.toMillis(30);
    final long fifteenMinutes = TimeUnit.MINUTES.toMillis(15);

    // Not expired on init, wait for activity to mark as active
    clock.setWallTime(initTime);
    Gauge g = newGauge("foo");
    Assertions.assertFalse(g.hasExpired());
    g.set(42.0);
    Assertions.assertFalse(g.hasExpired());
    Assertions.assertEquals(g.value(), 42.0, 1e-12);

    // Expires with inactivity
    clock.setWallTime(initTime + fifteenMinutes);
    Assertions.assertFalse(g.hasExpired());

    // Expires with inactivity
    clock.setWallTime(initTime + fifteenMinutes + 1);
    Assertions.assertEquals(g.value(), Double.NaN, 1e-12);
    Assertions.assertTrue(g.hasExpired());

    // Activity brings it back
    g.set(1.0);
    Assertions.assertEquals(g.value(), 1.0, 1e-12);
    Assertions.assertFalse(g.hasExpired());
  }

  @Test
  public void hasGaugeType() {
    final ServoRegistry r = Servo.newRegistry(clock);
    Gauge g = r.gauge(r.createId("foo"));
    g.set(1.0);

    Map<String, String> tags = r.getMonitors().get(0).getConfig().getTags().asMap();
    Assertions.assertEquals("GAUGE", tags.get("type"));
  }

  @Test
  public void measure() {
    final ServoRegistry r = Servo.newRegistry(clock);
    Gauge g = r.gauge(r.createId("foo"));
    g.set(1.0);

    Iterator<Measurement> ms = g.measure().iterator();
    Assertions.assertTrue(ms.hasNext());
    Measurement m = ms.next();
    Assertions.assertFalse(ms.hasNext());
    Assertions.assertEquals("foo", m.id().name());
    Assertions.assertEquals(1.0, 1.0, 1e-12);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void hasStatistic() {
    List<Monitor<?>> ms = new ArrayList<>();
    Gauge g = newGauge("foo");
    ((ServoGauge) g).addMonitors(ms);
    Assertions.assertEquals(1, ms.size());
    Assertions.assertEquals("gauge", ms.get(0).getConfig().getTags().getValue("statistic"));
  }

}
