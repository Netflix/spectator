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

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.ManualClock;
import com.netflix.spectator.api.Registry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class MonotonicCounterTest {
  private final ManualClock clock = new ManualClock();
  private final Registry registry = new DefaultRegistry(clock);
  private final Id id = registry.createId("test");

  private void update() {
    PolledMeter.update(registry);
  }

  @Test
  public void usingAtomicLong() {
    AtomicLong count = new AtomicLong();
    AtomicLong c = PolledMeter.using(registry).withId(id).monitorMonotonicCounter(count);
    Assertions.assertSame(count, c);

    Counter counter = registry.counter(id);
    update();
    Assertions.assertEquals(0L, counter.count());

    c.incrementAndGet();
    update();
    Assertions.assertEquals(1L, counter.count());

    c.addAndGet(42);
    update();
    Assertions.assertEquals(43L, counter.count());
  }

  @Test
  public void usingLongAdder() {
    LongAdder count = new LongAdder();
    LongAdder c = PolledMeter.using(registry).withId(id).monitorMonotonicCounter(count);
    Assertions.assertSame(count, c);

    Counter counter = registry.counter(id);
    update();
    Assertions.assertEquals(0L, counter.count());

    c.increment();
    update();
    Assertions.assertEquals(1L, counter.count());

    c.add(42);
    update();
    Assertions.assertEquals(43L, counter.count());
  }

  @Test
  public void nonMonotonicUpdates() {
    AtomicLong count = new AtomicLong();
    AtomicLong c = PolledMeter.using(registry).withId(id).monitorMonotonicCounter(count);

    Counter counter = registry.counter(id);
    update();
    Assertions.assertEquals(0L, counter.count());

    c.set(42L);
    update();
    Assertions.assertEquals(42L, counter.count());

    // Should not update the counter because it is lower, but must update
    // the previous recorded value
    c.set(21L);
    update();
    Assertions.assertEquals(42L, counter.count());

    // Make sure a subsequent increase is detected
    c.set(23L);
    update();
    Assertions.assertEquals(44L, counter.count());
  }

  @Test
  public void expire() throws Exception {
    WeakReference<LongAdder> ref = new WeakReference<>(
      PolledMeter.using(registry).withId(id).monitorMonotonicCounter(new LongAdder()));
    while (ref.get() != null) {
      System.gc();
    }

    Assertions.assertEquals(1, registry.state().size());
    update();
    Assertions.assertEquals(0, registry.state().size());
  }

  @Test
  public void removeGauge() throws Exception {
    LongAdder v = PolledMeter.using(registry).withId(id).monitorMonotonicCounter(new LongAdder());
    Assertions.assertEquals(1, registry.state().size());
    PolledMeter.remove(registry, id);
    Assertions.assertEquals(0, registry.state().size());
  }

  @Test
  public void removeOtherType() throws Exception {
    LongTaskTimer t = LongTaskTimer.get(registry, id);
    Assertions.assertEquals(3, registry.state().size());
    PolledMeter.remove(registry, id);
    Assertions.assertEquals(3, registry.state().size());
  }
}
