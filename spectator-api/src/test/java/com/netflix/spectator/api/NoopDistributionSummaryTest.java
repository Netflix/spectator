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

public class NoopDistributionSummaryTest {
  @Test
  public void testId() {
    Assertions.assertEquals(NoopDistributionSummary.INSTANCE.id(), NoopId.INSTANCE);
    Assertions.assertFalse(NoopDistributionSummary.INSTANCE.hasExpired());
  }

  @Test
  public void testIncrement() {
    NoopDistributionSummary t = NoopDistributionSummary.INSTANCE;
    t.record(42);
    Assertions.assertEquals(t.count(), 0L);
    Assertions.assertEquals(t.totalAmount(), 0L);
  }

  @Test
  public void testMeasure() {
    NoopDistributionSummary t = NoopDistributionSummary.INSTANCE;
    t.record(42);
    Assertions.assertFalse(t.measure().iterator().hasNext());
    t.record(new long[] { 1L, 2L, 3L }, 3);
    Assertions.assertFalse(t.measure().iterator().hasNext());
  }

  @Test
  public void testBatchUpdate() throws Exception {
    NoopDistributionSummary t = NoopDistributionSummary.INSTANCE;
    try (DistributionSummary.BatchUpdater batchUpdater = t.batchUpdater(10)) {
      for (long i = 0; i < 100; i++) {
        batchUpdater.record(i);
      }
    }
    Assertions.assertFalse(t.measure().iterator().hasNext());
  }
}
