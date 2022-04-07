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
package com.netflix.spectator.api.patterns;

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.RegistryConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicLong;

public class PolledMeterTest {

  private static final AtomicLong COUNTER = new AtomicLong();

  private static long testCounter() {
    return COUNTER.getAndIncrement();
  }

  private static double testValue() {
    return 42.0;
  }

  @Test
  public void monitorStaticMethodValue() {
    Registry r = new DefaultRegistry();
    Id id = r.createId("test");

    PolledMeter.using(r).withId(id).monitorStaticMethodValue(PolledMeterTest::testValue);
    PolledMeter.update(r);

    Assertions.assertEquals(42.0, r.gauge(id).value());
  }

  @Test
  public void monitorStaticMethodMonotonicCounter() {
    Registry r = new DefaultRegistry();
    Id id = r.createId("test");

    PolledMeter.using(r)
        .withId(id)
        .monitorStaticMethodMonotonicCounter(PolledMeterTest::testCounter);
    PolledMeter.update(r);
    PolledMeter.update(r);
    PolledMeter.update(r);
    PolledMeter.update(r);

    Assertions.assertEquals(4, r.counter(id).count());
  }

  @Test
  public void monitorValueNull() {
    Registry r = new DefaultRegistry(Clock.SYSTEM, p -> null);
    Id id = r.createId("test");

    PolledMeter.using(r).withId(id).<Collection<?>>monitorValue(null, Collection::size);
    PolledMeter.update(r);

    Assertions.assertEquals(Double.NaN, r.gauge(id).value());
  }

  @Test
  public void monitorValueNullPropagate() {
    RegistryConfig config = s -> s.equals("propagateWarnings") ? "true" : null;
    Registry r = new DefaultRegistry(Clock.SYSTEM, config);
    Id id = r.createId("test");

    IllegalArgumentException e = Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> PolledMeter.using(r)
            .withId(id)
            .<Collection<?>>monitorValue(null, Collection::size)
    );
    Assertions.assertTrue(e.getMessage().startsWith("obj is null"));
  }

  @Test
  public void monitorMonotonicCounterNull() {
    Registry r = new DefaultRegistry(Clock.SYSTEM, p -> null);
    Id id = r.createId("test");

    AtomicLong v = new AtomicLong(42);
    PolledMeter.using(r).withId(id).<AtomicLong>monitorMonotonicCounter(null, n -> v.get());
    PolledMeter.update(r);

    Assertions.assertEquals(0, r.counter(id).count());
  }

  @Test
  public void monitorMonotonicCounterNullPropagate() {
    RegistryConfig config = s -> s.equals("propagateWarnings") ? "true" : null;
    Registry r = new DefaultRegistry(Clock.SYSTEM, config);
    Id id = r.createId("test");

    AtomicLong v = new AtomicLong(42);
    IllegalArgumentException e = Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> PolledMeter.using(r)
            .withId(id)
            .<AtomicLong>monitorValue(null, n -> v.get())
    );
    Assertions.assertTrue(e.getMessage().startsWith("obj is null"));
  }

  @Test
  public void removeAndAddRepeatedlyCounter() {
    Registry r = new DefaultRegistry();
    Id id = r.createId("test");

    AtomicLong value = new AtomicLong();
    for (int i = 0; i < 10; ++i) {
      PolledMeter.using(r).withId(id).monitorMonotonicCounter(value);
      PolledMeter.update(r);
      value.incrementAndGet();
      PolledMeter.update(r);
      PolledMeter.remove(r, id);
    }

    Assertions.assertEquals(10, r.counter("test").count());
  }

  @Test
  public void removeAndAddRepeatedlyGauge() {
    Registry r = new DefaultRegistry();
    Id id = r.createId("test");

    AtomicLong value = new AtomicLong();
    for (int i = 0; i < 10; ++i) {
      PolledMeter.using(r).withId(id).monitorValue(value);
      value.set(i);
      PolledMeter.update(r);
      PolledMeter.remove(r, id);
    }

    Assertions.assertEquals(9.0, r.gauge("test").value(), 1e-12);
  }

  @Test
  public void poll() {
    Registry r = new DefaultRegistry();
    Gauge g = r.gauge("g");
    Counter c = r.counter("c");
    Assertions.assertTrue(Double.isNaN(g.value()));
    Assertions.assertEquals(0, c.count());

    ScheduledFuture<?> future = PolledMeter.poll(r, () -> {
      g.set(1.0);
      c.increment();
    });
    future.cancel(true);
    Assertions.assertEquals(1.0, g.value(), 1e-12);
    Assertions.assertEquals(1, c.count());
  }
}
