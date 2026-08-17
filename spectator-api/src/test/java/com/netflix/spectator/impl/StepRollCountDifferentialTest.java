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
package com.netflix.spectator.impl;

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.ManualClock;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

/**
 * {@link StepDouble} and {@link StepLong} cache the end of the current step interval so that the
 * division in {@code rollCount} is only paid on an actual rollover. The published values have to
 * be unchanged by that, so this drives the current implementation and a copy of the previous one
 * over identical timestamp sequences and requires the results to be bit-for-bit equal.
 *
 * <p>The sequences deliberately include the cases that distinguish the two: timestamps landing
 * exactly on a boundary, gaps that skip whole intervals so the previous interval had no activity,
 * and time moving backwards the way NTP can move it.</p>
 */
public class StepRollCountDifferentialTest {

  private static final long STEP = 5000L;

  /** The implementation of rollCount that shipped before the boundary was cached. */
  private static class LegacyStepDouble {

    private final double init;
    private final long step;

    private volatile double previous;
    private volatile long current;
    private volatile long lastInitPos;

    private static final AtomicLongFieldUpdater<LegacyStepDouble> CURRENT_UPDATER =
        AtomicLongFieldUpdater.newUpdater(LegacyStepDouble.class, "current");

    private static final AtomicLongFieldUpdater<LegacyStepDouble> LAST_INIT_POS_UPDATER =
        AtomicLongFieldUpdater.newUpdater(LegacyStepDouble.class, "lastInitPos");

    LegacyStepDouble(double init, Clock clock, long step) {
      this.init = init;
      this.step = step;
      previous = init;
      current = Double.doubleToLongBits(init);
      lastInitPos = clock.wallTime() / step;
    }

    private void rollCount(long now) {
      final long stepTime = now / step;
      final long lastInit = lastInitPos;
      if (lastInit < stepTime && LAST_INIT_POS_UPDATER.compareAndSet(this, lastInit, stepTime)) {
        final double v = Double.longBitsToDouble(
            CURRENT_UPDATER.getAndSet(this, Double.doubleToLongBits(init)));
        previous = (lastInit == stepTime - 1) ? v : init;
      }
    }

    double addAndGet(long now, double amount) {
      rollCount(now);
      long v;
      double d;
      double n;
      long next;
      do {
        v = current;
        d = Double.longBitsToDouble(v);
        n = d + amount;
        next = Double.doubleToLongBits(n);
      } while (!CURRENT_UPDATER.compareAndSet(this, v, next));
      return n;
    }

    double getCurrent(long now) {
      rollCount(now);
      return Double.longBitsToDouble(current);
    }

    double poll(long now) {
      rollCount(now);
      return previous;
    }

    double pollAsRate(long now) {
      final double amount = poll(now);
      final double period = step / 1000.0;
      return amount / period;
    }

    long timestamp() {
      return lastInitPos * step;
    }
  }

  /** The implementation of rollCount that shipped before the boundary was cached. */
  private static class LegacyStepLong {

    private final long init;
    private final long step;

    private volatile long previous;
    private volatile long current;
    private volatile long lastInitPos;

    private static final AtomicLongFieldUpdater<LegacyStepLong> CURRENT_UPDATER =
        AtomicLongFieldUpdater.newUpdater(LegacyStepLong.class, "current");

    private static final AtomicLongFieldUpdater<LegacyStepLong> LAST_INIT_POS_UPDATER =
        AtomicLongFieldUpdater.newUpdater(LegacyStepLong.class, "lastInitPos");

    LegacyStepLong(long init, Clock clock, long step) {
      this.init = init;
      this.step = step;
      previous = init;
      current = init;
      lastInitPos = clock.wallTime() / step;
    }

    private void rollCount(long now) {
      final long stepTime = now / step;
      final long lastInit = lastInitPos;
      if (lastInit < stepTime && LAST_INIT_POS_UPDATER.compareAndSet(this, lastInit, stepTime)) {
        final long v = CURRENT_UPDATER.getAndSet(this, init);
        previous = (lastInit == stepTime - 1) ? v : init;
      }
    }

    long addAndGet(long now, long amount) {
      rollCount(now);
      return CURRENT_UPDATER.addAndGet(this, amount);
    }

    long getCurrent(long now) {
      rollCount(now);
      return current;
    }

    long poll(long now) {
      rollCount(now);
      return previous;
    }

    double pollAsRate(long now) {
      final long amount = poll(now);
      final double period = step / 1000.0;
      return amount / period;
    }

    long timestamp() {
      return lastInitPos * step;
    }
  }

  /**
   * Timestamps covering the interesting rollover shapes: repeats within an interval, exact
   * boundaries, boundary minus and plus one, multi interval gaps, and backwards movement.
   */
  private List<Long> timestamps(long start) {
    List<Long> ts = new ArrayList<>();
    Random r = new Random(42);
    long now = start;
    for (int i = 0; i < 20_000; ++i) {
      ts.add(now);
      switch (r.nextInt(10)) {
        case 0:
          // Land exactly on the next boundary.
          now = (now / STEP + 1) * STEP;
          break;
        case 1:
          // One millisecond short of the next boundary.
          now = (now / STEP + 1) * STEP - 1;
          break;
        case 2:
          // One millisecond past the next boundary.
          now = (now / STEP + 1) * STEP + 1;
          break;
        case 3:
          // Skip several whole intervals, so the previous interval saw no activity.
          now += STEP * (2 + r.nextInt(5));
          break;
        case 4:
          // Time moves backwards, as it can when NTP adjusts the clock.
          now -= r.nextInt((int) STEP * 3);
          break;
        default:
          // Stay inside the current interval most of the time.
          now += r.nextInt(500);
          break;
      }
      // Wall time is not expected to be negative, but a backwards jump near zero can get there
      // with a ManualClock, and integer division truncates toward zero rather than flooring, so
      // the two implementations are compared over that range too rather than assumed equal.
      if (now < -3 * STEP) {
        now = 0;
      }
    }
    return ts;
  }

  @Test
  public void stepDoubleMatchesLegacyBitForBit() {
    for (long start : new long[] {0L, 1L, STEP, STEP - 1, 1_700_000_000_000L}) {
      ManualClock clock = new ManualClock(start, start);
      StepDouble actual = new StepDouble(0.0, clock, STEP);
      LegacyStepDouble expected = new LegacyStepDouble(0.0, clock, STEP);

      int op = 0;
      for (long now : timestamps(start)) {
        // Exercise every read and write path, since they all funnel through rollCount.
        switch (op++ % 4) {
          case 0:
            Assertions.assertEquals(
                Double.doubleToRawLongBits(expected.addAndGet(now, 1.0)),
                Double.doubleToRawLongBits(actual.addAndGet(now, 1.0)),
                "addAndGet at " + now + " from start " + start);
            break;
          case 1:
            Assertions.assertEquals(
                Double.doubleToRawLongBits(expected.getCurrent(now)),
                Double.doubleToRawLongBits(actual.getCurrent(now)),
                "getCurrent at " + now + " from start " + start);
            break;
          case 2:
            Assertions.assertEquals(
                Double.doubleToRawLongBits(expected.poll(now)),
                Double.doubleToRawLongBits(actual.poll(now)),
                "poll at " + now + " from start " + start);
            break;
          default:
            Assertions.assertEquals(
                Double.doubleToRawLongBits(expected.pollAsRate(now)),
                Double.doubleToRawLongBits(actual.pollAsRate(now)),
                "pollAsRate at " + now + " from start " + start);
            break;
        }
        Assertions.assertEquals(expected.timestamp(), actual.timestamp(),
            "timestamp at " + now + " from start " + start);
      }
    }
  }

  @Test
  public void stepLongMatchesLegacyBitForBit() {
    for (long start : new long[] {0L, 1L, STEP, STEP - 1, 1_700_000_000_000L}) {
      ManualClock clock = new ManualClock(start, start);
      StepLong actual = new StepLong(0L, clock, STEP);
      LegacyStepLong expected = new LegacyStepLong(0L, clock, STEP);

      int op = 0;
      for (long now : timestamps(start)) {
        switch (op++ % 4) {
          case 0:
            Assertions.assertEquals(
                expected.addAndGet(now, 1L),
                actual.addAndGet(now, 1L),
                "addAndGet at " + now + " from start " + start);
            break;
          case 1:
            Assertions.assertEquals(
                expected.getCurrent(now),
                actual.getCurrent(now),
                "getCurrent at " + now + " from start " + start);
            break;
          case 2:
            Assertions.assertEquals(
                expected.poll(now),
                actual.poll(now),
                "poll at " + now + " from start " + start);
            break;
          default:
            Assertions.assertEquals(
                Double.doubleToRawLongBits(expected.pollAsRate(now)),
                Double.doubleToRawLongBits(actual.pollAsRate(now)),
                "pollAsRate at " + now + " from start " + start);
            break;
        }
        Assertions.assertEquals(expected.timestamp(), actual.timestamp(),
            "timestamp at " + now + " from start " + start);
      }
    }
  }

  /**
   * An interval with no activity has to publish the init value rather than carrying the previous
   * interval forward. This is the case the cached boundary could plausibly break, so it is
   * asserted directly as well as through the differential comparison.
   */
  @Test
  public void noActivityInPreviousIntervalPublishesInit() {
    ManualClock clock = new ManualClock();
    StepDouble v = new StepDouble(0.0, clock, STEP);

    v.addAndGet(0L, 42.0);

    // Next interval: the 42.0 recorded above becomes the value for the completed interval.
    Assertions.assertEquals(42.0, v.poll(STEP), 1e-12);

    // Skip an entire interval with no updates at all. The completed interval had no activity,
    // so it must report init and not the stale 42.0.
    Assertions.assertEquals(0.0, v.poll(STEP * 4), 1e-12);
    Assertions.assertEquals(0.0, v.getCurrent(STEP * 4), 1e-12);
  }

  /**
   * The cached boundary must not let a rollover be skipped when the clock jumps backwards and
   * then forwards again, and a backwards jump inside the current interval must not roll.
   */
  @Test
  public void clockMovingBackwardsDoesNotRollOrSkip() {
    ManualClock clock = new ManualClock();
    StepDouble v = new StepDouble(0.0, clock, STEP);

    long base = STEP * 100;
    v.addAndGet(base, 7.0);
    Assertions.assertEquals(7.0, v.getCurrent(base), 1e-12);

    // Backwards within the same interval: no rollover, the value is still accumulating.
    v.addAndGet(base + 10, 1.0);
    v.addAndGet(base + 5, 1.0);
    Assertions.assertEquals(9.0, v.getCurrent(base + 5), 1e-12);

    // Backwards into an earlier interval: the existing lastInit < stepTime guard means this
    // does not roll backwards, and the current value is retained.
    Assertions.assertEquals(9.0, v.getCurrent(base - STEP * 3), 1e-12);

    // Forwards again past the boundary: the rollover still happens.
    Assertions.assertEquals(9.0, v.poll(base + STEP), 1e-12);
    Assertions.assertEquals(0.0, v.getCurrent(base + STEP), 1e-12);
  }
}
