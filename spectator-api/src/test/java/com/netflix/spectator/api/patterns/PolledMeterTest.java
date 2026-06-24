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
import com.netflix.spectator.impl.AtomicDouble;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

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
  public void monitorMonotonicCounterDouble() {
    Registry r = new DefaultRegistry(Clock.SYSTEM, p -> null);
    Id id = r.createId("test");

    AtomicDouble v = PolledMeter.using(r)
        .withName("test")
        .monitorMonotonicCounterDouble(new AtomicDouble(), AtomicDouble::doubleValue);

    Assertions.assertEquals(0.0, r.counter(id).actualCount());

    v.set(42.5);
    PolledMeter.update(r);
    Assertions.assertEquals(42.5, r.counter(id).actualCount());

    // No change, value unexpectedly decreased
    v.set(1.0);
    PolledMeter.update(r);
    Assertions.assertEquals(42.5, r.counter(id).actualCount());

    v.set(5.0);
    PolledMeter.update(r);
    Assertions.assertEquals(46.5, r.counter(id).actualCount());
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

  @Test
  public void monitorValueLongAdder() {
    final Registry r = new DefaultRegistry(Clock.SYSTEM, p -> null);
    final Id id = r.createId("test");

    final LongAdder v = PolledMeter.using(r)
            .withName("test")
            .monitorValue(new LongAdder());

    v.increment();
    PolledMeter.update(r);
    Assertions.assertEquals(1.0, r.gauge(id).value());

    v.decrement();
    PolledMeter.update(r);
    Assertions.assertEquals(0.0, r.gauge(id).value());

    v.add(50);
    PolledMeter.update(r);
    Assertions.assertEquals(50.0, r.gauge(id).value());
  }

  @Test
  public void monitorSizeOfList() {
    final Registry r = new DefaultRegistry(Clock.SYSTEM, p -> null);
    final Id id = r.createId("test");

    final List<String> list = PolledMeter.using(r)
            .withName("test")
            .monitorSize(Collections.synchronizedList(new ArrayList<String>()));

    list.add("a");
    PolledMeter.update(r);
    Assertions.assertEquals(1.0, r.gauge(id).value());

    list.add("b");
    PolledMeter.update(r);
    Assertions.assertEquals(2.0, r.gauge(id).value());

    list.clear();
    PolledMeter.update(r);
    Assertions.assertEquals(0.0, r.gauge(id).value());
  }

  @Test
  public void monitorSizeOfMap() {
    final Registry r = new DefaultRegistry(Clock.SYSTEM, p -> null);
    final Id id = r.createId("test");

    final Map<String, String> map = PolledMeter.using(r)
            .withName("test")
            .monitorSize(new ConcurrentHashMap<String, String>());

    map.put("a", "a-value");
    PolledMeter.update(r);
    Assertions.assertEquals(1.0, r.gauge(id).value());

    map.put("b", "b-value");
    PolledMeter.update(r);
    Assertions.assertEquals(2.0, r.gauge(id).value());

    map.remove("a");
    map.remove("b");
    PolledMeter.update(r);
    Assertions.assertEquals(0.0, r.gauge(id).value());
  }

  @Test
  public void removeAllClearsPolledState() {
    Registry r = new DefaultRegistry();

    PolledMeter.using(r).withName("gauge").monitorValue(new AtomicLong(1));
    PolledMeter.using(r).withName("counter").monitorMonotonicCounter(new AtomicLong(1));
    Assertions.assertFalse(r.state().isEmpty());

    PolledMeter.removeAll(r);
    Assertions.assertTrue(r.state().isEmpty());
  }

  @Test
  public void removeAllLeavesNonPolledStateUntouched() {
    Registry r = new DefaultRegistry();

    // State entries that are not polled meters must be left alone.
    Id marker = r.createId("marker");
    r.state().put(marker, new Object());

    Id gauge = r.createId("gauge");
    PolledMeter.using(r).withId(gauge).monitorValue(new AtomicLong(1));

    PolledMeter.removeAll(r);
    Assertions.assertFalse(r.state().containsKey(gauge));
    Assertions.assertTrue(r.state().containsKey(marker));
  }

  @Test
  public void cleanupActionRunsOnRemove() {
    Registry r = new DefaultRegistry();
    Id id = r.createId("test");
    AtomicLong closed = new AtomicLong();

    PolledMeter.using(r).withId(id)
        .withCleanupAction(closed::incrementAndGet)
        .monitorValue(new AtomicLong(1));

    Assertions.assertEquals(0, closed.get());
    PolledMeter.remove(r, id);
    Assertions.assertEquals(1, closed.get());
  }

  @Test
  public void cleanupActionRunsOnRemoveAll() {
    Registry r = new DefaultRegistry();
    AtomicLong closed = new AtomicLong();

    // Exercise the monotonic-counter path so the action is threaded through CounterState too.
    PolledMeter.using(r).withName("test")
        .withCleanupAction(closed::incrementAndGet)
        .monitorMonotonicCounter(new AtomicLong(1));

    PolledMeter.removeAll(r);
    Assertions.assertEquals(1, closed.get());
  }

  @Test
  public void cleanupActionExceptionDoesNotPropagate() {
    Registry r = new DefaultRegistry();
    Id id = r.createId("test");

    PolledMeter.using(r).withId(id)
        .withCleanupAction(() -> {
          throw new IllegalStateException("boom");
        })
        .monitorValue(new AtomicLong(1));

    // Must not throw, and the meter state should still be cleared.
    PolledMeter.remove(r, id);
    Assertions.assertTrue(r.state().isEmpty());
  }

  @Test
  public void multipleCleanupActionsForSameId() {
    Registry r = new DefaultRegistry();
    Id id = r.createId("test");
    AtomicLong closed = new AtomicLong();

    PolledMeter.using(r).withId(id)
        .withCleanupAction(closed::incrementAndGet)
        .monitorValue(new AtomicLong(1));
    PolledMeter.using(r).withId(id)
        .withCleanupAction(closed::incrementAndGet)
        .monitorValue(new AtomicLong(2));

    PolledMeter.remove(r, id);
    Assertions.assertEquals(2, closed.get());
  }

  @Test
  public void registryCloseCancelsPolledMetersAndPollTasks() {
    Registry r = new DefaultRegistry();
    AtomicLong closed = new AtomicLong();

    PolledMeter.using(r).withName("gauge")
        .withCleanupAction(closed::incrementAndGet)
        .monitorValue(new AtomicLong(1));
    ScheduledFuture<?> poll = PolledMeter.poll(r, () -> { });
    Assertions.assertFalse(r.state().isEmpty());

    r.close();

    Assertions.assertTrue(r.state().isEmpty());
    Assertions.assertTrue(poll.isCancelled());
    Assertions.assertEquals(1, closed.get());
  }

  @Test
  public void registryCloseViaTryWithResources() {
    AtomicLong closed = new AtomicLong();
    try (Registry r = new DefaultRegistry()) {
      PolledMeter.using(r).withName("gauge")
          .withCleanupAction(closed::incrementAndGet)
          .monitorValue(new AtomicLong(1));
    }
    Assertions.assertEquals(1, closed.get());
  }

  @Test
  public void registryCloseIsIdempotent() {
    Registry r = new DefaultRegistry();
    AtomicLong closed = new AtomicLong();

    PolledMeter.using(r).withName("gauge")
        .withCleanupAction(closed::incrementAndGet)
        .monitorValue(new AtomicLong(1));

    r.close();
    r.close();
    Assertions.assertEquals(1, closed.get());
  }

  @Test
  public void registryCloseClearsMeters() {
    Registry r = new DefaultRegistry();
    PolledMeter.using(r).withName("gauge").monitorValue(new AtomicLong(1));
    Assertions.assertTrue(r.iterator().hasNext());

    r.close();
    Assertions.assertFalse(r.iterator().hasNext());
    Assertions.assertTrue(r.state().isEmpty());
  }

  @Test
  public void cleanupActionExceptionDoesNotPropagateOnClose() {
    Registry r = new DefaultRegistry();
    PolledMeter.using(r).withName("gauge")
        .withCleanupAction(() -> {
          throw new IllegalStateException("boom");
        })
        .monitorValue(new AtomicLong(1));

    // close() must not propagate the exception and must still clear state.
    r.close();
    Assertions.assertTrue(r.state().isEmpty());
  }

  @Test
  public void resetCancelsPolledMeters() {
    DefaultRegistry r = new DefaultRegistry();
    AtomicLong closed = new AtomicLong();

    PolledMeter.using(r).withName("gauge")
        .withCleanupAction(closed::incrementAndGet)
        .monitorValue(new AtomicLong(1));

    r.reset();
    Assertions.assertEquals(1, closed.get());
    Assertions.assertTrue(r.state().isEmpty());
  }

  @Test
  public void removeAllCancelsPollTask() {
    Registry r = new DefaultRegistry();

    ScheduledFuture<?> future = PolledMeter.poll(r, () -> { });
    Assertions.assertFalse(future.isCancelled());
    Assertions.assertFalse(r.state().isEmpty());

    PolledMeter.removeAll(r);
    Assertions.assertTrue(future.isCancelled());
    Assertions.assertTrue(r.state().isEmpty());
  }

  @Test
  public void pollFutureCancelRemovesState() {
    Registry r = new DefaultRegistry();

    ScheduledFuture<?> future = PolledMeter.poll(r, () -> { });
    Assertions.assertFalse(r.state().isEmpty());

    future.cancel(true);
    Assertions.assertTrue(future.isCancelled());
    Assertions.assertTrue(r.state().isEmpty());
  }

  @Test
  public void cleanupActionRunsAtMostOnce() {
    Registry r = new DefaultRegistry();
    Id id = r.createId("test");
    AtomicLong closed = new AtomicLong();

    PolledMeter.using(r).withId(id)
        .withCleanupAction(closed::incrementAndGet)
        .monitorValue(new AtomicLong(1));

    PolledMeter.remove(r, id);
    PolledMeter.removeAll(r);
    Assertions.assertEquals(1, closed.get());
  }
}
