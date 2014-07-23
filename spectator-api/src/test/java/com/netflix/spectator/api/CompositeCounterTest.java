/**
 * Copyright 2014 Netflix, Inc.
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

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CompositeCounterTest {

  private final ManualClock clock = new ManualClock();

  private Counter newCounter(int n) {
    Counter[] ms = new Counter[n];
    for (int i = 0; i < n; ++i) {
      ms[i] = new DefaultCounter(clock, NoopId.INSTANCE);
    }
    return new CompositeCounter(NoopId.INSTANCE, ms);
  }

  private void assertCountEquals(Counter c, long expected) {
    Assert.assertEquals(c.count(), expected);
    for (Meter m : ((CompositeCounter) c).meters()) {
      Assert.assertEquals(((Counter) m).count(), expected);
    }
  }

  @Test
  public void empty() {
    Counter c = new CompositeCounter(NoopId.INSTANCE, new Counter[] {});
    assertCountEquals(c, 0L);
    c.increment();
    assertCountEquals(c, 0L);
  }

  @Test
  public void init() {
    Counter c = newCounter(5);
    assertCountEquals(c, 0L);
  }

  @Test
  public void increment() {
    Counter c = newCounter(5);
    c.increment();
    assertCountEquals(c, 1L);
    c.increment();
    c.increment();
    assertCountEquals(c, 3L);
  }

  @Test
  public void incrementAmount() {
    Counter c = newCounter(5);
    c.increment(42);
    assertCountEquals(c, 42L);
  }

  @Test
  public void measure() {
    Counter c = newCounter(5);
    c.increment(42);
    clock.setWallTime(3712345L);
    for (Measurement m : c.measure()) {
      Assert.assertEquals(m.id(), c.id());
      Assert.assertEquals(m.timestamp(), 3712345L);
      Assert.assertEquals(m.value(), 42.0, 0.1e-12);
    }
  }

}
