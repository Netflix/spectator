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
package com.netflix.spectator.servo;

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.ManualClock;
import com.netflix.spectator.api.Meter;
import com.netflix.spectator.impl.RemovableMeter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The servo meters are told when the registry removes them, so a reference the caller holds
 * finds out without reading the wall clock.
 */
public class ServoRemovalMarkingTest {

  private static final String[] NAMES = {
      "counter", "timer", "summary", "gauge", "maxGauge"
  };

  /** Clock that records how many times it was read. */
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
  private ServoRegistry registry;

  @BeforeEach
  public void init() {
    clock = new CountingClock();
    registry = Servo.newRegistry(clock);
  }

  private void populate() {
    registry.counter(NAMES[0]).increment();
    registry.timer(NAMES[1]).record(1, TimeUnit.NANOSECONDS);
    registry.distributionSummary(NAMES[2]).record(1);
    registry.gauge(NAMES[3]).set(1.0);
    registry.maxGauge(NAMES[4]).set(1.0);
  }

  private Meter[] created() {
    Meter[] ms = new Meter[NAMES.length];
    for (int i = 0; i < NAMES.length; ++i) {
      ms[i] = registry.get(registry.createId(NAMES[i]));
      Assertions.assertNotNull(ms[i], NAMES[i] + " was not registered");
    }
    return ms;
  }

  @Test
  public void everyMeterTypeIsRemovable() {
    populate();
    Set<String> seen = new HashSet<>();
    for (Meter m : created()) {
      Assertions.assertTrue(m instanceof RemovableMeter, m.getClass() + " is not removable");
      Assertions.assertFalse(((RemovableMeter) m).isRemoved(), m.id() + " is marked already");
      seen.add(m.getClass().getSimpleName());
    }
    Set<String> expected = new HashSet<>();
    expected.add("ServoCounter");
    expected.add("ServoTimer");
    expected.add("ServoDistributionSummary");
    expected.add("ServoGauge");
    expected.add("ServoMaxGauge");
    Assertions.assertEquals(expected, seen);
  }

  @Test
  public void cleanupPassMarksExpiredMeters() {
    populate();
    Meter[] before = created();

    clock.setWallTime(ServoRegistry.EXPIRATION_TIME_MILLIS + 1);
    // Driven through getMonitors(), which is the only thing in this registry that runs a
    // cleanup pass. A hand rolled loop over iterator() would only re-check the base class.
    registry.getMonitors();

    for (Meter m : before) {
      Assertions.assertTrue(((RemovableMeter) m).isRemoved(),
          m.id() + " was removed without being marked");
      Assertions.assertNull(registry.get(m.id()), m.id() + " is still registered");
    }
  }

  @Test
  public void meterSurvivingTheCleanupPassIsNotMarked() {
    populate();
    clock.setWallTime(ServoRegistry.EXPIRATION_TIME_MILLIS + 1);
    // Keep one meter active so the pass leaves it alone: the mark has to follow the removal
    // rather than every meter the pass visits.
    registry.counter(NAMES[0]).increment();

    registry.getMonitors();

    Meter kept = registry.get(registry.createId(NAMES[0]));
    Assertions.assertNotNull(kept, NAMES[0] + " was removed while still active");
    Assertions.assertFalse(((RemovableMeter) kept).isRemoved(),
        NAMES[0] + " was marked without being removed");
  }

  @Test
  public void heldReferenceRecoversWithoutTheTtl() {
    Id id = registry.createId("test");
    Counter held = registry.counter(id);
    held.increment();
    Meter first = registry.get(id);

    // Remove with no time passing, so nothing is expired: only the removal itself can tell the
    // held reference to resolve again.
    Iterator<Meter> it = registry.iterator();
    while (it.hasNext()) {
      if (it.next().id().equals(id)) {
        it.remove();
        break;
      }
    }
    Assertions.assertNull(registry.get(id));

    held.increment();
    Meter second = registry.get(id);
    Assertions.assertNotNull(second, "held reference never noticed the removal");
    Assertions.assertNotSame(first, second);
    // Resolving again is only half of it: the update has to land on the meter the registry
    // reports, not on the instance that was removed.
    Assertions.assertEquals(1.0, ((Counter) second).actualCount(), 1e-12,
        "the update did not land on the registered meter");
  }

  @Test
  public void theWrapperAddsNoClockReadsOfItsOwn() {
    Id id = registry.createId("test");
    Counter held = registry.counter(id);
    held.increment();
    Counter raw = (Counter) registry.get(id);

    // The raw meter is what the wrapper delegates to, so whatever the meter itself costs is the
    // floor. Checking the wrapper's staleness must not add to it, which is what asking
    // hasExpired() would do.
    long start = clock.reads.get();
    for (int i = 0; i < 1000; ++i) {
      raw.increment();
    }
    long rawReads = clock.reads.get() - start;

    start = clock.reads.get();
    for (int i = 0; i < 1000; ++i) {
      held.increment();
    }
    long heldReads = clock.reads.get() - start;

    // Guards the comparison against passing on two zeros: if the meter stopped reading the
    // clock the floor would be trivial and the assertion below would prove nothing.
    Assertions.assertTrue(rawReads > 0, "the raw meter never read the clock, nothing to compare");
    Assertions.assertEquals(rawReads, heldReads,
        "the wrapper read the clock " + (heldReads - rawReads) + " extra times per 1000 updates");
  }
}
