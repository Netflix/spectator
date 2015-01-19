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
public class CompositeDistributionSummaryTest {

  private final ManualClock clock = new ManualClock();

  private DistributionSummary newDistributionSummary(int n) {
    DistributionSummary[] ms = new DistributionSummary[n];
    for (int i = 0; i < n; ++i) {
      ms[i] = new DefaultDistributionSummary(clock, new DefaultId("foo"));
    }
    return new CompositeDistributionSummary(new DefaultId("foo"), ms);
  }

  private void assertCountEquals(DistributionSummary t, long expected) {
    Assert.assertEquals(t.count(), expected);
    for (Meter m : ((CompositeDistributionSummary) t).meters()) {
      Assert.assertEquals(((DistributionSummary) m).count(), expected);
    }
  }

  private void assertTotalEquals(DistributionSummary t, long expected) {
    Assert.assertEquals(t.totalAmount(), expected);
    for (Meter m : ((CompositeDistributionSummary) t).meters()) {
      Assert.assertEquals(((DistributionSummary) m).totalAmount(), expected);
    }
  }

  @Test
  public void empty() {
    DistributionSummary t = new CompositeDistributionSummary(
      NoopId.INSTANCE, new DistributionSummary[] {});
    assertCountEquals(t, 0L);
    assertTotalEquals(t, 0L);
    t.record(1L);
    assertCountEquals(t, 0L);
    assertTotalEquals(t, 0L);
  }

  @Test
  public void testInit() {
    DistributionSummary t = newDistributionSummary(5);
    assertCountEquals(t, 0L);
    assertTotalEquals(t, 0L);
  }

  @Test
  public void testRecord() {
    DistributionSummary t = newDistributionSummary(5);
    t.record(42);
    assertCountEquals(t, 1L);
    assertTotalEquals(t, 42L);
  }

  @Test
  public void testMeasure() {
    DistributionSummary t = newDistributionSummary(5);
    t.record(42);
    clock.setWallTime(3712345L);
    for (Measurement m : t.measure()) {
      Assert.assertEquals(m.timestamp(), 3712345L);
      if (m.id().equals(t.id().withTag(Statistic.count))) {
        Assert.assertEquals(m.value(), 1.0, 0.1e-12);
      } else if (m.id().equals(t.id().withTag(Statistic.totalAmount))) {
        Assert.assertEquals(m.value(), 42, 0.1e-12);
      } else {
        Assert.fail("unexpected id: " + m.id());
      }
    }
  }
}
