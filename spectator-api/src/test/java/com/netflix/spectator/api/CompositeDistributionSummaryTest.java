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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class CompositeDistributionSummaryTest {

  private final ManualClock clock = new ManualClock();

  private final Id id = new DefaultId("foo");
  private List<Registry> registries;

  private DistributionSummary newDistributionSummary() {
    List<DistributionSummary> ds = registries.stream()
        .map(r -> r.distributionSummary(id))
        .collect(Collectors.toList());
    return new CompositeDistributionSummary(id, ds);
  }

  private void assertCountEquals(DistributionSummary t, long expected) {
    Assertions.assertEquals(t.count(), expected);
    for (Registry r : registries) {
      Assertions.assertEquals(r.distributionSummary(id).count(), expected);
    }
  }

  private void assertTotalEquals(DistributionSummary t, long expected) {
    Assertions.assertEquals(t.totalAmount(), expected);
    for (Registry r : registries) {
      Assertions.assertEquals(r.distributionSummary(id).totalAmount(), expected);
    }
  }

  @BeforeEach
  public void init() {
    registries = new ArrayList<>();
    for (int i = 0; i < 5; ++i) {
      registries.add(new DefaultRegistry(clock));
    }
  }

  @Test
  public void empty() {
    DistributionSummary t = new CompositeDistributionSummary(
      NoopId.INSTANCE, Collections.emptyList());
    assertCountEquals(t, 0L);
    assertTotalEquals(t, 0L);
    t.record(1L);
    assertCountEquals(t, 0L);
    assertTotalEquals(t, 0L);
  }

  @Test
  public void testInit() {
    DistributionSummary t = newDistributionSummary();
    assertCountEquals(t, 0L);
    assertTotalEquals(t, 0L);
  }

  @Test
  public void testRecord() {
    DistributionSummary t = newDistributionSummary();
    t.record(42);
    assertCountEquals(t, 1L);
    assertTotalEquals(t, 42L);
  }

  @Test
  public void testMeasure() {
    DistributionSummary t = newDistributionSummary();
    t.record(42);
    clock.setWallTime(3712345L);
    for (Measurement m : t.measure()) {
      Assertions.assertEquals(m.timestamp(), 3712345L);
      if (m.id().equals(t.id().withTag(Statistic.count))) {
        Assertions.assertEquals(m.value(), 1.0, 0.1e-12);
      } else if (m.id().equals(t.id().withTag(Statistic.totalAmount))) {
        Assertions.assertEquals(m.value(), 42, 0.1e-12);
      } else {
        Assertions.fail("unexpected id: " + m.id());
      }
    }
  }
}
