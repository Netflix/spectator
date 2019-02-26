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
package com.netflix.spectator.api.histogram;

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.ExpiringRegistry;
import com.netflix.spectator.api.ManualClock;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Spectator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class PercentileTimerTest {

  private Registry newRegistry() {
    return new DefaultRegistry(Clock.SYSTEM, k -> null);
  }

  private void checkPercentiles(PercentileTimer t, int start) {
    for (int i = 0; i < 100_000; ++i) {
      t.record(i, TimeUnit.MILLISECONDS);
    }
    for (int i = start; i <= 100; ++i) {
      double expected = (double) i;
      double threshold = 0.15 * expected + 1e-12;
      Assertions.assertEquals(expected, t.percentile(i), threshold);
    }
  }

  @Test
  public void percentile() {
    Registry r = newRegistry();
    PercentileTimer t = PercentileTimer.get(r, r.createId("test"));
    checkPercentiles(t, 0);
  }

  @Test
  public void builderWithThreshold() {
    Registry r = newRegistry();
    PercentileTimer t = PercentileTimer.builder(r)
        .withName("test")
        .withRange(10, 100, TimeUnit.SECONDS)
        .build();
    checkPercentiles(t, 10);
  }

  @Test
  public void builderWithThresholdDuration() {
    Registry r = newRegistry();
    PercentileTimer t = PercentileTimer.builder(r)
        .withName("test")
        .withRange(Duration.ZERO, Duration.ofSeconds(100))
        .build();
    checkPercentiles(t, 0);
  }

  private void checkValue(PercentileTimer t1, PercentileTimer t2, double expected) {
    Assertions.assertEquals(expected, t1.percentile(99.0), expected / 5.0);
    Assertions.assertEquals(expected, t2.percentile(99.0), expected / 5.0);
  }

  @Test
  public void builderWithDifferentThresholds() {
    Registry r = newRegistry();
    PercentileTimer t1 = PercentileTimer.builder(r)
        .withName("test")
        .withRange(10, 50, TimeUnit.SECONDS)
        .build();
    PercentileTimer t2 = PercentileTimer.builder(r)
        .withName("test")
        .withRange(100, 200, TimeUnit.SECONDS)
        .build();

    t1.record(5, TimeUnit.SECONDS);
    checkValue(t1, t2, 10.0);

    t1.record(500, TimeUnit.SECONDS);
    checkValue(t1, t2, 50.0);

    t2.record(5, TimeUnit.SECONDS);
    checkValue(t1, t2, 100.0);

    t2.record(500, TimeUnit.SECONDS);
    checkValue(t1, t2, 200.0);
  }

  @Test
  public void expiration() {
    ManualClock clock = new ManualClock();
    ExpiringRegistry r = new ExpiringRegistry(clock);
    PercentileTimer t = PercentileTimer.builder(r)
        .withName("test")
        .build();

    Assertions.assertFalse(t.hasExpired());
    t.record(5, TimeUnit.SECONDS);
    Assertions.assertEquals(1, r.timer("test").count());

    clock.setWallTime(1);
    Assertions.assertTrue(t.hasExpired());
    r.removeExpiredMeters();
    Assertions.assertNull(r.state().get(t.id()));

    t.record(5, TimeUnit.SECONDS);
    Assertions.assertFalse(t.hasExpired());
    Assertions.assertEquals(1, r.timer("test").count());
  }

  @Test
  public void expirationGlobalRegistry() {
    ManualClock clock = new ManualClock();
    ExpiringRegistry r = new ExpiringRegistry(clock);
    Spectator.globalRegistry().removeAll();
    Spectator.globalRegistry().add(r);
    PercentileTimer t = PercentileTimer.builder(Spectator.globalRegistry())
        .withName("test")
        .build();

    Assertions.assertFalse(t.hasExpired());
    t.record(5, TimeUnit.SECONDS);
    Assertions.assertEquals(1, r.timer("test").count());

    clock.setWallTime(1);
    Assertions.assertTrue(t.hasExpired());
    r.removeExpiredMeters();
    Assertions.assertNull(r.state().get(t.id()));

    t.record(5, TimeUnit.SECONDS);
    Assertions.assertFalse(t.hasExpired());
    Assertions.assertEquals(1, r.timer("test").count());
  }
}
