/*
 * Copyright 2014-2024 Netflix, Inc.
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

import com.netflix.spectator.api.ManualClock;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class StepLongTest {

  private final ManualClock clock = new ManualClock();

  @BeforeEach
  public void init() {
    clock.setWallTime(0L);
  }

  @Test
  public void empty() {
    StepLong v = new StepLong(0L, clock, 10L);
    Assertions.assertEquals(0L, v.getCurrent());
    Assertions.assertEquals(0L, v.poll());
  }

  @Test
  public void increment() {
    StepLong v = new StepLong(0L, clock, 10L);
    v.incrementAndGet(clock.wallTime());
    Assertions.assertEquals(1L, v.getCurrent());
    Assertions.assertEquals(0L, v.poll());
  }

  @Test
  public void incrementAndCrossStepBoundary() {
    StepLong v = new StepLong(0L, clock, 10L);
    v.incrementAndGet(clock.wallTime());
    clock.setWallTime(10L);
    Assertions.assertEquals(0L, v.getCurrent());
    Assertions.assertEquals(1L, v.poll());
  }

  @Test
  public void missedRead() {
    StepLong v = new StepLong(0L, clock, 10L);
    v.incrementAndGet(clock.wallTime());
    clock.setWallTime(20L);
    Assertions.assertEquals(0L, v.getCurrent());
    Assertions.assertEquals(0L, v.poll());
  }

  @Test
  public void initDefault() {
    StepLong v = new StepLong(0L, clock, 10L);
    Assertions.assertEquals(0L, v.getCurrent());
  }

  @Test
  public void initWithValue() {
    StepLong v = new StepLong(42L, clock, 10L);
    Assertions.assertEquals(42L, v.getCurrent());
  }

  @Test
  public void set() {
    StepLong v = new StepLong(13L, clock, 10L);
    v.setCurrent(clock.wallTime(), 42L);
    Assertions.assertEquals(42L, v.getCurrent(clock.wallTime()));
  }

  @Test
  public void getAndSet() {
    final long now = clock.wallTime();
    StepLong v = new StepLong(13L, clock, 10L);
    Assertions.assertEquals(13L, v.getAndSet(now, 42L));
    Assertions.assertEquals(42L, v.getCurrent(now));
  }

  @Test
  public void compareAndSet() {
    final long now = clock.wallTime();
    StepLong v = new StepLong(13L, clock, 10L);
    Assertions.assertTrue(v.compareAndSet(now, 13L, 42L));
    Assertions.assertEquals(42L, v.getCurrent(now));
  }

  @Test
  public void compareAndSetFail() {
    final long now = clock.wallTime();
    StepLong v = new StepLong(13L, clock, 10L);
    Assertions.assertFalse(v.compareAndSet(now, 12L, 42L));
    Assertions.assertEquals(13L, v.getCurrent(now));
  }

  @Test
  public void incrementAndGet() {
    final long now = clock.wallTime();
    StepLong v = new StepLong(13L, clock, 10L);
    Assertions.assertEquals(14L, v.incrementAndGet(now));
    Assertions.assertEquals(14L, v.getCurrent(now));
  }

  @Test
  public void getAndIncrement() {
    final long now = clock.wallTime();
    StepLong v = new StepLong(13L, clock, 10L);
    Assertions.assertEquals(13L, v.getAndIncrement(now));
    Assertions.assertEquals(14L, v.getCurrent(now));
  }

  @Test
  public void addAndGet() {
    final long now = clock.wallTime();
    StepLong v = new StepLong(13L, clock, 10L);
    Assertions.assertEquals(55L, v.addAndGet(now, 42L));
    Assertions.assertEquals(55L, v.getCurrent(now));
  }

  @Test
  public void getAndAdd() {
    final long now = clock.wallTime();
    StepLong v = new StepLong(13L, clock, 10L);
    Assertions.assertEquals(13L, v.getAndAdd(now, 42L));
    Assertions.assertEquals(55L, v.getCurrent(now));
  }

  @Test
  public void minGt() {
    final long now = clock.wallTime();
    StepLong v = new StepLong(0L, clock, 10L);
    v.min(now, 2L);
    Assertions.assertEquals(0L, v.getCurrent(now));
  }

  @Test
  public void minLt() {
    final long now = clock.wallTime();
    StepLong v = new StepLong(2L, clock, 10L);
    v.min(now, 0L);
    Assertions.assertEquals(0L, v.getCurrent(now));
  }

  @Test
  public void minNegative() {
    final long now = clock.wallTime();
    StepLong v = new StepLong(-42L, clock, 10L);
    v.min(now, -41L);
    Assertions.assertEquals(-42L, v.getCurrent(now));
  }

  @Test
  public void maxGt() {
    final long now = clock.wallTime();
    StepLong v = new StepLong(0L, clock, 10L);
    v.max(now, 2L);
    Assertions.assertEquals(2L, v.getCurrent(now));
  }

  @Test
  public void maxLt() {
    final long now = clock.wallTime();
    StepLong v = new StepLong(2L, clock, 10L);
    v.max(now, 0L);
    Assertions.assertEquals(2L, v.getCurrent(now));
  }

  @Test
  public void maxNegative() {
    final long now = clock.wallTime();
    StepLong v = new StepLong(-42L, clock, 10L);
    v.max(now, -41L);
    Assertions.assertEquals(-41L, v.getCurrent(now));
  }
}
