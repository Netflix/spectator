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
package com.netflix.spectator.atlas;

import com.netflix.spectator.api.ManualClock;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


public class AtlasRegistryTest {

  private ManualClock clock = new ManualClock();
  private AtlasRegistry registry = new AtlasRegistry(clock, newConfig());

  private AtlasConfig newConfig() {
    Map<String, String> props = new LinkedHashMap<>();
    props.put("atlas.enabled", "false");
    props.put("atlas.step", "PT10S");
    props.put("atlas.batchSize", "3");

    return new AtlasConfig() {
      @Override public String get(String k) {
        return props.get(k);
      }

      @Override public Registry debugRegistry() {
        return new NoopRegistry();
      }
    };
  }

  private List<Measurement> getMeasurements() {
    return registry.measurements().collect(Collectors.toList());
  }

  @Test
  public void measurementsEmpty() {
    Assertions.assertEquals(0, getMeasurements().size());
  }

  @Test
  public void measurementsWithCounter() {
    registry.counter("test").increment();
    Assertions.assertEquals(1, getMeasurements().size());
  }

  @Test
  public void measurementsWithTimer() {
    registry.timer("test").record(42, TimeUnit.NANOSECONDS);
    Assertions.assertEquals(4, getMeasurements().size());
  }

  @Test
  public void measurementsWithDistributionSummary() {
    registry.distributionSummary("test").record(42);
    Assertions.assertEquals(4, getMeasurements().size());
  }

  @Test
  public void measurementsWithGauge() {
    registry.gauge("test").set(4.0);
    Assertions.assertEquals(1, getMeasurements().size());
  }

  @Test
  public void measurementsIgnoresNaN() {
    registry.gauge("test").set(Double.NaN);
    Assertions.assertEquals(0, getMeasurements().size());
  }

  @Test
  public void measurementsWithMaxGauge() {
    registry.maxGauge(registry.createId("test")).set(4.0);
    Assertions.assertEquals(1, getMeasurements().size());
  }

  @Test
  public void batchesEmpty() {
    Assertions.assertEquals(0, registry.getBatches().size());
  }

  @Test
  public void batchesExact() {
    for (int i = 0; i < 9; ++i) {
      registry.counter("" + i).increment();
    }
    Assertions.assertEquals(3, registry.getBatches().size());
    for (List<Measurement> batch : registry.getBatches()) {
      Assertions.assertEquals(3, batch.size());
    }
  }

  @Test
  public void batchesLastPartial() {
    for (int i = 0; i < 7; ++i) {
      registry.counter("" + i).increment();
    }
    List<List<Measurement>> batches = registry.getBatches();
    Assertions.assertEquals(3, batches.size());
    for (int i = 0; i < batches.size(); ++i) {
      Assertions.assertEquals((i < 2) ? 3 : 1, batches.get(i).size());
    }
  }

  @Test
  public void initialDelayTooCloseToStart() {
    long d = registry.getInitialDelay(10000);
    Assertions.assertEquals(1000, d);
  }

  @Test
  public void initialDelayTooCloseToEnd() {
    clock.setWallTime(19123);
    long d = registry.getInitialDelay(10000);
    Assertions.assertEquals(9000, d);
  }

  @Test
  public void initialDelayOk() {
    clock.setWallTime(12123);
    long d = registry.getInitialDelay(10000);
    Assertions.assertEquals(2123, d);
  }

  @Test
  public void batchesExpiration() {
    for (int i = 0; i < 9; ++i) {
      registry.counter("" + i).increment();
    }
    Assertions.assertEquals(3, registry.getBatches().size());
    for (List<Measurement> batch : registry.getBatches()) {
      Assertions.assertEquals(3, batch.size());
    }

    clock.setWallTime(Duration.ofMinutes(15).toMillis() + 1);
    Assertions.assertEquals(0, registry.getBatches().size());
  }

  @Test
  public void keepsNonExpired() {
    for (int i = 0; i < 9; ++i) {
      registry.counter("" + i).increment();
    }
    registry.collectData();
    Assertions.assertEquals(3, registry.getBatches().size());
  }

  @Test
  public void removesExpired() {
    for (int i = 0; i < 9; ++i) {
      registry.counter("" + i).increment();
    }
    clock.setWallTime(Duration.ofMinutes(15).toMillis() + 1);
    registry.collectData();
    Assertions.assertEquals(0, registry.getBatches().size());
  }
}
