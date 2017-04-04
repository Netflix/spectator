/*
 * Copyright 2014-2017 Netflix, Inc.
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

import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.ManualClock;
import com.netflix.spectator.api.Registry;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@RunWith(JUnit4.class)
public class ServoGaugeTest {

  private final ManualClock clock = new ManualClock();

  private Gauge newGauge(String name) {
    final Registry r = new ServoRegistry(clock);
    return r.gauge(r.createId(name));
  }

  @Before
  public void before() {
    clock.setWallTime(0L);
    clock.setMonotonicTime(0L);
  }

  @Test
  public void testInit() {
    Gauge g = newGauge("foo");
    Assert.assertEquals(g.value(), Double.NaN, 1e-12);
    g.set(1.0);
    Assert.assertEquals(g.value(), 1.0, 1e-12);
  }

  @Test
  public void testGet() {
    final ServoRegistry r = new ServoRegistry(clock);
    Gauge g = r.gauge(r.createId("foo"));
    g.set(1.0);

    Assert.assertEquals(1, r.getMonitors().size());
    Assert.assertEquals(1.0, (Double) r.getMonitors().get(0).getValue(0), 1e-12);
  }

  @Test
  public void expiration() {
    final long initTime = TimeUnit.MINUTES.toMillis(30);
    final long fifteenMinutes = TimeUnit.MINUTES.toMillis(15);

    // Expired on init, wait for activity to mark as active
    clock.setWallTime(initTime);
    Gauge g = newGauge("foo");
    Assert.assertTrue(g.hasExpired());
    g.set(42.0);
    Assert.assertFalse(g.hasExpired());
    Assert.assertEquals(g.value(), 42.0, 1e-12);

    // Expires with inactivity
    clock.setWallTime(initTime + fifteenMinutes);
    Assert.assertFalse(g.hasExpired());

    // Expires with inactivity
    clock.setWallTime(initTime + fifteenMinutes + 1);
    Assert.assertEquals(g.value(), Double.NaN, 1e-12);
    Assert.assertTrue(g.hasExpired());

    // Activity brings it back
    g.set(1.0);
    Assert.assertEquals(g.value(), 1.0, 1e-12);
    Assert.assertFalse(g.hasExpired());
  }

  @Test
  public void hasGaugeType() {
    final ServoRegistry r = new ServoRegistry(clock);
    Gauge g = r.gauge(r.createId("foo"));
    g.set(1.0);

    Map<String, String> tags = r.getMonitors().get(0).getConfig().getTags().asMap();
    Assert.assertEquals("GAUGE", tags.get("type"));
  }

}
