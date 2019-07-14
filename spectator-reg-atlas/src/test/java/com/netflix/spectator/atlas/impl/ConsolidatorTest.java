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
package com.netflix.spectator.atlas.impl;

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.ManualClock;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Statistic;
import com.netflix.spectator.atlas.AtlasRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.DoubleConsumer;
import java.util.function.Supplier;

public class ConsolidatorTest {

  private static final long TTL = 15L * 60L * 1000L;
  private static final long PRIMARY_STEP = 5L * 1000L;
  private static final long CONSOLIDATED_STEP = 60L * 1000L;
  private static final int MULTIPLE = (int) (CONSOLIDATED_STEP / PRIMARY_STEP);

  private AtlasRegistry registry(Clock clock, long step) {
    Map<String, String> config = new HashMap<>();
    config.put("atlas.meterTTL", Duration.ofMillis(TTL).toString());
    config.put("atlas.step", Duration.ofMillis(step).toString());
    config.put("atlas.lwc.step", Duration.ofMillis(step).toString());
    return new AtlasRegistry(clock, config::get);
  }

  @Test
  public void avgNormalOperation() {
    Consolidator consolidator = new Consolidator.Avg(CONSOLIDATED_STEP, MULTIPLE);
    for (int i = 0; i < 10; ++i) {
      long baseTimestamp = i * CONSOLIDATED_STEP;
      for (int j = 0; j < MULTIPLE; ++j) {
        consolidator.update(baseTimestamp + j * PRIMARY_STEP, j);
      }
      Assertions.assertEquals(i == 0 ? 0.0 : 5.5, consolidator.value(baseTimestamp), 1e-8);
    }
  }

  @Test
  public void avgMissingPrimaryValues() {
    Consolidator consolidator = new Consolidator.Avg(CONSOLIDATED_STEP, MULTIPLE);
    consolidator.update(30000, 12.0);
    consolidator.update(60000, Double.NaN);
    Assertions.assertEquals(1.0, consolidator.value(60000));
  }

  @Test
  public void avgSingleStepGap() {
    Consolidator consolidator = new Consolidator.Avg(CONSOLIDATED_STEP, MULTIPLE);
    consolidator.update(30000, 12.0);
    consolidator.update(110000, 12.0);
    Assertions.assertEquals(1.0, consolidator.value(60000));
    consolidator.update(120000, Double.NaN);
    Assertions.assertEquals(1.0, consolidator.value(120000));
  }

  @Test
  public void avgManyStepGap() {
    Consolidator consolidator = new Consolidator.Avg(CONSOLIDATED_STEP, MULTIPLE);
    consolidator.update(30000, 12.0);
    consolidator.update(360000, 12.0);
    Assertions.assertTrue(Double.isNaN(consolidator.value(60000)));
    Assertions.assertTrue(Double.isNaN(consolidator.value(360000)));
  }

  @Test
  public void avgBackInTime() {
    Consolidator consolidator = new Consolidator.Avg(CONSOLIDATED_STEP, MULTIPLE);
    consolidator.update(360000, 12.0);
    consolidator.update(350000, 12.0);
    Assertions.assertEquals(1.0, consolidator.value(360000), 1e-8);
  }

  @Test
  public void avgEmpty() {
    Consolidator consolidator = new Consolidator.Avg(CONSOLIDATED_STEP, MULTIPLE);
    Assertions.assertTrue(consolidator.isEmpty());
    consolidator.update(30000, 12.0);
    Assertions.assertFalse(consolidator.isEmpty());
    consolidator.update(150000, Double.NaN);
    Assertions.assertTrue(consolidator.isEmpty());
  }

  @Test
  public void noneEmpty() {
    Consolidator consolidator = new Consolidator.None();
    Assertions.assertTrue(consolidator.isEmpty());
    consolidator.update(30000, 12.0);
    Assertions.assertFalse(consolidator.isEmpty());
    consolidator.update(150000, Double.NaN);
    Assertions.assertTrue(consolidator.isEmpty());
  }

  private void consolidateRandomData(
      Id measurementId,
      ManualClock clock,
      Consolidator consolidator,
      DoubleConsumer primary,
      DoubleConsumer consolidated,
      Supplier<Iterable<Measurement>> primaryMeasure,
      Supplier<Iterable<Measurement>> consolidatedMeasure) {

    Random r = new Random(42);

    for (int i = 0; i < 3600; ++i) {
      long t = i * 1000L;
      clock.setWallTime(t);
      int v = r.nextInt(10_000);
      primary.accept(v);
      consolidated.accept(v);
      if (t % PRIMARY_STEP == 0L) {
        for (Measurement m : primaryMeasure.get()) {
          consolidator.update(m);
        }
      }
      if (t % CONSOLIDATED_STEP == 0L) {
        Measurement actual = new Measurement(measurementId, t, consolidator.value(t));
        for (Measurement m : consolidatedMeasure.get()) {
          Assertions.assertEquals(m.id(), actual.id());
          Assertions.assertEquals(m.timestamp(), actual.timestamp());
          Assertions.assertEquals(m.value(), actual.value(), 1e-8);
        }
      }

      // Simulate a gap
      if (i == 968) {
        i += 360;
      }
    }
  }

  @Test
  public void avgRandom() {
    Id id = Id.create("test");
    Id measurementId = id.withTag("atlas.dstype", "rate").withTag(Statistic.count);
    ManualClock clock = new ManualClock();

    Counter primary = registry(clock, PRIMARY_STEP).counter(id);
    Counter consolidated = registry(clock, CONSOLIDATED_STEP).counter(id);

    Consolidator consolidator = new Consolidator.Avg(CONSOLIDATED_STEP, MULTIPLE);

    consolidateRandomData(
        measurementId,
        clock,
        consolidator,
        primary::add,
        consolidated::add,
        primary::measure,
        consolidated::measure);
  }

  @Test
  public void maxRandom() {
    Id id = Id.create("test");
    Id measurementId = id.withTag("atlas.dstype", "gauge").withTag(Statistic.max);
    ManualClock clock = new ManualClock();

    Gauge primary = registry(clock, PRIMARY_STEP).maxGauge(id);
    Gauge consolidated = registry(clock, CONSOLIDATED_STEP).maxGauge(id);

    Consolidator consolidator = new Consolidator.Max(CONSOLIDATED_STEP, MULTIPLE);

    consolidateRandomData(
        measurementId,
        clock,
        consolidator,
        primary::set,
        consolidated::set,
        primary::measure,
        consolidated::measure);
  }

  @Test
  public void lastRandom() {
    Id id = Id.create("test");
    Id measurementId = id.withTag("atlas.dstype", "gauge").withTag(Statistic.gauge);
    ManualClock clock = new ManualClock();

    Gauge primary = registry(clock, PRIMARY_STEP).gauge(id);
    Gauge consolidated = registry(clock, CONSOLIDATED_STEP).gauge(id);

    Consolidator consolidator = new Consolidator.Last(CONSOLIDATED_STEP, MULTIPLE);

    consolidateRandomData(
        measurementId,
        clock,
        consolidator,
        primary::set,
        consolidated::set,
        primary::measure,
        consolidated::measure);
  }

  @Test
  public void noneRandom() {
    Id id = Id.create("test");
    Id measurementId = id.withTag("atlas.dstype", "rate").withTag(Statistic.count);
    ManualClock clock = new ManualClock();

    Counter primary = registry(clock, CONSOLIDATED_STEP).counter(id);
    Counter consolidated = registry(clock, CONSOLIDATED_STEP).counter(id);

    Consolidator consolidator = new Consolidator.None();

    consolidateRandomData(
        measurementId,
        clock,
        consolidator,
        primary::add,
        consolidated::add,
        primary::measure,
        consolidated::measure);
  }

  @Test
  public void createFromStatistic() {
    EnumSet<Statistic> counters = EnumSet.of(
        Statistic.count,
        Statistic.totalAmount,
        Statistic.totalTime,
        Statistic.totalOfSquares,
        Statistic.percentile);
    EnumSet<Statistic> maxGauges = EnumSet.of(
        Statistic.max,
        Statistic.duration,
        Statistic.activeTasks);
    EnumSet<Statistic> gauges = EnumSet.of(Statistic.gauge);
    for (Statistic statistic : Statistic.values()) {
      Consolidator consolidator = Consolidator.create(statistic, CONSOLIDATED_STEP, MULTIPLE);
      if (counters.contains(statistic)) {
        Assertions.assertTrue(consolidator instanceof Consolidator.Avg, statistic.name());
      } else if (maxGauges.contains(statistic)) {
        Assertions.assertTrue(consolidator instanceof Consolidator.Max, statistic.name());
      } else if (gauges.contains(statistic)) {
        Assertions.assertTrue(consolidator instanceof Consolidator.Last, statistic.name());
      } else {
        Assertions.fail(statistic.name());
      }
    }
  }

  @Test
  public void createFromIdCounter() {
    Id id = Id.create("foo").withTag(Statistic.count);
    Consolidator consolidator = Consolidator.create(id, CONSOLIDATED_STEP, MULTIPLE);
    Assertions.assertTrue(consolidator instanceof Consolidator.Avg);
  }

  @Test
  public void createFromIdGauge() {
    Id id = Id.create("foo").withTag(Statistic.gauge);
    Consolidator consolidator = Consolidator.create(id, CONSOLIDATED_STEP, MULTIPLE);
    Assertions.assertTrue(consolidator instanceof Consolidator.Last);
  }

  @Test
  public void createFromIdNoStatistic() {
    Id id = Id.create("foo");
    Consolidator consolidator = Consolidator.create(id, CONSOLIDATED_STEP, MULTIPLE);
    Assertions.assertTrue(consolidator instanceof Consolidator.Last);
  }

  @Test
  public void createFromIdMultipleOne() {
    Id id = Id.create("foo");
    Consolidator consolidator = Consolidator.create(id, CONSOLIDATED_STEP, 1);
    Assertions.assertTrue(consolidator instanceof Consolidator.None);
  }
}
