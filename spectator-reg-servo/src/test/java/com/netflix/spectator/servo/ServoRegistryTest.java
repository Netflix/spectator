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

import com.netflix.servo.DefaultMonitorRegistry;
import com.netflix.servo.MonitorRegistry;
import com.netflix.spectator.api.CompositeRegistry;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.ManualClock;
import com.netflix.spectator.api.Meter;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Spectator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

public class ServoRegistryTest {

  @Test
  public void multiRegistration() {
    // Servo uses statics internally and the indended use of ServoRegistry
    // is there would be one in use at a given time. We don't want to make
    // it a singleton because that would break some existing unit tests that
    // expect isolated counts from the spectator api. This test just verifies
    // that multiple registrations can coexist in servo and will not clobber
    // each other.
    MonitorRegistry mr = DefaultMonitorRegistry.getInstance();

    ServoRegistry r1 = new ServoRegistry();
    Assertions.assertTrue(mr.getRegisteredMonitors().contains(r1));

    ServoRegistry r2 = new ServoRegistry();
    Assertions.assertTrue(mr.getRegisteredMonitors().contains(r1));
    Assertions.assertTrue(mr.getRegisteredMonitors().contains(r2));

    ServoRegistry r3 = new ServoRegistry();
    Assertions.assertTrue(mr.getRegisteredMonitors().contains(r1));
    Assertions.assertTrue(mr.getRegisteredMonitors().contains(r2));
    Assertions.assertTrue(mr.getRegisteredMonitors().contains(r3));
  }

  @Test
  public void iteratorDoesNotContainNullMeters() {
    Registry dflt = new ServoRegistry();

    boolean found = false;
    Counter counter = dflt.counter("servo.testCounter");
    for (Meter m : dflt) {
      found = m.id().equals(counter.id());
    }
    Assertions.assertTrue(found, "id could not be found in iterator");
  }

  // Reproduces: https://github.com/Netflix/spectator/issues/530
  public void globalIterator(Function<Registry, Meter> createMeter) {
    Registry dflt = new ServoRegistry();
    CompositeRegistry global = Spectator.globalRegistry();
    global.removeAll();
    global.add(dflt);

    boolean found = false;
    Id expected = createMeter.apply(dflt).id();
    for (Meter m : global) {
      found |= m.id().equals(expected);
    }
    Assertions.assertTrue(found, "id for sub-registry could not be found in global iterator");
  }

  @Test
  public void globalIteratorCounter() {
    globalIterator(r -> r.counter("servo.testCounter"));
  }

  @Test
  public void globalIteratorGauge() {
    globalIterator(r -> r.gauge("servo.testGauge"));
  }

  @Test
  public void globalIteratorTimer() {
    globalIterator(r -> r.timer("servo.testTimer"));
  }

  @Test
  public void globalIteratorDistSummary() {
    globalIterator(r -> r.distributionSummary("servo.testDistSummary"));
  }

  @Test
  public void keepNonExpired() {
    ManualClock clock = new ManualClock();
    ServoRegistry registry = new ServoRegistry(clock);
    registry.counter("test").increment();
    Assertions.assertEquals(1, registry.getMonitors().size());
    Assertions.assertEquals(1, registry.counters().count());
  }

  @Test
  public void removesExpired() {
    ManualClock clock = new ManualClock();
    ServoRegistry registry = new ServoRegistry(clock);
    registry.counter("test").increment();
    clock.setWallTime(60000 * 30);
    Assertions.assertEquals(0, registry.getMonitors().size());
    Assertions.assertEquals(0, registry.counters().count());
  }

  @Test
  public void resurrectExpiredAndIncrement() {
    ManualClock clock = new ManualClock();
    ServoRegistry registry = new ServoRegistry(clock);
    Counter c = registry.counter("test");

    clock.setWallTime(60000 * 30);
    registry.getMonitors();

    Assertions.assertTrue(c.hasExpired());

    c.increment();
    Assertions.assertEquals(1, c.count());
    Assertions.assertEquals(1, registry.counter("test").count());

    clock.setWallTime(60000 * 60);
    registry.getMonitors();

    Assertions.assertTrue(c.hasExpired());

    c.increment();
    Assertions.assertEquals(1, c.count());
    Assertions.assertEquals(1, registry.counter("test").count());
  }

}
