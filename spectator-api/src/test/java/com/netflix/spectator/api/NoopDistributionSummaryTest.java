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
public class NoopDistributionSummaryTest {
  @Test
  public void testId() {
    Assert.assertEquals(NoopDistributionSummary.INSTANCE.id(), NoopId.INSTANCE);
    Assert.assertTrue(NoopDistributionSummary.INSTANCE.hasExpired());
  }

  @Test
  public void testIncrement() {
    NoopDistributionSummary t = NoopDistributionSummary.INSTANCE;
    t.record(42);
    Assert.assertEquals(t.count(), 0L);
    Assert.assertEquals(t.totalAmount(), 0L);
  }

  @Test
  public void testMeasure() {
    NoopDistributionSummary t = NoopDistributionSummary.INSTANCE;
    t.record(42);
    Assert.assertFalse(t.measure().iterator().hasNext());
  }

}
