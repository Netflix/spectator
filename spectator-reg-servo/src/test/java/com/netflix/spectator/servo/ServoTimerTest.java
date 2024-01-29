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
package com.netflix.spectator.servo;

import com.netflix.spectator.api.ManualClock;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Statistic;
import com.netflix.spectator.api.Timer;
import com.netflix.spectator.api.Utils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class ServoTimerTest {

  private final ManualClock clock = new ManualClock();

  private Timer newTimer(String name) {
    final Registry r = Servo.newRegistry(clock);
    return r.timer(r.createId(name));
  }

  @BeforeEach
  public void before() {
    clock.setWallTime(0L);
    clock.setMonotonicTime(0L);
  }

  @Test
  public void testInit() {
    Timer t = newTimer("foo");
    Assertions.assertEquals(t.count(), 0L);
    Assertions.assertEquals(t.totalTime(), 0L);
  }

  @Test
  public void testRecord() {
    Timer t = newTimer("foo");
    t.record(42, TimeUnit.MILLISECONDS);
    Assertions.assertEquals(t.count(), 1L);
    Assertions.assertEquals(t.totalTime(), 42000000L);
  }

  @Test
  public void testRecordNegative() {
    Timer t = newTimer("foo");
    t.record(-42, TimeUnit.MILLISECONDS);
    Assertions.assertEquals(t.count(), 0L);
    Assertions.assertEquals(t.totalTime(), 0L);
  }

  @Test
  public void testRecordZero() {
    Timer t = newTimer("foo");
    t.record(0, TimeUnit.MILLISECONDS);
    Assertions.assertEquals(t.count(), 1L);
    Assertions.assertEquals(t.totalTime(), 0L);
  }

  @Test
  public void testRecordCallable() throws Exception {
    Timer t = newTimer("foo");
    clock.setMonotonicTime(100L);
    int v = t.recordCallable(() -> {
      clock.setMonotonicTime(500L);
      return 42;
    });
    Assertions.assertEquals(v, 42);
    Assertions.assertEquals(t.count(), 1L);
    Assertions.assertEquals(t.totalTime(), 400L);
  }

  @Test
  public void testRecordCallableException() throws Exception {
    Timer t = newTimer("foo");
    clock.setMonotonicTime(100L);
    boolean seen = false;
    try {
      t.recordCallable(() -> {
        clock.setMonotonicTime(500L);
        throw new RuntimeException("foo");
      });
    } catch (Exception e) {
      seen = true;
    }
    Assertions.assertTrue(seen);
    Assertions.assertEquals(t.count(), 1L);
    Assertions.assertEquals(t.totalTime(), 400L);
  }

  @Test
  public void testRecordRunnable() throws Exception {
    Timer t = newTimer("foo");
    clock.setMonotonicTime(100L);
    t.recordRunnable(() -> clock.setMonotonicTime(500L));
    Assertions.assertEquals(t.count(), 1L);
    Assertions.assertEquals(t.totalTime(), 400L);
  }

  @Test
  public void testRecordRunnableException() throws Exception {
    Timer t = newTimer("foo");
    clock.setMonotonicTime(100L);
    boolean seen = false;
    try {
      t.recordRunnable(() -> {
        clock.setMonotonicTime(500L);
        throw new RuntimeException("foo");
      });
    } catch (Exception e) {
      seen = true;
    }
    Assertions.assertTrue(seen);
    Assertions.assertEquals(t.count(), 1L);
    Assertions.assertEquals(t.totalTime(), 400L);
  }

  @Test
  public void testMeasure() {
    Timer t = newTimer("foo");
    t.record(42, TimeUnit.MILLISECONDS);
    clock.setWallTime(61000L);
    for (Measurement m : t.measure()) {
      Assertions.assertEquals(m.timestamp(), 61000L);
      final double count = Utils.first(t.measure(), Statistic.count).value();
      final double totalTime = Utils.first(t.measure(), Statistic.totalTime).value();
      Assertions.assertEquals(count, 1.0 / 60.0, 0.1e-12);
      Assertions.assertEquals(totalTime, 42e-3 / 60.0, 0.1e-12);
    }
  }

  @Test
  public void totalOfSquaresOverflow() {
    final long seconds = 10;
    final long nanos = TimeUnit.SECONDS.toNanos(seconds);
    final BigInteger s = new BigInteger("" + nanos);
    final BigInteger s2 = s.multiply(s);

    Timer t = newTimer("foo");
    t.record(seconds, TimeUnit.SECONDS);
    clock.setWallTime(61000L);

    final double v = Utils.first(t.measure(), Statistic.totalOfSquares).value();

    final double factor = 1e9 * 1e9;
    final BigInteger perSec = s2.divide(BigInteger.valueOf(60));
    Assertions.assertEquals(perSec.doubleValue() / factor, v, 1e-12);
  }

  @Test
  public void totalOfSquaresManySmallValues() {
    Timer t = newTimer("foo");
    BigInteger sumOfSq = new BigInteger("0");
    for (int i = 0; i < 100000; ++i) {
      final long nanos = i;
      final BigInteger s = new BigInteger("" + nanos);
      final BigInteger s2 = s.multiply(s);
      sumOfSq = sumOfSq.add(s2);
      t.record(i, TimeUnit.NANOSECONDS);
    }
    clock.setWallTime(61000L);

    final double v = Utils.first(t.measure(), Statistic.totalOfSquares).value();

    final double factor = 1e9 * 1e9;
    sumOfSq = sumOfSq.divide(BigInteger.valueOf(60));
    Assertions.assertEquals(sumOfSq.doubleValue() / factor, v, 1e-12);
  }

  @Test
  public void totalOfSquaresManyBigValues() {
    Timer t = newTimer("foo");
    BigInteger sumOfSq = new BigInteger("0");
    for (int i = 0; i < 100000; ++i) {
      final long nanos = TimeUnit.SECONDS.toNanos(i);
      final BigInteger s = new BigInteger("" + nanos);
      final BigInteger s2 = s.multiply(s);
      sumOfSq = sumOfSq.add(s2);
      t.record(i, TimeUnit.SECONDS);
    }
    clock.setWallTime(61000L);

    final double v = Utils.first(t.measure(), Statistic.totalOfSquares).value();

    // Expected :3.3332833335E14
    // Actual   :3.3332833334999825E14
    final double factor = 1e9 * 1e9;
    sumOfSq = sumOfSq.divide(BigInteger.valueOf(60));
    Assertions.assertEquals(sumOfSq.doubleValue() / factor, v, 2.0);
  }

  @Test
  public void expiration() {
    final long initTime = TimeUnit.MINUTES.toMillis(30);
    final long fifteenMinutes = TimeUnit.MINUTES.toMillis(15);

    // Not expired on init, wait for activity to mark as active
    clock.setWallTime(initTime);
    Timer t = newTimer("foo");
    Assertions.assertFalse(t.hasExpired());
    t.record(1, TimeUnit.SECONDS);
    Assertions.assertFalse(t.hasExpired());

    // Expires with inactivity
    clock.setWallTime(initTime + fifteenMinutes + 1);
    Assertions.assertTrue(t.hasExpired());

    // Activity brings it back
    t.record(42, TimeUnit.SECONDS);
    Assertions.assertFalse(t.hasExpired());
  }

}
