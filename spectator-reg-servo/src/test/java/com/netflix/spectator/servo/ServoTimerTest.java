/**
 * Copyright 2015 Netflix, Inc.
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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.math.BigInteger;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

@RunWith(JUnit4.class)
public class ServoTimerTest {

  private final ManualClock clock = new ManualClock();

  private Timer newTimer(String name) {
    final Registry r = new ServoRegistry(clock);
    return r.timer(r.createId(name));
  }

  @Before
  public void before() {
    clock.setWallTime(0L);
    clock.setMonotonicTime(0L);
  }

  @Test
  public void testInit() {
    Timer t = newTimer("foo");
    Assert.assertEquals(t.count(), 0L);
    Assert.assertEquals(t.totalTime(), 0L);
  }

  @Test
  public void testRecord() {
    Timer t = newTimer("foo");
    t.record(42, TimeUnit.MILLISECONDS);
    Assert.assertEquals(t.count(), 1L);
    Assert.assertEquals(t.totalTime(), 42000000L);
  }

  @Test
  public void testRecordNegative() {
    Timer t = newTimer("foo");
    t.record(-42, TimeUnit.MILLISECONDS);
    Assert.assertEquals(t.count(), 0L);
    Assert.assertEquals(t.totalTime(), 0L);
  }

  @Test
  public void testRecordZero() {
    Timer t = newTimer("foo");
    t.record(0, TimeUnit.MILLISECONDS);
    Assert.assertEquals(t.count(), 1L);
    Assert.assertEquals(t.totalTime(), 0L);
  }

  @Test
  public void testRecordCallable() throws Exception {
    Timer t = newTimer("foo");
    clock.setMonotonicTime(100L);
    int v = t.call(() -> {
      clock.setMonotonicTime(500L);
      return 42;
    });
    Assert.assertEquals(v, 42);
    Assert.assertEquals(t.count(), 1L);
    Assert.assertEquals(t.totalTime(), 400L);
  }

  @Test
  public void testRecordCallableException() throws Exception {
    Timer t = newTimer("foo");
    clock.setMonotonicTime(100L);
    boolean seen = false;
    try {
      t.call((Callable<Integer>) () -> {
        clock.setMonotonicTime(500L);
        throw new RuntimeException("foo");
      });
    } catch (Exception e) {
      seen = true;
    }
    Assert.assertTrue(seen);
    Assert.assertEquals(t.count(), 1L);
    Assert.assertEquals(t.totalTime(), 400L);
  }

  @Test
  public void testRecordRunnable() throws Exception {
    Timer t = newTimer("foo");
    clock.setMonotonicTime(100L);
    t.run(() -> clock.setMonotonicTime(500L));
    Assert.assertEquals(t.count(), 1L);
    Assert.assertEquals(t.totalTime(), 400L);
  }

  @Test
  public void testRecordRunnableException() throws Exception {
    Timer t = newTimer("foo");
    clock.setMonotonicTime(100L);
    boolean seen = false;
    try {
      t.run((Runnable) () -> {
        clock.setMonotonicTime(500L);
        throw new RuntimeException("foo");
      });
    } catch (Exception e) {
      seen = true;
    }
    Assert.assertTrue(seen);
    Assert.assertEquals(t.count(), 1L);
    Assert.assertEquals(t.totalTime(), 400L);
  }

  @Test
  public void testMeasure() {
    Timer t = newTimer("foo");
    t.record(42, TimeUnit.MILLISECONDS);
    clock.setWallTime(61000L);
    for (Measurement m : t.measure()) {
      Assert.assertEquals(m.timestamp(), 61000L);
      final double count = Utils.first(t.measure(), Statistic.count).value();
      final double totalTime = Utils.first(t.measure(), Statistic.totalTime).value();
      Assert.assertEquals(count, 1.0 / 60.0, 0.1e-12);
      Assert.assertEquals(totalTime, 42e-3 / 60.0, 0.1e-12);
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
    Assert.assertEquals(perSec.doubleValue() / factor, v, 1e-12);
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
    Assert.assertEquals(sumOfSq.doubleValue() / factor, v, 1e-12);
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
    Assert.assertEquals(sumOfSq.doubleValue() / factor, v, 2.0);
  }

  @Test
  public void expiration() {
    final long initTime = TimeUnit.MINUTES.toMillis(30);
    final long fifteenMinutes = TimeUnit.MINUTES.toMillis(15);

    // Expired on init, wait for activity to mark as active
    clock.setWallTime(initTime);
    Timer t = newTimer("foo");
    Assert.assertTrue(t.hasExpired());
    t.record(1, TimeUnit.SECONDS);
    Assert.assertFalse(t.hasExpired());

    // Expires with inactivity
    clock.setWallTime(initTime + fifteenMinutes + 1);
    Assert.assertTrue(t.hasExpired());

    // Activity brings it back
    t.record(42, TimeUnit.SECONDS);
    Assert.assertFalse(t.hasExpired());
  }

}
