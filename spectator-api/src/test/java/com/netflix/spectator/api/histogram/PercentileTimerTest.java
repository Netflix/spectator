/*
 * Copyright 2014-2018 Netflix, Inc.
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
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@RunWith(JUnit4.class)
public class PercentileTimerTest {

  private Registry newRegistry() {
    return new DefaultRegistry(Clock.SYSTEM, k -> null);
  }

  private void checkPercentiles(PercentileTimer t, int start) {
    for (int i = 0; i < 100_000; ++i) {
      t.record(i, TimeUnit.MILLISECONDS);
    }
    for (int i = start; i <= 100; ++i) {
      double expected = (double) i;
      double threshold = 0.15 * expected;
      Assert.assertEquals(expected, t.percentile(i), threshold);
    }
  }

  @Test
  public void percentile() {
    Registry r = newRegistry();
    PercentileTimer t = PercentileTimer.get(r, r.createId("test"));
    checkPercentiles(t, 0);
  }

  @Test
  public void builderWithThreshold() {
    Registry r = newRegistry();
    PercentileTimer t = PercentileTimer.builder(r)
        .withName("test")
        .withRange(10, 100, TimeUnit.SECONDS)
        .build();
    checkPercentiles(t, 10);
  }

  @Test
  public void builderWithThresholdDuration() {
    Registry r = newRegistry();
    PercentileTimer t = PercentileTimer.builder(r)
        .withName("test")
        .withRange(Duration.ZERO, Duration.ofSeconds(100))
        .build();
    checkPercentiles(t, 0);
  }
}
