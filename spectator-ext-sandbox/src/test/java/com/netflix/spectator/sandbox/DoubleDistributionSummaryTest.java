/**
 * Copyright 2014 Netflix, Inc.
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
package com.netflix.spectator.sandbox;

import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.ManualClock;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Tag;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DoubleDistributionSummaryTest {

  private final ManualClock clock = new ManualClock();

  private Registry registry = new DefaultRegistry();

  private DoubleDistributionSummary newInstance() {
    clock.setWallTime(0L);
    return new DoubleDistributionSummary(clock, registry.createId("foo"), 60000);
  }

  private String get(Id id, String key) {
    for (Tag t : id.tags()) {
      if (key.equals(t.key())) {
        return t.value();
      }
    }
    return null;
  }

  @Test
  public void testInit() {
    DoubleDistributionSummary t = newInstance();
    Assert.assertEquals(t.count(), 0L);
    Assert.assertEquals(t.totalAmount(), 0.0, 1e-12);
  }

  @Test
  public void testRecord() {
    DoubleDistributionSummary t = newInstance();
    t.record(42.0);
    Assert.assertEquals(t.count(), 1L);
    Assert.assertEquals(t.totalAmount(), 42.0, 1e-12);
  }

  @Test
  public void testMeasureNotEnoughTime() {
    DoubleDistributionSummary t = newInstance();
    t.record(42.0);
    clock.setWallTime(500L);
    int c = 0;
    for (Measurement m : t.measure()) {
      ++c;
    }
    Assert.assertEquals(0L, c);
  }

  @Test
  public void testMeasure() {
    DoubleDistributionSummary t = newInstance();
    t.record(42.0);
    clock.setWallTime(65000L);
    for (Measurement m : t.measure()) {
      Assert.assertEquals(m.timestamp(), 65000L);
      switch (get(m.id(), "statistic")) {
        case "count":
          Assert.assertEquals(m.value(), 1.0 / 65.0, 1e-12);
          break;
        case "totalAmount":
          Assert.assertEquals(m.value(), 42.0 / 65.0, 1e-12);
          break;
        case "totalOfSquares":
          Assert.assertEquals(m.value(), 42.0 * 42.0 / 65.0, 1e-12);
          break;
        case "max":
          Assert.assertEquals(m.value(), 42.0, 1e-12);
          break;
        default:
          Assert.fail("unexpected id: " + m.id());
          break;
      }
    }
  }

  private double stddev(double[] values) {
    double t = 0.0;
    double t2 = 0.0;
    double n = 0.0;
    for (double v : values) {
      t += v;
      t2 += v * v;
      n += 1.0;
    }
    return Math.sqrt((n * t2 - t * t) / (n * n));
  }

  @Test
  public void testMeasureZeroToOne() {
    double[] values = { 0.1, 0.2, 0.7, 0.8, 0.1, 0.4, 0.6, 0.9, 0.1, 1.0, 0.0, 0.5, 0.4 };
    DoubleDistributionSummary s = newInstance();
    for (double v : values) {
      s.record(v);
    }
    clock.setWallTime(65000L);

    double t = 0.0;
    double t2 = 0.0;
    double n = 0.0;
    double max = 0.0;
    for (Measurement m : s.measure()) {
      switch (get(m.id(), "statistic")) {
        case "count":          n = m.value();   break;
        case "totalAmount":    t = m.value();   break;
        case "totalOfSquares": t2 = m.value();  break;
        case "max":            max = m.value(); break;
        default:
          Assert.fail("unexpected id: " + m.id());
          break;
      }
    }

    Assert.assertEquals(1.0, max, 1e-12);
    Assert.assertEquals(stddev(values), Math.sqrt((n * t2 - t * t) / (n * n)), 1e-12);
  }

  @Test
  public void testRegister() {
    DoubleDistributionSummary t = newInstance();
    registry.register(t);
    t.record(42.0);
    clock.setWallTime(65000L);
    for (Measurement m : registry.get(t.id()).measure()) {
      Assert.assertEquals(m.timestamp(), 65000L);
      switch (get(m.id(), "statistic")) {
        case "count":
          Assert.assertEquals(m.value(), 1.0 / 65.0, 1e-12);
          break;
        case "totalAmount":
          Assert.assertEquals(m.value(), 42.0 / 65.0, 1e-12);
          break;
        case "totalOfSquares":
          Assert.assertEquals(m.value(), 42.0 * 42.0 / 65.0, 1e-12);
          break;
        case "max":
          Assert.assertEquals(m.value(), 42.0, 1e-12);
          break;
        default:
          Assert.fail("unexpected id: " + m.id());
          break;
      }
    }
  }

  @Test
  public void staticGet() {
    Id id = registry.createId("foo");
    DoubleDistributionSummary t = DoubleDistributionSummary.get(registry, id);
    Assert.assertSame(t, DoubleDistributionSummary.get(registry, id));
    Assert.assertNotNull(registry.get(id));
  }

}
