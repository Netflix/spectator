/*
 * Copyright 2014-2025 Netflix, Inc.
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

/**
 * The registry bumps a version when it removes a meter so that held references know to resolve
 * again. That signal must not leak into {@code hasExpired()}.
 *
 * <p>Callers treat a true result as licence to discard the meter: {@code PolledMeter} drops it
 * from the set it aggregates, {@code Registry.measurements()} filters its data out, and
 * {@code cleanupCachedState()} evicts it. A {@link CompositeMeter} reports expired only when all
 * of its delegates do, and its delegates are the per registry swap wrappers, so a version based
 * answer there would blank out every meter in a composite after the first cleanup pass. That
 * matters because {@code Spectator.globalRegistry()} is a composite.</p>
 */
public class SwapMeterExpiryReportingTest {

  @Test
  public void cleanupDoesNotExpireHealthyMeters() {
    ManualClock clock = new ManualClock();
    ExpiringRegistry registry = new ExpiringRegistry(clock);

    // Counters in this registry expire as soon as the clock moves past their creation time.
    registry.counter("idle").increment();
    clock.setWallTime(1);

    Counter active = registry.counter("active");
    active.increment();
    Assertions.assertFalse(active.hasExpired(), "precondition: freshly created meter is healthy");

    registry.removeExpiredMeters();
    Assertions.assertEquals(1, registry.counters().count(), "the idle meter should be gone");

    Assertions.assertFalse(active.hasExpired(),
        "cleanup of an unrelated meter must not report a healthy meter as expired");
  }

  /**
   * The registry version has to be sampled before the meter is looked up, not after.
   *
   * <p>If it were sampled afterwards, a cleanup pass landing between the lookup and the sampling
   * would already be accounted for, so the returned wrapper would never notice that the meter it
   * captured is no longer registered. Every later update through it would be silently dropped.
   * The registry is subclassed here to run a removal in exactly that window.</p>
   */
  @Test
  public void versionIsSampledBeforeTheMeterIsResolved() {
    ManualClock clock = new ManualClock();

    class RacingRegistry extends ExpiringRegistry {
      private boolean sweepDuringLookup;

      RacingRegistry() {
        super(clock);
      }

      void armSweep() {
        sweepDuringLookup = true;
      }

      @Override protected <T extends Meter> T getOrCreate(
          Id id, Class<T> cls, T dflt, java.util.function.Function<Id, T> factory) {
        T m = super.getOrCreate(id, cls, dflt, factory);
        if (sweepDuringLookup) {
          sweepDuringLookup = false;
          // Simulates the cleanup pass removing this meter right after it was resolved but
          // before the caller has finished building the wrapper around it.
          removeExpiredMeters();
        }
        return m;
      }
    }

    RacingRegistry registry = new RacingRegistry();
    registry.counter("test").increment();

    // Move past the creation time so the meter is now eligible for removal, then arrange for the
    // sweep to happen inside the next lookup.
    clock.setWallTime(1);
    registry.armSweep();

    Counter held = registry.counter("test");
    Assertions.assertEquals(0, registry.counters().count(), "the sweep should have removed it");

    held.increment();
    Assertions.assertEquals(1, registry.counters().count(),
        "the returned reference must notice the removal and re-register the meter");
  }

  @Test
  public void cleanupDoesNotBlankOutACompositeRegistry() {
    ManualClock clock = new ManualClock();
    ExpiringRegistry underlying = new ExpiringRegistry(clock);
    CompositeRegistry composite = new CompositeRegistry(clock);
    composite.add(underlying);

    composite.counter("idle").increment();
    clock.setWallTime(1);

    Counter active = composite.counter("active");
    active.increment();

    underlying.removeExpiredMeters();

    Assertions.assertFalse(active.hasExpired(),
        "a composite meter must not report expired because the sub registry ran cleanup");

    // The whole point: the meter's data still has to reach the published stream, which filters
    // on hasExpired().
    long visible = composite.stream().filter(m -> !m.hasExpired()).count();
    Assertions.assertTrue(visible > 0,
        "cleanup must not filter every meter out of the composite's published stream");
  }
}
