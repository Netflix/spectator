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
package com.netflix.spectator.atlas;

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.ManualClock;
import com.netflix.spectator.api.Meter;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * What a reference the caller holds does on the update path. It has to notice that the registry
 * removed its meter, and it has to work that out without reading the wall clock.
 */
public class HeldReferenceResolutionTest {

  private static final long TTL = 900_000L;
  private static final long LWC_STEP = 5_000L;

  /**
   * Clock that records how many times the time was read. Both methods count, so switching the
   * update path from the wall clock to the monotonic one would still show up as a read rather
   * than quietly satisfying the assertion.
   */
  private static final class CountingClock implements Clock {
    private final ManualClock delegate = new ManualClock();
    final AtomicLong reads = new AtomicLong();

    @Override public long wallTime() {
      reads.incrementAndGet();
      return delegate.wallTime();
    }

    @Override public long monotonicTime() {
      reads.incrementAndGet();
      return delegate.monotonicTime();
    }

    void setWallTime(long t) {
      delegate.setWallTime(t);
    }
  }

  private CountingClock clock;
  private AtlasRegistry registry;

  private AtlasConfig newConfig() {
    Map<String, String> props = new LinkedHashMap<>();
    props.put("atlas.enabled", "false");
    props.put("atlas.meterTTL", Duration.ofMillis(TTL).toString());
    // Pinned rather than left to the default, because completedCount rolls the clock forward by
    // exactly this much to read the previous interval.
    props.put("atlas.lwc.step", Duration.ofMillis(LWC_STEP).toString());

    return new AtlasConfig() {
      @Override public String get(String k) {
        return props.get(k);
      }

      @Override public Registry debugRegistry() {
        return new NoopRegistry();
      }
    };
  }

  @BeforeEach
  public void init() {
    clock = new CountingClock();
    registry = new AtlasRegistry(clock, newConfig());
  }

  /** Counters report the previous completed interval, so roll into the next one to read it. */
  private double completedCount(Id id, long now) {
    clock.setWallTime(now + LWC_STEP);
    return ((Counter) registry.get(id)).actualCount();
  }

  @Test
  public void heldCounterRecoversAfterItsMeterIsRemoved() {
    Id id = registry.createId("test");
    Counter held = registry.counter(id);
    held.increment();
    Meter first = registry.get(id);

    clock.setWallTime(TTL + 1);
    registry.removeExpiredMeters();
    Assertions.assertNull(registry.get(id));

    // The update has to land on a meter the registry reports, not on the removed instance.
    held.increment();
    Meter second = registry.get(id);
    Assertions.assertNotNull(second, "the update did not register a new meter");
    Assertions.assertNotSame(first, second);
    Assertions.assertEquals(1.0, completedCount(id, TTL + 1), 1e-12,
        "the update did not land on the registered meter");
  }

  @Test
  public void updatesDoNotReadTheClockToCheckTheMeterIsCurrent() {
    Id id = registry.createId("test");
    Counter held = registry.counter(id);
    held.increment();

    // AtlasCounter.add() reads the clock once, to stamp the value and record activity. Checking
    // whether the meter is still the registered one must not add a second read, which is what
    // asking hasExpired() would cost.
    long start = clock.reads.get();
    for (int i = 0; i < 1000; ++i) {
      held.increment();
    }
    long reads = clock.reads.get() - start;

    Assertions.assertEquals(1000, reads,
        "expected one clock read per update, for recording the value");
  }

  @Test
  public void updatesThroughACompositeDoNotReadTheClockEither() {
    // What Spectator.globalRegistry() hands out: a wrapper around the sub-registry's own
    // wrapper. The outer one has to get the flag passed through, or the nesting costs a clock
    // read per update and the cheap path never reaches the common entry point.
    com.netflix.spectator.api.CompositeRegistry composite =
        com.netflix.spectator.api.Spectator.globalRegistry();
    composite.removeAll();
    composite.add(registry);
    try {
      Counter held = composite.counter(composite.createId("test.composite"));
      held.increment();

      long start = clock.reads.get();
      for (int i = 0; i < 1000; ++i) {
        held.increment();
      }
      long reads = clock.reads.get() - start;

      Assertions.assertEquals(1000, reads,
          "expected one clock read per update through the composite, for recording the value");
    } finally {
      composite.removeAll();
    }
  }

  @Test
  public void resolvingAgainDoesNotDependOnTheTtl() {
    Id id = registry.createId("test");
    Counter held = registry.counter(id);
    held.increment();

    // Remove the meter without any time passing, so nothing is expired: only the removal itself
    // can tell the held reference to look the meter up again.
    Iterator<Meter> it = registry.iterator();
    while (it.hasNext()) {
      if (it.next().id().equals(id)) {
        it.remove();
        break;
      }
    }
    Assertions.assertNull(registry.get(id));

    held.increment();
    Assertions.assertNotNull(registry.get(id), "held reference never noticed the removal");
    Assertions.assertEquals(1.0, completedCount(id, 0L), 1e-12,
        "the update did not land on the registered meter");
  }
}
