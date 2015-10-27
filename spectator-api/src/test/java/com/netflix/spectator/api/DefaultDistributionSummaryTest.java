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
package com.netflix.spectator.api;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DefaultDistributionSummaryTest {

  private final ManualClock clock = new ManualClock();

  @Test
  public void testInit() {
    DistributionSummary t = new DefaultDistributionSummary(clock, NoopId.INSTANCE);
    Assert.assertEquals(t.count(), 0L);
    Assert.assertEquals(t.totalAmount(), 0L);
    Assert.assertFalse(t.hasExpired());
  }

  @Test
  public void testRecord() {
    DistributionSummary t = new DefaultDistributionSummary(clock, NoopId.INSTANCE);
    t.record(42);
    Assert.assertEquals(t.count(), 1L);
    Assert.assertEquals(t.totalAmount(), 42L);
  }

  @Test
  public void testRecordNegative() {
    DistributionSummary t = new DefaultDistributionSummary(clock, NoopId.INSTANCE);
    t.record(-42);
    Assert.assertEquals(t.count(), 0L);
    Assert.assertEquals(t.totalAmount(), 0L);
  }

  @Test
  public void testRecordZero() {
    DistributionSummary t = new DefaultDistributionSummary(clock, NoopId.INSTANCE);
    t.record(0);
    Assert.assertEquals(t.count(), 1L);
    Assert.assertEquals(t.totalAmount(), 0L);
  }

  @Test
  public void testMeasure() {
    DistributionSummary t = new DefaultDistributionSummary(clock, new DefaultId("foo"));
    t.record(42);
    clock.setWallTime(3712345L);
    for (Measurement m : t.measure()) {
      Assert.assertEquals(m.timestamp(), 3712345L);
      if (m.id().equals(t.id().withTag(Statistic.count))) {
        Assert.assertEquals(m.value(), 1.0, 0.1e-12);
      } else if (m.id().equals(t.id().withTag(Statistic.totalAmount))) {
        Assert.assertEquals(m.value(), 42.0, 0.1e-12);
      } else {
        Assert.fail("unexpected id: " + m.id());
      }
    }
  }

}
