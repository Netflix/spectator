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
package com.netflix.spectator.impl;

import com.netflix.spectator.api.ManualClock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class StepLongTest {

  private final ManualClock clock = new ManualClock();

  @Before
  public void init() {
    clock.setWallTime(0L);
  }

  @Test
  public void empty() {
    StepLong v = new StepLong(0L, clock, 10L);
    Assert.assertEquals(0L, v.getCurrent().get());
    Assert.assertEquals(0L, v.poll());
  }

  @Test
  public void increment() {
    StepLong v = new StepLong(0L, clock, 10L);
    v.getCurrent().incrementAndGet();
    Assert.assertEquals(1L, v.getCurrent().get());
    Assert.assertEquals(0L, v.poll());
  }

  @Test
  public void incrementAndCrossStepBoundary() {
    StepLong v = new StepLong(0L, clock, 10L);
    v.getCurrent().incrementAndGet();
    clock.setWallTime(10L);
    Assert.assertEquals(0L, v.getCurrent().get());
    Assert.assertEquals(1L, v.poll());
  }

  @Test
  public void missedRead() {
    StepLong v = new StepLong(0L, clock, 10L);
    v.getCurrent().incrementAndGet();
    clock.setWallTime(20L);
    Assert.assertEquals(0L, v.getCurrent().get());
    Assert.assertEquals(0L, v.poll());
  }
}
