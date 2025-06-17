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

public class StepDoubleTest {

  private final ManualClock clock = new ManualClock();

  @BeforeEach
  public void init() {
    clock.setWallTime(0L);
  }

  @Test
  public void empty() {
    StepDouble v = new StepDouble(0.0, clock, 10L);
    Assertions.assertEquals(0.0, v.getCurrent(), 1e-12);
    Assertions.assertEquals(0.0, v.poll(), 1e-12);
  }

  @Test
  public void increment() {
    StepDouble v = new StepDouble(0.0, clock, 10L);
    v.addAndGet(clock.wallTime(), 1.0);
    Assertions.assertEquals(1.0, v.getCurrent(), 1e-12);
    Assertions.assertEquals(0.0, v.poll(), 1e-12);
  }

  @Test
  public void incrementAndCrossStepBoundary() {
    StepDouble v = new StepDouble(0.0, clock, 10L);
    v.addAndGet(clock.wallTime(), 1.0);
    clock.setWallTime(10L);
    Assertions.assertEquals(0.0, v.getCurrent(), 1e-12);
    Assertions.assertEquals(1.0, v.poll(), 1e-12);
  }

  @Test
  public void missedRead() {
    StepDouble v = new StepDouble(0.0, clock, 10L);
    v.addAndGet(clock.wallTime(), 1.0);
    clock.setWallTime(20L);
    Assertions.assertEquals(0.0, v.getCurrent(), 1e-12);
    Assertions.assertEquals(0.0, v.poll(), 1e-12);
  }

  @Test
  public void initDefault() {
    StepDouble v = new StepDouble(0.0, clock, 10L);
    Assertions.assertEquals(0.0, v.getCurrent(), 1e-12);
  }

  @Test
  public void initWithValue() {
    StepDouble v = new StepDouble(42.0, clock, 10L);
    Assertions.assertEquals(42.0, v.getCurrent(), 1e-12);
  }

  @Test
  public void set() {
    StepDouble v = new StepDouble(13.0, clock, 10L);
    v.setCurrent(clock.wallTime(), 42.0);
    Assertions.assertEquals(42.0, v.getCurrent(clock.wallTime()), 1e-12);
  }

  @Test
  public void getAndSet() {
    final long now = clock.wallTime();
    StepDouble v = new StepDouble(13.0, clock, 10L);
    Assertions.assertEquals(13.0, v.getAndSet(now, 42.0), 1e-12);
    Assertions.assertEquals(42.0, v.getCurrent(now), 1e-12);
  }

  @Test
  public void compareAndSet() {
    final long now = clock.wallTime();
    StepDouble v = new StepDouble(13.0, clock, 10L);
    Assertions.assertTrue(v.compareAndSet(now, 13.0, 42.0));
    Assertions.assertEquals(42.0, v.getCurrent(now), 1e-12);
  }

  @Test
  public void compareAndSetFail() {
    final long now = clock.wallTime();
    StepDouble v = new StepDouble(13.0, clock, 10L);
    Assertions.assertFalse(v.compareAndSet(now, 12.0, 42.0));
    Assertions.assertEquals(13.0, v.getCurrent(now), 1e-12);
  }

  @Test
  public void addAndGet() {
    final long now = clock.wallTime();
    StepDouble v = new StepDouble(13.0, clock, 10L);
    Assertions.assertEquals(55.0, v.addAndGet(now, 42.0), 1e-12);
    Assertions.assertEquals(55.0, v.getCurrent(now), 1e-12);
  }

  @Test
  public void getAndAdd() {
    final long now = clock.wallTime();
    StepDouble v = new StepDouble(13.0, clock, 10L);
    Assertions.assertEquals(13.0, v.getAndAdd(now, 42.0), 1e-12);
    Assertions.assertEquals(55.0, v.getCurrent(now), 1e-12);
  }

  @Test
  public void minGt() {
    final long now = clock.wallTime();
    StepDouble v = new StepDouble(0.0, clock, 10L);
    v.min(now, 2.0);
    Assertions.assertEquals(0.0, v.getCurrent(now), 1e-12);
  }

  @Test
  public void minLt() {
    final long now = clock.wallTime();
    StepDouble v = new StepDouble(2.0, clock, 10L);
    v.min(now, 0.0);
    Assertions.assertEquals(0.0, v.getCurrent(now), 1e-12);
  }

  @Test
  public void minNegative() {
    final long now = clock.wallTime();
    StepDouble v = new StepDouble(-42.0, clock, 10L);
    v.min(now, -41.0);
    Assertions.assertEquals(-42.0, v.getCurrent(now), 1e-12);
  }

  @Test
  public void minNaN() {
    final long now = clock.wallTime();
    StepDouble v = new StepDouble(Double.NaN, clock, 10L);
    v.min(now, 0.0);
    Assertions.assertEquals(0.0, v.getCurrent(now), 1e-12);
  }

  @Test
  public void minValueNaN() {
    final long now = clock.wallTime();
    StepDouble v = new StepDouble(0.0, clock, 10L);
    v.min(now, Double.NaN);
    Assertions.assertEquals(0.0, v.getCurrent(now), 1e-12);
  }

  @Test
  public void minNegativeNaN() {
    final long now = clock.wallTime();
    StepDouble v = new StepDouble(Double.NaN, clock, 10L);
    v.min(now, -42.0);
    Assertions.assertEquals(-42.0, v.getCurrent(now), 1e-12);
  }

  @Test
  public void minValueInfinity() {
    final long now = clock.wallTime();
    StepDouble v = new StepDouble(0.0, clock, 10L);
    v.min(now, Double.NEGATIVE_INFINITY);
    Assertions.assertEquals(0.0, v.getCurrent(now), 1e-12);
  }

  @Test
  public void maxGt() {
    final long now = clock.wallTime();
    StepDouble v = new StepDouble(0.0, clock, 10L);
    v.max(now, 2.0);
    Assertions.assertEquals(2.0, v.getCurrent(now), 1e-12);
  }

  @Test
  public void maxLt() {
    final long now = clock.wallTime();
    StepDouble v = new StepDouble(2.0, clock, 10L);
    v.max(now, 0.0);
    Assertions.assertEquals(2.0, v.getCurrent(now), 1e-12);
  }

  @Test
  public void maxNegative() {
    final long now = clock.wallTime();
    StepDouble v = new StepDouble(-42.0, clock, 10L);
    v.max(now, -41.0);
    Assertions.assertEquals(-41.0, v.getCurrent(now), 1e-12);
  }

  @Test
  public void maxNaN() {
    final long now = clock.wallTime();
    StepDouble v = new StepDouble(Double.NaN, clock, 10L);
    v.max(now, 0.0);
    Assertions.assertEquals(0.0, v.getCurrent(now), 1e-12);
  }

  @Test
  public void maxValueNaN() {
    final long now = clock.wallTime();
    StepDouble v = new StepDouble(0.0, clock, 10L);
    v.max(now, Double.NaN);
    Assertions.assertEquals(0.0, v.getCurrent(now), 1e-12);
  }

  @Test
  public void maxNegativeNaN() {
    final long now = clock.wallTime();
    StepDouble v = new StepDouble(Double.NaN, clock, 10L);
    v.max(now, -42.0);
    Assertions.assertEquals(-42.0, v.getCurrent(now), 1e-12);
  }

  @Test
  public void maxValueInfinity() {
    final long now = clock.wallTime();
    StepDouble v = new StepDouble(0.0, clock, 10L);
    v.max(now, Double.POSITIVE_INFINITY);
    Assertions.assertEquals(0.0, v.getCurrent(now), 1e-12);
  }
}
