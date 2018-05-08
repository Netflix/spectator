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
package com.netflix.spectator.atlas;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.ManualClock;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AtlasSwapMeterTest {
  private final ManualClock clock = new ManualClock();
  private final AtlasRegistry registry = new AtlasRegistry(clock, System::getProperty);

  private final Id gaugeId = registry.createId("gauge");
  private final MaxGauge gauge = registry.maxGauge(gaugeId);

  @Test
  public void gaugeNullHasExpired() {
    SwapMaxGauge sg = new SwapMaxGauge(registry, gaugeId, null);
    Assert.assertTrue(sg.hasExpired());
  }

  @Test
  public void gaugeNullLookup() {
    SwapMaxGauge sg = new SwapMaxGauge(registry, gaugeId, null);
    sg.set(42.0);
    Assert.assertEquals(gauge.value(), sg.get().value(), 1e-12);
  }

  @Test
  public void gaugeSetToNull() {
    SwapMaxGauge sg = new SwapMaxGauge(registry, gaugeId, gauge);
    Assert.assertFalse(sg.hasExpired());
    sg.set(null);
    Assert.assertTrue(sg.hasExpired());
    Assert.assertEquals(gauge.value(), sg.get().value(), 1e-12);
  }
}
