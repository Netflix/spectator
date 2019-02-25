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
package com.netflix.spectator.api.histogram;

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Registry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PercentileDistributionSummaryTest {

  private Registry newRegistry() {
    return new DefaultRegistry(Clock.SYSTEM, k -> null);
  }

  private void checkPercentiles(PercentileDistributionSummary t, int start) {
    for (int i = 0; i < 100_000; ++i) {
      t.record(i);
    }
    for (int i = start; i <= 100; ++i) {
      double expected = i * 1000.0;
      double threshold = 0.15 * expected + 1e-12;
      Assertions.assertEquals(expected, t.percentile(i), threshold);
    }
  }

  @Test
  public void percentile() {
    Registry r = newRegistry();
    PercentileDistributionSummary t = PercentileDistributionSummary.get(r, r.createId("test"));
    checkPercentiles(t, 0);
  }

  @Test
  public void builder() {
    Registry r = newRegistry();
    PercentileDistributionSummary t = PercentileDistributionSummary.builder(r)
        .withName("test")
        .build();
    checkPercentiles(t, 0);
  }

  @Test
  public void builderWithThreshold() {
    Registry r = newRegistry();
    PercentileDistributionSummary t = PercentileDistributionSummary.builder(r)
        .withName("test")
        .withRange(25_000, 100_000)
        .build();
    checkPercentiles(t, 25);
  }

  private void checkValue(PercentileDistributionSummary t1, PercentileDistributionSummary t2, double expected) {
    Assertions.assertEquals(expected, t1.percentile(99.0), expected / 5.0);
    Assertions.assertEquals(expected, t2.percentile(99.0), expected / 5.0);
  }

  @Test
  public void builderWithDifferentThresholds() {
    Registry r = newRegistry();
    PercentileDistributionSummary t1 = PercentileDistributionSummary.builder(r)
        .withName("test")
        .withRange(10, 50)
        .build();
    PercentileDistributionSummary t2 = PercentileDistributionSummary.builder(r)
        .withName("test")
        .withRange(100, 200)
        .build();

    t1.record(5);
    checkValue(t1, t2, 10.0);

    t1.record(500);
    checkValue(t1, t2, 50.0);

    t2.record(5);
    checkValue(t1, t2, 100.0);

    t2.record(500);
    checkValue(t1, t2, 200.0);
  }
}
