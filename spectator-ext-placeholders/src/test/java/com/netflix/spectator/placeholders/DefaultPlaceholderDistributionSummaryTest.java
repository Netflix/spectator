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
package com.netflix.spectator.placeholders;

import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.DistributionSummary;
import com.netflix.spectator.api.ManualClock;
import com.netflix.spectator.api.Registry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;

/**
 * Unit tests for the DefaultPlaceholderDistributionSummary class.
 */
public class DefaultPlaceholderDistributionSummaryTest {
  private final ManualClock clock = new ManualClock();
  private final Registry registry = new DefaultRegistry(clock);
  private final PlaceholderFactory factory = PlaceholderFactory.from(registry);

  @Test
  public void testInit() {
    DistributionSummary summary = new DefaultPlaceholderDistributionSummary(new DefaultPlaceholderId("testInit", registry), registry);

    Assertions.assertEquals(0L, summary.count());
    Assertions.assertEquals(0L, summary.totalAmount());
  }

  @Test
  public void testRecord() {
    String[] tagValue = new String[] { "default" };
    DistributionSummary summary = factory.distributionSummary(factory.createId("testRecord",
            Collections.singleton(new TestTagFactory(tagValue))));

    summary.record(42L);
    Assertions.assertEquals("testRecord:tag=default", summary.id().toString());
    Assertions.assertEquals(summary.count(), 1L);
    Assertions.assertEquals(42L, summary.totalAmount());

    tagValue[0] = "value2";
    Assertions.assertEquals("testRecord:tag=value2", summary.id().toString());
    Assertions.assertEquals(0L, summary.count());
    Assertions.assertEquals(0L, summary.totalAmount());
  }

  @Test
  public void testRecordNegative() {
    DistributionSummary summary = factory.distributionSummary(factory.createId("testRecordNegative"));

    summary.record(-42L);
    Assertions.assertEquals(summary.count(), 0L);
    Assertions.assertEquals(0L, summary.totalAmount());
  }

  @Test
  public void testRecordZero() {
    DistributionSummary summary = factory.distributionSummary(factory.createId("testRecordNegative"));

    summary.record(0);
    Assertions.assertEquals(summary.count(), 1L);
    Assertions.assertEquals(summary.totalAmount(), 0L);
  }
}
