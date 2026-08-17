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
package com.netflix.spectator.atlas;

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.ManualClock;
import com.netflix.spectator.api.Meter;
import com.netflix.spectator.api.Timer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

/**
 * A user is expected to hold a {@code Counter} reference for the life of the process. If the
 * meter behind it goes idle long enough to be removed by the cleanup pass, later updates through
 * that stale reference still have to reach the registry.
 *
 * <p>{@code SwapMeter.get()} detects this from the registry version rather than from the TTL of
 * the underlying meter, so that the update path does not have to read the wall clock. These tests
 * pin that recovery down through the real Atlas registry.</p>
 */
public class AtlasHeldReferenceExpiryTest {

  private static final long STEP = 10_000L;
  private static final long TTL = 60_000L;

  private AtlasRegistry newRegistry(ManualClock clock) {
    // The core meter types are created with lwcStep as their step size, not step, so both are
    // pinned to STEP here to keep the roll forward in these tests exactly one interval.
    return new AtlasRegistry(clock, k -> {
      switch (k) {
        case "atlas.step":      return "PT10S";
        case "atlas.lwc.step":  return "PT10S";
        case "atlas.meterTTL":  return "PT1M";
        case "atlas.enabled":   return "false";
        default: return null;
      }
    });
  }

  @Test
  public void heldCounterRecoversAfterMeterIsRemoved() {
    ManualClock clock = new ManualClock();
    AtlasRegistry registry = newRegistry(clock);

    Counter held = registry.counter("test.counter");
    held.increment();

    Id id = registry.createId("test.counter");
    Meter original = registry.get(id);
    Assertions.assertNotNull(original);

    // Go idle past the TTL and run the cleanup pass, which drops the meter.
    clock.setWallTime(TTL * 2);
    registry.removeExpiredMeters();
    Assertions.assertNull(registry.get(id), "meter should have been removed");

    // The stale reference must resolve a fresh meter and the update must land on it.
    held.increment();

    Meter resurrected = registry.get(id);
    Assertions.assertNotNull(resurrected, "held reference must re-register the meter");
    Assertions.assertNotSame(original, resurrected);
    // actualCount reports the last completed interval, so roll forward to publish the update.
    clock.setWallTime(TTL * 2 + STEP);
    Assertions.assertEquals(1.0, ((Counter) resurrected).actualCount(), 1e-12);

    // And it must keep working, rather than recovering exactly once.
    clock.setWallTime(TTL * 4);
    registry.removeExpiredMeters();
    Assertions.assertNull(registry.get(id));
    held.increment();
    Meter second = registry.get(id);
    Assertions.assertNotNull(second);
    clock.setWallTime(TTL * 4 + STEP);
    Assertions.assertEquals(1.0, ((Counter) second).actualCount(), 1e-12);
  }

  @Test
  public void heldTimerRecoversAfterMeterIsRemoved() {
    ManualClock clock = new ManualClock();
    AtlasRegistry registry = newRegistry(clock);

    Timer held = registry.timer("test.timer");
    held.record(5, TimeUnit.MILLISECONDS);

    Id id = registry.createId("test.timer");
    clock.setWallTime(TTL * 2);
    registry.removeExpiredMeters();
    Assertions.assertNull(registry.get(id));

    held.record(5, TimeUnit.MILLISECONDS);
    Meter resurrected = registry.get(id);
    Assertions.assertNotNull(resurrected, "held reference must re-register the meter");
    // count reports the last completed interval, so roll forward to publish the update.
    clock.setWallTime(TTL * 2 + STEP);
    Assertions.assertEquals(1L, ((Timer) resurrected).count());
  }

  /**
   * Updates through a held reference must not be silently dropped on the floor. Before the
   * registry tracked a version, removal was noticed via the underlying TTL check; if that check
   * is removed without a version bump the first update after removal refreshes the orphaned
   * meter's timestamp, the reference never re-resolves, and every later update is lost.
   */
  @Test
  public void updatesAfterRemovalAreNotLost() {
    ManualClock clock = new ManualClock();
    AtlasRegistry registry = newRegistry(clock);

    Counter held = registry.counter("test.counter");
    Id id = registry.createId("test.counter");

    clock.setWallTime(TTL * 2);
    registry.removeExpiredMeters();

    // Several updates through the stale reference, all inside one step interval so they
    // accumulate into the same bucket.
    for (int i = 0; i < 5; ++i) {
      held.increment();
    }

    Meter m = registry.get(id);
    Assertions.assertNotNull(m, "held reference must re-register the meter");
    clock.setWallTime(TTL * 2 + STEP);
    Assertions.assertEquals(5.0, ((Counter) m).actualCount(), 1e-12,
        "every update through the held reference must reach the registered meter");

    // Repeat across another expiry cycle to confirm recovery is not a one shot.
    clock.setWallTime(TTL * 4);
    registry.removeExpiredMeters();
    Assertions.assertNull(registry.get(id));
    for (int i = 0; i < 3; ++i) {
      held.increment();
    }
    Meter m2 = registry.get(id);
    Assertions.assertNotNull(m2);
    clock.setWallTime(TTL * 4 + STEP);
    Assertions.assertEquals(3.0, ((Counter) m2).actualCount(), 1e-12);
  }

  /**
   * Sweeping one meter must not make unrelated healthy meters report themselves as expired; see
   * the class javadoc for why that signal is kept out of {@code hasExpired()}.
   */
  @Test
  public void sweepingOneMeterDoesNotExpireOthers() {
    ManualClock clock = new ManualClock();
    AtlasRegistry registry = newRegistry(clock);

    Counter idle = registry.counter("test.idle");
    idle.increment();

    // Let the idle meter age out, then keep the other one active right up to now.
    clock.setWallTime(TTL * 2);
    Counter active = registry.counter("test.active");
    active.increment();

    registry.removeExpiredMeters();
    Assertions.assertNull(registry.get(registry.createId("test.idle")),
        "the idle meter should have been swept");

    Assertions.assertFalse(active.hasExpired(),
        "an active meter must not report expired just because another meter was swept");

    // And its data must still be published rather than filtered out as expired.
    clock.setWallTime(TTL * 2 + STEP);
    Assertions.assertTrue(
        registry.measurements().anyMatch(m -> "test.active".equals(m.id().name())),
        "an active meter's measurements must not be filtered out of the published stream");
  }

  /**
   * A meter that is past its TTL but has not been swept yet must keep accumulating into the same
   * instance, so that an actively used meter is not reset by the expiry check itself.
   */
  @Test
  public void expiredButNotYetRemovedMeterKeepsSameInstance() {
    ManualClock clock = new ManualClock();
    AtlasRegistry registry = newRegistry(clock);

    Counter held = registry.counter("test.counter");
    held.increment();

    Id id = registry.createId("test.counter");
    Meter original = registry.get(id);

    // Past the TTL, but no cleanup pass has run.
    clock.setWallTime(TTL * 2);
    held.increment();

    Assertions.assertSame(original, registry.get(id));
    // The update refreshed the meter, so it is no longer a candidate for removal.
    registry.removeExpiredMeters();
    Assertions.assertSame(original, registry.get(id));
  }
}
