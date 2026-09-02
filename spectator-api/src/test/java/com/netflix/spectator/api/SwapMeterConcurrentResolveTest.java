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
package com.netflix.spectator.api;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Updates through a held reference must not be lost while another thread is resolving the same
 * wrapper. The resolve version is published after the meter it describes, so a caller that sees
 * the new version also sees the new meter; publishing it first would let that caller skip the
 * resolve and keep writing to the instance the lookup just replaced.
 */
public class SwapMeterConcurrentResolveTest {

  /** Counter that reports itself expired once removed, like AtlasCounter past its TTL. */
  private static final class ExpirableCounter implements Counter {
    private final Id id;
    private final AtomicLong count = new AtomicLong();
    volatile boolean expired = false;

    ExpirableCounter(Id id) {
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
      count.addAndGet((long) amount);
    }

    @Override public double actualCount() {
      return count.get();
    }
  }

  private static final class TestRegistry extends AbstractRegistry {
    /** Set to make the next meter creation block, pinning a thread inside lookup(). */
    final AtomicBoolean blockNextCreate = new AtomicBoolean();
    final CountDownLatch insideLookup = new CountDownLatch(1);
    final CountDownLatch releaseLookup = new CountDownLatch(1);

    TestRegistry() {
      super(Clock.SYSTEM);
    }

    @Override protected Counter newCounter(Id id) {
      if (blockNextCreate.compareAndSet(true, false)) {
        insideLookup.countDown();
        try {
          releaseLookup.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
      return new ExpirableCounter(id);
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

    /** Remove the expired meters, as a registry cleanup pass would. */
    void sweep() {
      Iterator<Meter> it = iterator();
      while (it.hasNext()) {
        if (it.next().hasExpired()) {
          it.remove();
        }
      }
    }
  }

  /**
   * Pins the interleaving: one thread is inside the lookup while a second updates the same
   * wrapper.
   */
  @Test
  @Timeout(60)
  public void updateIsNotLostWhileAnotherThreadResolves() throws Exception {
    TestRegistry registry = new TestRegistry();
    Id id = registry.createId("test");

    // A reference the user holds on to, as in `Counter c = registry.counter(id)`.
    Counter held = registry.counter(id);
    held.increment();
    ExpirableCounter first = (ExpirableCounter) registry.get(id);
    Assertions.assertEquals(1.0, first.actualCount());

    first.expired = true;
    registry.sweep();

    // Thread A updates through the held reference and blocks inside lookup().
    registry.blockNextCreate.set(true);
    Thread a = new Thread(held::increment);
    a.start();
    Assertions.assertTrue(registry.insideLookup.await(30, TimeUnit.SECONDS),
        "thread A never reached the lookup");

    // Thread B updates the same held reference while A is still resolving.
    Thread b = new Thread(held::increment);
    b.start();
    b.join(30_000);
    Assertions.assertFalse(b.isAlive(), "thread B did not finish");

    registry.releaseLookup.countDown();
    a.join(30_000);
    Assertions.assertFalse(a.isAlive(), "thread A did not finish");

    Assertions.assertEquals(2.0, registry.counter(id).actualCount(),
        "both updates should land on the registered meter; the removed one received "
            + (first.actualCount() - 1.0));
  }
}
