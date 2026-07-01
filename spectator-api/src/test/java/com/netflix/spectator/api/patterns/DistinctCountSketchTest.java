/*
 * Copyright 2014-2026 Netflix, Inc.
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
package com.netflix.spectator.api.patterns;

import com.netflix.spectator.api.BasicTag;
import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.ExpiringRegistry;
import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.ManualClock;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Statistic;
import com.netflix.spectator.api.Utils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

public class DistinctCountSketchTest {

  private static final int REGISTERS = DistinctCountSketch.REGISTERS;

  private Registry newRegistry() {
    return new DefaultRegistry(Clock.SYSTEM, k -> null);
  }

  // Read the current value (max rho) for each register, mapping the unset NaN to 0.
  private double[] readRegisters(Registry r, Id baseId) {
    double[] regs = new double[REGISTERS];
    for (int i = 0; i < REGISTERS; ++i) {
      Id rid = baseId.withTags(Statistic.distinct, new BasicTag("distinct", String.format("R%02X", i)));
      Gauge g = r.maxGauge(rid);
      double v = g.value();
      regs[i] = Double.isNaN(v) ? 0.0 : v;
    }
    return regs;
  }

  @Test
  public void id() {
    Registry r = newRegistry();
    Id id = r.createId("test");
    DistinctCountSketch sketch = DistinctCountSketch.get(r, id);
    Assertions.assertEquals(id, sketch.id());
  }

  @Test
  public void expiration() {
    ManualClock clock = new ManualClock();
    ExpiringRegistry r = new ExpiringRegistry(clock);
    DistinctCountSketch sketch = DistinctCountSketch.get(r, r.createId("test"));

    // A sketch with no registers yet has nothing to keep it alive, so it is reclaimable.
    Assertions.assertTrue(sketch.hasExpired());

    sketch.record(42L);
    Assertions.assertFalse(sketch.hasExpired());

    // Once the backing register gauge expires, the sketch expires and is dropped from the
    // registry state by the cleanup sweep.
    clock.setWallTime(1);
    Assertions.assertTrue(sketch.hasExpired());
    r.removeExpiredMeters();
    Assertions.assertNull(r.state().get(sketch.id()));

    // Recording again revives the self-healing register gauge and the sketch.
    sketch.record(42L);
    Assertions.assertFalse(sketch.hasExpired());
  }

  @Test
  public void getCachesInstance() {
    Registry r = newRegistry();
    Id id = r.createId("test");
    DistinctCountSketch s1 = DistinctCountSketch.get(r, id);
    DistinctCountSketch s2 = DistinctCountSketch.get(r, id);
    Assertions.assertSame(s1, s2);
  }

  @Test
  public void builderAppliesTags() {
    Registry r = newRegistry();
    DistinctCountSketch sketch = DistinctCountSketch.builder(r)
        .withName("test")
        .withTag("region", "us-east-1")
        .build();
    Id expected = r.createId("test").withTag("region", "us-east-1");
    Assertions.assertEquals(expected, sketch.id());
  }

  @Test
  public void recordUsesDistinctStatisticAndRegisterTag() {
    Registry r = newRegistry();
    Id id = r.createId("test");
    DistinctCountSketch sketch = DistinctCountSketch.get(r, id);
    sketch.record(42L);

    // Exactly one register should be populated by a single observation, and it must carry
    // both the distinct statistic and a R## register tag.
    int populated = 0;
    for (Gauge g : r.gauges().toArray(Gauge[]::new)) {
      if ("test".equals(g.id().name())) {
        Assertions.assertEquals("distinct", Utils.getTagValue(g.id(), "statistic"));
        String register = Utils.getTagValue(g.id(), "distinct");
        Assertions.assertNotNull(register);
        Assertions.assertTrue(register.matches("R[0-9A-F]{2}"), register);
        Assertions.assertTrue(g.value() >= 1.0 && g.value() <= 59.0, "rho=" + g.value());
        ++populated;
      }
    }
    Assertions.assertEquals(1, populated);
  }

  @Test
  public void recordingSameValueIsIdempotent() {
    Registry r = newRegistry();
    Id id = r.createId("test");
    DistinctCountSketch sketch = DistinctCountSketch.get(r, id);
    sketch.record(42L);
    double[] first = readRegisters(r, id);
    for (int i = 0; i < 100; ++i) {
      sketch.record(42L);
    }
    double[] second = readRegisters(r, id);
    Assertions.assertArrayEquals(first, second);
  }

  @Test
  public void registersAreUniformlyOccupied() {
    // Recording many distinct values should populate every register, which catches an
    // off-by-one in the index bit-split that would leave some registers unreachable.
    Registry r = newRegistry();
    Id id = r.createId("test");
    DistinctCountSketch sketch = DistinctCountSketch.get(r, id);
    for (long i = 0; i < 100_000; ++i) {
      sketch.record(i);
    }
    double[] regs = readRegisters(r, id);
    for (int i = 0; i < REGISTERS; ++i) {
      Assertions.assertTrue(regs[i] >= 1.0, "register " + i + " was not populated");
    }
  }

  @Test
  public void accuracyLong() {
    for (int n : new int[] {100, 1_000, 10_000, 100_000}) {
      Registry r = newRegistry();
      Id id = r.createId("test");
      DistinctCountSketch sketch = DistinctCountSketch.get(r, id);
      for (long i = 0; i < n; ++i) {
        sketch.record(i);
      }
      double est = sketch.cardinality();
      double error = Math.abs(est - n) / n;
      // 13% standard error; allow a generous multiple to stay non-flaky while still
      // catching gross encoding errors. Inputs are deterministic so the result is stable.
      Assertions.assertTrue(error < 0.30, "n=" + n + " est=" + est + " error=" + error);
    }
  }

  @Test
  public void accuracySmall() {
    // Small cardinalities exercise the linear-counting branch of the estimator. Inputs are
    // deterministic so the realized error is stable across runs.
    for (int n : new int[] {1, 2, 5, 10, 25, 50}) {
      Registry r = newRegistry();
      Id id = r.createId("test");
      DistinctCountSketch sketch = DistinctCountSketch.get(r, id);
      for (long i = 0; i < n; ++i) {
        sketch.record(i);
      }
      double est = sketch.cardinality();
      // Guard against a no-op record(): the absolute tolerance below would otherwise let an
      // all-zero (estimate 0) sketch pass for n=1 and n=2.
      Assertions.assertTrue(est > 0, "n=" + n + " est=" + est);
      // Absolute tolerance dominates for tiny n (a single register collision is a large
      // relative error when n is 1 or 2), relative tolerance dominates as n grows.
      double tolerance = Math.max(2.0, 0.30 * n);
      Assertions.assertEquals(n, est, tolerance, "n=" + n + " est=" + est);
    }
  }

  @Test
  public void accuracyString() {
    Registry r = newRegistry();
    Id id = r.createId("test");
    DistinctCountSketch sketch = DistinctCountSketch.get(r, id);
    int n = 50_000;
    for (int i = 0; i < n; ++i) {
      sketch.record("user-" + i);
    }
    double est = sketch.cardinality();
    double error = Math.abs(est - n) / n;
    Assertions.assertTrue(error < 0.30, "est=" + est + " error=" + error);
  }

  @Test
  public void accuracyBytes() {
    Registry r = newRegistry();
    Id id = r.createId("test");
    DistinctCountSketch sketch = DistinctCountSketch.get(r, id);
    int n = 50_000;
    for (int i = 0; i < n; ++i) {
      sketch.record(("ip-" + i).getBytes(StandardCharsets.UTF_8));
    }
    double est = sketch.cardinality();
    double error = Math.abs(est - n) / n;
    Assertions.assertTrue(error < 0.30, "est=" + est + " error=" + error);
  }

  @Test
  public void cardinalityOfEmptySketchIsZero() {
    // All registers unset: linear counting yields m * ln(m/m) = 0.
    Assertions.assertEquals(0.0, DistinctCountSketch.cardinality(new double[REGISTERS]), 1e-12);
  }

  @Test
  public void cardinalityStaticMatchesInstance() {
    Registry r = newRegistry();
    Id id = r.createId("test");
    DistinctCountSketch sketch = DistinctCountSketch.get(r, id);
    for (long i = 0; i < 10_000; ++i) {
      sketch.record(i);
    }
    Assertions.assertEquals(
        DistinctCountSketch.cardinality(readRegisters(r, id)),
        sketch.cardinality(),
        1e-12);
  }

  @Test
  public void supplementaryCodePointAboveBmp() {
    // A string containing a character above the BMP should hash without error and populate
    // a register, exercising the surrogate-pair path in the hash.
    Registry r = newRegistry();
    Id id = r.createId("test");
    DistinctCountSketch sketch = DistinctCountSketch.get(r, id);
    sketch.record("😀"); // U+1F600
    double[] regs = readRegisters(r, id);
    int populated = 0;
    for (double rho : regs) {
      if (rho >= 1.0) {
        ++populated;
      }
    }
    Assertions.assertEquals(1, populated);
  }
}
