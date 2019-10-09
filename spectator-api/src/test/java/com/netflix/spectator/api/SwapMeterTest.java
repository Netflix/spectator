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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

public class SwapMeterTest {

  private static final LongSupplier VERSION = () -> 0L;

  private final ManualClock clock = new ManualClock();
  private final Registry registry = new DefaultRegistry();

  private final Id counterId = registry.createId("counter");

  private final Id gaugeId = registry.createId("gauge");

  private final Id timerId = registry.createId("timer");

  private final Id distSummaryId = registry.createId("distSummary");

  @Test
  public void wrappedCounters() {
    Counter c = new DefaultCounter(clock, counterId);
    SwapCounter sc1 = new SwapCounter(registry, VERSION, counterId, c);
    SwapCounter sc2 = new SwapCounter(registry, VERSION, counterId, sc1);
    Assertions.assertFalse(sc2.hasExpired());
    sc2.increment();
    Assertions.assertEquals(1, c.count());
    Assertions.assertEquals(1, sc1.count());
    Assertions.assertEquals(1, sc2.count());
  }

  @Test
  public void wrapExpiredCounter() {
    ExpiringRegistry registry = new ExpiringRegistry(clock);
    Counter c = registry.counter(counterId);
    clock.setWallTime(60000 * 30);
    SwapCounter s1 = new SwapCounter(registry, VERSION, counterId, c);
    s1.increment();
    Assertions.assertEquals(1, c.count());
    Assertions.assertEquals(1, s1.count());
  }

  @Test
  public void wrapExpiredTimer() {
    ExpiringRegistry registry = new ExpiringRegistry(clock);
    Timer t = registry.timer(timerId);
    clock.setWallTime(60000 * 30);
    SwapTimer s1 = new SwapTimer(registry, VERSION, timerId, t);
    s1.record(42, TimeUnit.NANOSECONDS);
    Assertions.assertEquals(1, t.count());
    Assertions.assertEquals(1, s1.count());
  }

  @Test
  public void wrapExpiredGauge() {
    ExpiringRegistry registry = new ExpiringRegistry(clock);
    Gauge c = registry.gauge(gaugeId);
    clock.setWallTime(60000 * 30);
    SwapGauge s1 = new SwapGauge(registry, VERSION, gaugeId, c);
    s1.set(1.0);
    Assertions.assertEquals(1.0, c.value(), 1e-12);
    Assertions.assertEquals(1.0, s1.value(), 1e-12);
  }

  @Test
  public void wrapExpiredDistSummary() {
    ExpiringRegistry registry = new ExpiringRegistry(clock);
    DistributionSummary c = registry.distributionSummary(distSummaryId);
    clock.setWallTime(60000 * 30);
    SwapDistributionSummary s1 = new SwapDistributionSummary(registry, VERSION, distSummaryId, c);
    s1.record(1);
    Assertions.assertEquals(1, c.count());
    Assertions.assertEquals(1, s1.count());
  }

  @Test
  public void versionUpdateExpiration() {
    AtomicLong version = new AtomicLong();
    Counter c = new DefaultCounter(clock, counterId);
    SwapCounter sc = new SwapCounter(registry, version::get, counterId, c);

    sc.increment();
    Assertions.assertFalse(sc.hasExpired());

    version.incrementAndGet();
    Assertions.assertTrue(sc.hasExpired());
    sc.increment();
    Assertions.assertFalse(sc.hasExpired());
  }
}
