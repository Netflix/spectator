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
package com.netflix.spectator.placeholders;

import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.DistributionSummary;
import com.netflix.spectator.api.ManualClock;
import com.netflix.spectator.api.Registry;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Collections;

/**
 * Unit tests for the DefaultDynamicDistributionSummary class.
 */
@RunWith(JUnit4.class)
public class DefaultDynamicDistributionSummaryTest {
  private final ManualClock clock = new ManualClock();
  private final Registry registry = new DefaultRegistry(clock);
  private final PlaceholderFactory factory = PlaceholderFactory.from(registry);

  @Test
  public void testInit() {
    DistributionSummary summary = new DefaultDynamicDistributionSummary(new DefaultDynamicId("testInit"), registry);

    Assert.assertEquals(0L, summary.count());
    Assert.assertEquals(0L, summary.totalAmount());
  }

  @Test
  public void testRecord() {
    String[] tagValue = new String[] { "default" };
    DistributionSummary summary = factory.distributionSummary(factory.createId("testRecord",
            Collections.singleton(new TestTagFactory(tagValue))));

    summary.record(42L);
    Assert.assertEquals("testRecord:tag=default", summary.id().toString());
    Assert.assertEquals(summary.count(), 1L);
    Assert.assertEquals(42L, summary.totalAmount());

    tagValue[0] = "value2";
    Assert.assertEquals("testRecord:tag=value2", summary.id().toString());
    Assert.assertEquals(0L, summary.count());
    Assert.assertEquals(0L, summary.totalAmount());
  }

  @Test
  public void testRecordNegative() {
    DistributionSummary summary = factory.distributionSummary(factory.createId("testRecordNegative"));

    summary.record(-42L);
    Assert.assertEquals(summary.count(), 0L);
    Assert.assertEquals(0L, summary.totalAmount());
  }

  @Test
  public void testRecordZero() {
    DistributionSummary summary = factory.distributionSummary(factory.createId("testRecordNegative"));

    summary.record(0);
    Assert.assertEquals(summary.count(), 1L);
    Assert.assertEquals(summary.totalAmount(), 0L);
  }
}
