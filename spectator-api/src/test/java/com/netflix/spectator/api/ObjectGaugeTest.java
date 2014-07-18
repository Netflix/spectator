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

import java.util.concurrent.atomic.AtomicLong;

@RunWith(JUnit4.class)
public class ObjectGaugeTest {

  private final ManualClock clock = new ManualClock();

  @Test
  public void testGC() {
    ObjectGauge g = new ObjectGauge(
      clock, NoopId.INSTANCE, new AtomicLong(42L), Functions.IDENTITY);
    for (Measurement m : g.measure()) {
      Assert.assertEquals(m.value(), 42.0, 1e-12);
    }

    // Verify we get NaN after gc, this is quite possibly flakey and can be commented out
    // if needed
    System.gc();
    Assert.assertTrue(g.hasExpired());
    for (Measurement m : g.measure()) {
      Assert.assertEquals(m.value(), Double.NaN, 1e-12);
    }
  }
}
