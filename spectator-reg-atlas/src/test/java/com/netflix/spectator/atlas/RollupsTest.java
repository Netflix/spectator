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

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.ManualClock;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Statistic;
import com.netflix.spectator.api.Tag;
import com.netflix.spectator.api.Utils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class RollupsTest {

  private ManualClock clock;
  private AtlasRegistry registry;

  @BeforeEach
  public void before() {
    clock = new ManualClock();
    AtlasConfig config = new AtlasConfig() {
      @Override public String get(String k) {
        return "atlas.step".equals(k) ? "PT5S" : null;
      }

      @Override public Registry debugRegistry() {
        return new NoopRegistry();
      }
    };
    registry = new AtlasRegistry(clock, config);
  }

  private Id removeIdxTag(Id id) {
    List<Tag> filtered = new ArrayList<>();
    for (Tag t : id.tags()) {
      if (!"i".equals(t.key())) {
        filtered.add(t);
      }
    }
    return registry.createId(id.name()).withTags(filtered);
  }

  @Test
  public void aggregateCounters() {
    for (int i = 0; i < 10; ++i) {
      registry.counter("test", "i", "" + i).increment();
    }
    clock.setWallTime(5000);
    List<Measurement> input = registry.measurements().collect(Collectors.toList());
    List<Measurement> aggr = Rollups.aggregate(this::removeIdxTag, input);
    Assertions.assertEquals(1, aggr.size());

    Measurement m = aggr.get(0);
    Id id = registry.createId("test")
        .withTag("atlas.dstype", "rate")
        .withTag(Statistic.count);
    Assertions.assertEquals(id, m.id());
    Assertions.assertEquals(10.0 / 5.0, m.value(), 1e-12);
  }

  @Test
  public void aggregateGauges() {
    for (int i = 0; i < 10; ++i) {
      registry.gauge("test", "i", "" + i).set(2.0);
    }
    clock.setWallTime(5000);
    List<Measurement> input = registry.measurements().collect(Collectors.toList());
    List<Measurement> aggr = Rollups.aggregate(this::removeIdxTag, input);
    Assertions.assertEquals(1, aggr.size());

    Measurement m = aggr.get(0);
    Id id = registry.createId("test")
        .withTag("atlas.dstype", "gauge")
        .withTag(Statistic.gauge);
    Assertions.assertEquals(id, m.id());
    Assertions.assertEquals(2.0, m.value(), 1e-12);
  }

  @Test
  public void aggregateGaugesWithNaN() {
    for (int i = 0; i < 10; ++i) {
      double v = (i % 2 == 0) ? i : Double.NaN;
      registry.gauge("test", "i", "" + i).set(v);
    }
    clock.setWallTime(5000);
    List<Measurement> input = registry.measurements().collect(Collectors.toList());
    List<Measurement> aggr = Rollups.aggregate(this::removeIdxTag, input);
    Assertions.assertEquals(1, aggr.size());

    Measurement m = aggr.get(0);
    Id id = registry.createId("test")
        .withTag("atlas.dstype", "gauge")
        .withTag(Statistic.gauge);
    Assertions.assertEquals(id, m.id());
    Assertions.assertEquals(8.0, m.value(), 1e-12);
  }

  @Test
  public void aggregateTimers() {
    for (int i = 0; i < 10; ++i) {
      registry.timer("test", "i", "" + i).record(i, TimeUnit.SECONDS);
    }
    clock.setWallTime(5000);
    List<Measurement> input = registry.measurements().collect(Collectors.toList());
    List<Measurement> aggr = Rollups.aggregate(this::removeIdxTag, input);
    Assertions.assertEquals(4, aggr.size());

    for (Measurement m : aggr) {
      Id id = registry.createId("test");
      switch (Utils.getTagValue(m.id(), "statistic")) {
        case "count":
          id = id.withTag("atlas.dstype", "rate").withTag(Statistic.count);
          Assertions.assertEquals(id, m.id());
          Assertions.assertEquals(10.0 / 5.0, m.value(), 1e-12);
          break;
        case "totalTime":
          id = id.withTag("atlas.dstype", "rate").withTag(Statistic.totalTime);
          Assertions.assertEquals(id, m.id());
          Assertions.assertEquals(45.0 / 5.0, m.value(), 1e-12);
          break;
        case "totalOfSquares":
          id = id.withTag("atlas.dstype", "rate").withTag(Statistic.totalOfSquares);
          Assertions.assertEquals(id, m.id());
          Assertions.assertEquals(285.0 / 5.0, m.value(), 1e-12);
          break;
        case "max":
          id = id.withTag("atlas.dstype", "gauge").withTag(Statistic.max);
          Assertions.assertEquals(id, m.id());
          Assertions.assertEquals(9.0, m.value(), 1e-12);
          break;
        default:
          Assertions.fail("unexpected id: " + m.id());
          break;
      }
    }
  }

  @Test
  public void aggregateDistributionSummaries() {
    for (int i = 0; i < 10; ++i) {
      registry.distributionSummary("test", "i", "" + i).record(i);
    }
    clock.setWallTime(5000);
    List<Measurement> input = registry.measurements().collect(Collectors.toList());
    List<Measurement> aggr = Rollups.aggregate(this::removeIdxTag, input);
    Assertions.assertEquals(4, aggr.size());

    for (Measurement m : aggr) {
      Id id = registry.createId("test");
      switch (Utils.getTagValue(m.id(), "statistic")) {
        case "count":
          id = id.withTag("atlas.dstype", "rate").withTag(Statistic.count);
          Assertions.assertEquals(id, m.id());
          Assertions.assertEquals(10.0 / 5.0, m.value(), 1e-12);
          break;
        case "totalAmount":
          id = id.withTag("atlas.dstype", "rate").withTag(Statistic.totalAmount);
          Assertions.assertEquals(id, m.id());
          Assertions.assertEquals(45.0 / 5.0, m.value(), 1e-12);
          break;
        case "totalOfSquares":
          id = id.withTag("atlas.dstype", "rate").withTag(Statistic.totalOfSquares);
          Assertions.assertEquals(id, m.id());
          Assertions.assertEquals(285.0 / 5.0, m.value(), 1e-12);
          break;
        case "max":
          id = id.withTag("atlas.dstype", "gauge").withTag(Statistic.max);
          Assertions.assertEquals(id, m.id());
          Assertions.assertEquals(9.0, m.value(), 1e-12);
          break;
        default:
          Assertions.fail("unexpected id: " + m.id());
          break;
      }
    }
  }
}
