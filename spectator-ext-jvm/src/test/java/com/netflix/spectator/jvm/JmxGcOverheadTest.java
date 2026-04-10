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

}
