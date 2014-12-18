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
package com.netflix.spectator.servo;

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.ManualClock;
import com.netflix.spectator.api.Registry;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.TimeUnit;

@RunWith(JUnit4.class)
public class ServoCounterTest {

  private final ManualClock clock = new ManualClock();

  private Counter newCounter(String name) {
    final Registry r = new ServoRegistry(clock);
    return r.counter(r.createId(name));
  }

  @Before
  public void before() {
    clock.setWallTime(0L);
    clock.setMonotonicTime(0L);
  }

  @Test
  public void testInit() {
    Counter c = newCounter("foo");
    Assert.assertEquals(c.count(), 0L);
    c.increment();
    Assert.assertEquals(c.count(), 1L);
  }

  @Test
  public void expiration() {
    // Not expired on init
    clock.setWallTime(0L);
    Counter c = newCounter("foo");
    Assert.assertTrue(!c.hasExpired());
    c.increment(42);

    // Expires with inactivity, total count in memory is maintained
    clock.setWallTime(TimeUnit.MINUTES.toMillis(15));
    Assert.assertTrue(!c.hasExpired());

    // Expires with inactivity, total count in memory is maintained
    clock.setWallTime(TimeUnit.MINUTES.toMillis(15) + 1);
    Assert.assertEquals(c.count(), 42);
    Assert.assertTrue(c.hasExpired());

    // Activity brings it back
    c.increment();
    Assert.assertEquals(c.count(), 43);
    Assert.assertTrue(!c.hasExpired());
  }

}
