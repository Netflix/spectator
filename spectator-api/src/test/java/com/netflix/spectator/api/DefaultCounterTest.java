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

public class DefaultCounterTest {

  private final ManualClock clock = new ManualClock();

  @Test
  public void testInit() {
    Counter c = new DefaultCounter(clock, NoopId.INSTANCE);
    Assertions.assertEquals(c.count(), 0L);
  }

  @Test
  public void testIncrement() {
    Counter c = new DefaultCounter(clock, NoopId.INSTANCE);
    c.increment();
    Assertions.assertEquals(c.count(), 1L);
    c.increment();
    c.increment();
    Assertions.assertEquals(c.count(), 3L);
  }

  @Test
  public void testIncrementAmount() {
    Counter c = new DefaultCounter(clock, NoopId.INSTANCE);
    c.increment(42);
    Assertions.assertEquals(c.count(), 42L);
  }

  @Test
  public void testAddAmount() {
    Counter c = new DefaultCounter(clock, NoopId.INSTANCE);
    c.add(42.0);
    Assertions.assertEquals(c.actualCount(), 42.0, 1e-12);
  }

  @Test
  public void testAddNegativeAmount() {
    Counter c = new DefaultCounter(clock, NoopId.INSTANCE);
    c.add(-42.0);
    Assertions.assertEquals(c.actualCount(), 0.0, 1e-12);
  }

  @Test
  public void testAddNaN() {
    Counter c = new DefaultCounter(clock, NoopId.INSTANCE);
    c.add(1.0);
    c.add(Double.NaN);
    Assertions.assertEquals(c.actualCount(), 1.0, 1e-12);
  }

  @Test
  public void testAddInfinity() {
    Counter c = new DefaultCounter(clock, NoopId.INSTANCE);
    c.add(Double.POSITIVE_INFINITY);
    Assertions.assertEquals(c.actualCount(), 0.0, 1e-12);
  }

  @Test
  public void testMeasure() {
    Counter c = new DefaultCounter(clock, NoopId.INSTANCE);
    c.increment(42);
    clock.setWallTime(3712345L);
    for (Measurement m : c.measure()) {
      Assertions.assertEquals(m.id(), c.id());
      Assertions.assertEquals(m.timestamp(), 3712345L);
      Assertions.assertEquals(m.value(), 42.0, 0.1e-12);
    }
  }

}
