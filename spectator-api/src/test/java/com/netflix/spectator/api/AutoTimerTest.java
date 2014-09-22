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

import java.util.concurrent.TimeUnit;


@RunWith(JUnit4.class)
public class AutoTimerTest {

  private final ManualClock clock = new ManualClock();

  @Test
  public void testInit() {
    Timer t = new DefaultTimer(clock, NoopId.INSTANCE);
    Assert.assertEquals(t.count(), 0L);
    Assert.assertEquals(t.totalTime(), 0L);
  }

  @Test
  public void testRecord() {
    Timer t = new DefaultTimer(clock, NoopId.INSTANCE);
    try (AutoTimer at = new AutoTimer(t, clock)) {
      Assert.assertEquals(at.duration(TimeUnit.MICROSECONDS), 0L);
      clock.setMonotonicTime(42000L);
      Assert.assertEquals(at.duration(TimeUnit.MICROSECONDS), 42L);
    }
    Assert.assertEquals(t.count(), 1L);
    Assert.assertEquals(t.totalTime(), 42000L);
  }

  @Test
  public void testRecordWithException() {
    Timer t = new DefaultTimer(clock, NoopId.INSTANCE);
    try {
      try (AutoTimer at = new AutoTimer(t, clock)) {
        Assert.assertEquals(at.duration(TimeUnit.MICROSECONDS), 0L);
        clock.setMonotonicTime(42000L);
        Assert.assertEquals(at.duration(TimeUnit.MICROSECONDS), 42L);
        throw new Exception("die");
      }
    } catch (Exception e) {
    }
    Assert.assertEquals(t.count(), 1L);
    Assert.assertEquals(t.totalTime(), 42000L);
  }

}

