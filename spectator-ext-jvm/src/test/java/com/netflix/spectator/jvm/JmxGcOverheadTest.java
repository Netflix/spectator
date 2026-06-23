/*
 * Copyright 2014-2026 Netflix, Inc.
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
package com.netflix.spectator.jvm;

import com.netflix.spectator.api.*;
import com.netflix.spectator.api.patterns.PolledMeter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

import static org.junit.jupiter.api.Assertions.*;

public class JmxGcOverheadTest {

  @Test
  @EnabledForJreRange(min = JRE.JAVA_26)
  public void gcOverheadMeterRegistered() {
    Registry registry = new DefaultRegistry();
    Jmx.registerStandardMXBeans(registry);
    Meter meter = registry.get(Id.create("jvm.gc.overhead"));
    assertNotNull(meter, "jvm.gc.overhead should be registered");
  }

  @Test
  @EnabledForJreRange(min = JRE.JAVA_26)
  public void gcOverheadMeterMeasure() {
    Registry registry = new DefaultRegistry();
    Jmx.registerStandardMXBeans(registry);
    // First poll establishes the baseline, value will be NaN
    PolledMeter.update(registry);
    // Trigger GC activity between polls
    System.gc();
    // Second poll computes the delta from the baseline
    PolledMeter.update(registry);
    assertTrue(registry.gauge(Id.create("jvm.gc.overhead")).value() > 0);
  }

  @Test
  public void memoryPoolMetersRegistered() {
    Registry registry = new DefaultRegistry();
    Jmx.registerStandardMXBeans(registry);
    // Memory pools are sampled by PolledMeter.poll, which samples once at registration, so the
    // used gauges should already report the live heap usage without an explicit poll. Use
    // anyMatch rather than summing so a pool reporting NaN (null usage) can't mask a live one.
    assertTrue(
        registry.gauges()
            .filter(g -> "jvm.memory.used".equals(g.id().name()))
            .anyMatch(g -> g.value() > 0.0),
        "jvm.memory.used should report a positive value for at least one pool");
    assertTrue(
        registry.gauges().anyMatch(g ->
            "jvm.memory.used".equals(g.id().name()) && hasTag(g.id(), "memtype")),
        "jvm.memory.used should carry a memtype tag");
  }

  @Test
  public void bufferPoolMetersRegistered() {
    Registry registry = new DefaultRegistry();
    Jmx.registerStandardMXBeans(registry);
    // Buffer pools use monitorValue, which samples on the poll pass, so force one.
    PolledMeter.update(registry);
    double count = registry.gauges()
        .filter(g -> "jvm.buffer.count".equals(g.id().name()))
        .mapToDouble(Gauge::value)
        .sum();
    assertFalse(Double.isNaN(count), "jvm.buffer.count should report a numeric value");
    assertTrue(count >= 0.0, "jvm.buffer.count should be non-negative");
  }

  private static boolean hasTag(Id id, String key) {
    for (Tag t : id.tags()) {
      if (key.equals(t.key())) {
        return true;
      }
    }
    return false;
  }

  @Test
  public void startMonitoringStandardMXBeansReturnsCloseable() throws Exception {
    Registry registry = new DefaultRegistry();
    AutoCloseable closeable = Jmx.startMonitoringStandardMXBeans(registry);
    assertNotNull(closeable);
    closeable.close();
  }

}
