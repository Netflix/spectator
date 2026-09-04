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
package com.netflix.spectator.api;

import com.netflix.spectator.impl.RemovableMeter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Iterator;

/**
 * The registry tells a meter when it has removed it. Nothing reads the mark yet; these pin the
 * marking itself so the reader can be added on top of behaviour that is already covered.
 */
public class RemovableMeterMarkingTest {

  private static class TestCounter implements Counter, RemovableMeter {
    private final Id id;
    private final Registry registry;
    volatile boolean expired;
    private volatile boolean removed;
    /** Whether the registry could still find this meter at the moment it was marked. */
    volatile boolean foundWhenMarked;

    TestCounter(Registry registry, Id id) {
      this.registry = registry;
      this.id = id;
    }

    @Override public Id id() {
      return id;
    }

    @Override public boolean hasExpired() {
      return expired;
    }

    @Override public boolean isRemoved() {
      return removed;
    }

    @Override public void markRemoved() {
      // The mark must only be set once the entry is gone. A reader that sees the mark and can
      // still resolve the id lands straight back on this instance and never makes progress.
      foundWhenMarked = registry.get(id) == this;
      removed = true;
    }

    @Override public Iterable<Measurement> measure() {
      return Collections.emptyList();
    }

    @Override public void add(double amount) {
    }

    @Override public double actualCount() {
      return 0.0;
    }
  }

  /** Counter that does not implement RemovableMeter, to check it is simply left alone. */
  private static final class PlainCounter implements Counter {
    private final Id id;
    volatile boolean expired;

    PlainCounter(Id id) {
      this.id = id;
    }

    @Override public Id id() {
      return id;
    }

    @Override public boolean hasExpired() {
      return expired;
    }

    @Override public Iterable<Measurement> measure() {
      return Collections.emptyList();
    }

    @Override public void add(double amount) {
    }

    @Override public double actualCount() {
      return 0.0;
    }
  }

  private static class TestRegistry extends AbstractRegistry {
    TestRegistry() {
      super(Clock.SYSTEM);
    }

    @Override protected Counter newCounter(Id id) {
      // Chosen from the id so the factory does not depend on call order.
      return id.name().startsWith("plain") ? new PlainCounter(id) : new TestCounter(this, id);
    }

    @Override protected DistributionSummary newDistributionSummary(Id id) {
      throw new UnsupportedOperationException();
    }

    @Override protected Timer newTimer(Id id) {
      throw new UnsupportedOperationException();
    }

    @Override protected Gauge newGauge(Id id) {
      throw new UnsupportedOperationException();
    }

    @Override protected Gauge newMaxGauge(Id id) {
      throw new UnsupportedOperationException();
    }
  }

  private TestCounter registered(TestRegistry r, String name) {
    r.counter(name).increment();
    return (TestCounter) r.get(r.createId(name));
  }

  @Test
  public void iteratorRemoveMarks() {
    TestRegistry r = new TestRegistry();
    TestCounter c = registered(r, "test");
    Assertions.assertFalse(c.isRemoved());

    Iterator<Meter> it = r.iterator();
    it.next();
    it.remove();

    Assertions.assertTrue(c.isRemoved());
    Assertions.assertNull(r.get(c.id()));
    Assertions.assertFalse(c.foundWhenMarked, "marked while still registered");
  }

  @Test
  public void cleanupPassMarks() {
    TestRegistry r = new TestRegistry();
    TestCounter kept = registered(r, "kept");
    TestCounter dropped = registered(r, "dropped");
    dropped.expired = true;

    r.removeExpiredMeters();

    Assertions.assertTrue(dropped.isRemoved());
    Assertions.assertFalse(dropped.foundWhenMarked, "marked while still registered");
    Assertions.assertFalse(kept.isRemoved(), "a meter that survived the pass must not be marked");
    Assertions.assertNull(r.get(dropped.id()));
    Assertions.assertNotNull(r.get(kept.id()));
  }

  @Test
  public void closeMarks() {
    TestRegistry r = new TestRegistry();
    TestCounter c = registered(r, "test");

    r.close();

    Assertions.assertTrue(c.isRemoved());
    Assertions.assertNull(r.get(c.id()));
    Assertions.assertFalse(c.foundWhenMarked, "marked while still registered");
  }

  @Test
  public void resetMarks() {
    TestRegistry r = new TestRegistry();
    TestCounter c = registered(r, "test");

    r.reset();

    Assertions.assertTrue(c.isRemoved());
    Assertions.assertNull(r.get(c.id()));
    Assertions.assertFalse(c.foundWhenMarked, "marked while still registered");
  }

  @Test
  public void meterThatCannotBeMarkedIsLeftAlone() {
    TestRegistry r = new TestRegistry();
    r.counter("plain").increment();
    Meter plainMeter = r.get(r.createId("plain"));
    Assertions.assertFalse(plainMeter instanceof RemovableMeter, "expected an unmarkable meter");
    PlainCounter plain = (PlainCounter) plainMeter;
    TestCounter markable = registered(r, "markable");
    plain.expired = true;
    markable.expired = true;

    r.removeExpiredMeters();

    Assertions.assertNull(r.get(plain.id()), "removal must still happen for a meter it cannot mark");
    // The unmarkable meter must not stop the rest of the pass from being marked.
    Assertions.assertTrue(markable.isRemoved());
    Assertions.assertNull(r.get(markable.id()));
  }

  @Test
  public void iteratorRemoveKeepsIllegalStateBehaviour() {
    TestRegistry r = new TestRegistry();
    registered(r, "test");

    Iterator<Meter> before = r.iterator();
    Assertions.assertThrows(IllegalStateException.class, before::remove,
        "remove() before next()");

    Iterator<Meter> twice = r.iterator();
    twice.next();
    twice.remove();
    Assertions.assertThrows(IllegalStateException.class, twice::remove,
        "repeated remove()");
  }
}
