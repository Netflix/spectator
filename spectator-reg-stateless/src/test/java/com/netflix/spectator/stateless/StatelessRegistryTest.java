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
package com.netflix.spectator.stateless;

import com.netflix.spectator.api.ManualClock;
import com.netflix.spectator.api.Measurement;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;


@RunWith(JUnit4.class)
public class StatelessRegistryTest {

  private ManualClock clock = new ManualClock();
  private StatelessRegistry registry = new StatelessRegistry(clock, newConfig());

  private StatelessConfig newConfig() {
    ConcurrentHashMap<String, String> props = new ConcurrentHashMap<>();
    props.put("stateless.frequency", "PT10S");
    props.put("stateless.batchSize", "3");
    return props::get;
  }

  @Test
  public void measurementsEmpty() {
    Assert.assertEquals(0, registry.getMeasurements().size());
  }

  @Test
  public void measurementsWithCounter() {
    registry.counter("test").increment();
    Assert.assertEquals(1, registry.getMeasurements().size());
  }

  @Test
  public void measurementsWithTimer() {
    registry.timer("test").record(42, TimeUnit.NANOSECONDS);
    Assert.assertEquals(4, registry.getMeasurements().size());
  }

  @Test
  public void measurementsWithDistributionSummary() {
    registry.distributionSummary("test").record(42);
    Assert.assertEquals(4, registry.getMeasurements().size());
  }

  @Test
  public void measurementsWithGauge() {
    registry.gauge("test").set(4.0);
    Assert.assertEquals(1, registry.getMeasurements().size());
  }

  @Test
  public void measurementsWithMaxGauge() {
    registry.maxGauge(registry.createId("test")).set(4.0);
    Assert.assertEquals(1, registry.getMeasurements().size());
  }

  @Test
  public void batchesEmpty() {
    Assert.assertEquals(0, registry.getBatches().size());
  }

  @Test
  public void batchesExact() {
    for (int i = 0; i < 9; ++i) {
      registry.counter("" + i).increment();
    }
    Assert.assertEquals(3, registry.getBatches().size());
    for (List<Measurement> batch : registry.getBatches()) {
      Assert.assertEquals(3, batch.size());
    }
  }

  @Test
  public void batchesLastPartial() {
    for (int i = 0; i < 7; ++i) {
      registry.counter("" + i).increment();
    }
    List<List<Measurement>> batches = registry.getBatches();
    Assert.assertEquals(3, batches.size());
    for (int i = 0; i < batches.size(); ++i) {
      Assert.assertEquals((i < 2) ? 3 : 1, batches.get(i).size());
    }
  }

  @Test
  public void batchesExpiration() {
    for (int i = 0; i < 9; ++i) {
      registry.counter("" + i).increment();
    }
    Assert.assertEquals(3, registry.getBatches().size());
    for (List<Measurement> batch : registry.getBatches()) {
      Assert.assertEquals(3, batch.size());
    }

    clock.setWallTime(Duration.ofMinutes(15).toMillis() + 1);
    Assert.assertEquals(0, registry.getBatches().size());
  }

}
