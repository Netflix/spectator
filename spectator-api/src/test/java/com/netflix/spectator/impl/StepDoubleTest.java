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
    Assertions.assertEquals(0.0, v.getCurrent().get(), 1e-12);
    Assertions.assertEquals(0.0, v.poll(), 1e-12);
  }

  @Test
  public void increment() {
    StepDouble v = new StepDouble(0.0, clock, 10L);
    v.getCurrent().addAndGet(1.0);
    Assertions.assertEquals(1.0, v.getCurrent().get(), 1e-12);
    Assertions.assertEquals(0.0, v.poll(), 1e-12);
  }

  @Test
  public void incrementAndCrossStepBoundary() {
    StepDouble v = new StepDouble(0.0, clock, 10L);
    v.getCurrent().addAndGet(1.0);
    clock.setWallTime(10L);
    Assertions.assertEquals(0.0, v.getCurrent().get(), 1e-12);
    Assertions.assertEquals(1.0, v.poll(), 1e-12);
  }

  @Test
  public void missedRead() {
    StepDouble v = new StepDouble(0.0, clock, 10L);
    v.getCurrent().addAndGet(1.0);
    clock.setWallTime(20L);
    Assertions.assertEquals(0.0, v.getCurrent().get(), 1e-12);
    Assertions.assertEquals(0.0, v.poll(), 1e-12);
  }
}
