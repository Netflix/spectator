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
package com.netflix.spectator.api.patterns;

import com.netflix.spectator.api.*;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

@RunWith(JUnit4.class)
public class MonotonicCounterTest {
  private final ManualClock clock = new ManualClock();
  private final Registry registry = new DefaultRegistry(clock);
  private final Id id = registry.createId("test");

  private void update() {
    registry.state().forEach((id, obj) -> {
      if (obj instanceof MonotonicCounter.Tuple<?>) {
        MonotonicCounter.updateCounter(registry, ((MonotonicCounter.Tuple<?>) obj));
      }
    });
  }

  @Test
  public void usingAtomicLong() {
    AtomicLong count = new AtomicLong();
    AtomicLong c = MonotonicCounter.monitorNumber(registry, id, count);
    Assert.assertSame(count, c);

    Counter counter = registry.counter(id);
    update();
    Assert.assertEquals(0L, counter.count());

    c.incrementAndGet();
    update();
    Assert.assertEquals(1L, counter.count());

    c.addAndGet(42);
    update();
    Assert.assertEquals(43L, counter.count());
  }

  @Test
  public void usingLongAdder() {
    LongAdder count = new LongAdder();
    LongAdder c = MonotonicCounter.monitorNumber(registry, id, count);
    Assert.assertSame(count, c);

    Counter counter = registry.counter(id);
    update();
    Assert.assertEquals(0L, counter.count());

    c.increment();
    update();
    Assert.assertEquals(1L, counter.count());

    c.add(42);
    update();
    Assert.assertEquals(43L, counter.count());
  }

  @Test
  public void nonMonotonicUpdates() {
    AtomicLong count = new AtomicLong();
    AtomicLong c = MonotonicCounter.monitorNumber(registry, id, count);

    Counter counter = registry.counter(id);
    update();
    Assert.assertEquals(0L, counter.count());

    c.set(42L);
    update();
    Assert.assertEquals(42L, counter.count());

    // Should not update the counter because it is lower, but must update
    // the previous recorded value
    c.set(21L);
    update();
    Assert.assertEquals(42L, counter.count());

    // Make sure a subsequent increase is detected
    c.set(23L);
    update();
    Assert.assertEquals(44L, counter.count());
  }

  @Test
  public void expire() throws Exception {
    WeakReference<LongAdder> ref = new WeakReference<>(
      MonotonicCounter.monitorNumber(registry, id, new LongAdder()));
    while (ref.get() != null) {
      System.gc();
    }

    Assert.assertEquals(1, registry.state().size());
    update();
    Assert.assertEquals(0, registry.state().size());
  }


}
