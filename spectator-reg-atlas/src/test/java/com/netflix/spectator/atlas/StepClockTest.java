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
package com.netflix.spectator.atlas;

import com.netflix.spectator.api.ManualClock;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


public class StepClockTest {

  @Test
  public void wallTime() {
    ManualClock mc = new ManualClock();
    StepClock sc = new StepClock(mc, 10000);

    mc.setWallTime(5000);
    Assertions.assertEquals(0L, sc.wallTime());

    mc.setWallTime(10000);
    Assertions.assertEquals(10000L, sc.wallTime());

    mc.setWallTime(20212);
    Assertions.assertEquals(20000L, sc.wallTime());
  }

  @Test
  public void monotonicTime() {
    ManualClock mc = new ManualClock();
    StepClock sc = new StepClock(mc, 10000);

    mc.setMonotonicTime(5000);
    Assertions.assertEquals(5000L, sc.monotonicTime());

    mc.setMonotonicTime(10000);
    Assertions.assertEquals(10000L, sc.monotonicTime());

    mc.setMonotonicTime(20212);
    Assertions.assertEquals(20212L, sc.monotonicTime());
  }
}
