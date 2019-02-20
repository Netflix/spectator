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
package com.netflix.spectator.api;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DefaultDistributionSummaryTest {

  private final ManualClock clock = new ManualClock();

  @Test
  public void testInit() {
    DistributionSummary t = new DefaultDistributionSummary(clock, NoopId.INSTANCE);
    Assertions.assertEquals(t.count(), 0L);
    Assertions.assertEquals(t.totalAmount(), 0L);
    Assertions.assertFalse(t.hasExpired());
  }

  @Test
  public void testRecord() {
    DistributionSummary t = new DefaultDistributionSummary(clock, NoopId.INSTANCE);
    t.record(42);
    Assertions.assertEquals(t.count(), 1L);
    Assertions.assertEquals(t.totalAmount(), 42L);
  }

  @Test
  public void testRecordNegative() {
    DistributionSummary t = new DefaultDistributionSummary(clock, NoopId.INSTANCE);
    t.record(-42);
    Assertions.assertEquals(t.count(), 0L);
    Assertions.assertEquals(t.totalAmount(), 0L);
  }

  @Test
  public void testRecordZero() {
    DistributionSummary t = new DefaultDistributionSummary(clock, NoopId.INSTANCE);
    t.record(0);
    Assertions.assertEquals(t.count(), 1L);
    Assertions.assertEquals(t.totalAmount(), 0L);
  }

  @Test
  public void testMeasure() {
    DistributionSummary t = new DefaultDistributionSummary(clock, new DefaultId("foo"));
    t.record(42);
    clock.setWallTime(3712345L);
    for (Measurement m : t.measure()) {
      Assertions.assertEquals(m.timestamp(), 3712345L);
      if (m.id().equals(t.id().withTag(Statistic.count))) {
        Assertions.assertEquals(m.value(), 1.0, 0.1e-12);
      } else if (m.id().equals(t.id().withTag(Statistic.totalAmount))) {
        Assertions.assertEquals(m.value(), 42.0, 0.1e-12);
      } else {
        Assertions.fail("unexpected id: " + m.id());
      }
    }
  }

}
