/*
 * Copyright 2014-2019 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spectator.servo;

import com.netflix.servo.annotations.DataSourceType;
import com.netflix.servo.monitor.MonitorConfig;
import com.netflix.servo.util.ManualClock;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DoubleCounterTest {

  private static final double DELTA = 1e-06;
  private final ManualClock clock = new ManualClock(ServoPollers.POLLING_INTERVALS[1]);

  private DoubleCounter newInstance(String name) {
    return new DoubleCounter(MonitorConfig.builder(name).build(), clock);
  }

  private long time(long t) {
    return t * 1000 + ServoPollers.POLLING_INTERVALS[1];
  }

  @Test
  public void testSimpleTransition() {
    clock.set(time(1));
    DoubleCounter c = newInstance("c");
    Assertions.assertEquals(0.0, c.getValue(1).doubleValue(), DELTA);

    clock.set(time(3));
    c.increment(1);
    Assertions.assertEquals(0.0, c.getValue(1).doubleValue(), DELTA);

    clock.set(time(9));
    c.increment(1);
    Assertions.assertEquals(0.0, c.getValue(1).doubleValue(), DELTA);

    clock.set(time(12));
    c.increment(1);
    Assertions.assertEquals(2.0 / 10.0, c.getValue(1).doubleValue(), DELTA);
  }


  @Test
  public void testInitialPollIsZero() {
    clock.set(time(1));
    DoubleCounter c = newInstance("foo");
    Assertions.assertEquals(0.0, c.getValue(1).doubleValue(), DELTA);
  }

  @Test
  public void testHasRightType() throws Exception {
    Assertions.assertEquals(newInstance("foo").getConfig().getTags().getValue(DataSourceType.KEY),
        "NORMALIZED");
  }

  @Test
  public void testBoundaryTransition() {
    clock.set(time(1));
    DoubleCounter c = newInstance("foo");

    // Should all go to one bucket
    c.increment(1);
    clock.set(time(4));
    c.increment(1);
    clock.set(time(9));
    c.increment(1);

    // Should cause transition
    clock.set(time(10));
    c.increment(1);
    clock.set(time(19));
    c.increment(1);

    // Check counts
    Assertions.assertEquals(0.3, c.getValue(1).doubleValue(), DELTA);
  }

  @Test
  public void testResetPreviousValue() {
    clock.set(time(1));
    DoubleCounter c = newInstance("foo");
    for (int i = 1; i <= 100000; ++i) {
      c.increment(1);
      clock.set(time(i * 10 + 1));
      Assertions.assertEquals(0.1, c.getValue(1).doubleValue(), DELTA);
    }
  }

  @Test
  public void testMissedInterval() {
    clock.set(time(1));
    DoubleCounter c = newInstance("foo");
    c.getValue(1);

    // Multiple updates without polling
    c.increment(1);
    clock.set(time(4));
    c.increment(1);
    clock.set(time(14));
    c.increment(1);
    clock.set(time(24));
    c.increment(1);
    clock.set(time(34));
    c.increment(1);

    // Check counts
    Assertions.assertTrue(Double.isNaN(c.getValue(1).doubleValue()));
  }

  @Test
  public void testNonMonotonicClock() {
    clock.set(time(1));
    DoubleCounter c = newInstance("foo");
    c.getValue(1);

    c.increment(1);
    c.increment(1);
    clock.set(time(10));
    c.increment(1);
    clock.set(time(9)); // Should get ignored
    c.increment(1);
    c.increment(1);
    clock.set(time(10));
    c.increment(1);
    c.increment(1);

    // Check rate for previous interval
    Assertions.assertEquals(0.2, c.getValue(1).doubleValue(), DELTA);
  }

  @Test
  public void testGetValueTwice() {
    ManualClock manualClock = new ManualClock(0L);

    DoubleCounter c = new DoubleCounter(MonitorConfig.builder("test").build(), manualClock);
    c.increment(1);
    for (int i = 1; i < 10; ++i) {
      manualClock.set(i * 60000L);
      c.increment(1);
      c.getValue(0);
      Assertions.assertEquals(1 / 60.0, c.getValue(0).doubleValue(), DELTA);
    }
  }
}
