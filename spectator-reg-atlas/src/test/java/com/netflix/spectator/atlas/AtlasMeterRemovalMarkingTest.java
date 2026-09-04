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
package com.netflix.spectator.atlas;

import com.netflix.spectator.api.ManualClock;
import com.netflix.spectator.api.Meter;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.impl.RemovableMeter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * The Atlas meters are told when the registry removes them, so a reference held elsewhere can
 * find out without reading the wall clock. Nothing reads the mark yet.
 */
public class AtlasMeterRemovalMarkingTest {

  private static final long TTL = 15 * 60_000L;

  private ManualClock clock;
  private AtlasRegistry registry;

  /**
   * Publishing off and a separate debug registry, so the registry under test holds only the
   * meters the test creates rather than the publisher's own bookkeeping counters.
   */
  private AtlasConfig newConfig() {
    Map<String, String> props = new LinkedHashMap<>();
    props.put("atlas.enabled", "false");
    props.put("atlas.meterTTL", Duration.ofMillis(TTL).toString());

    return new AtlasConfig() {
      @Override public String get(String k) {
        return props.get(k);
      }

      @Override public Registry debugRegistry() {
        return new NoopRegistry();
      }
    };
  }

  @BeforeEach
  public void init() {
    clock = new ManualClock();
    registry = new AtlasRegistry(clock, newConfig());
  }

  /** One of every meter type the registry can hand out. */
  private void populate() {
    registry.counter("counter").increment();
    registry.timer("timer").record(1, TimeUnit.NANOSECONDS);
    registry.distributionSummary("summary").record(1);
    registry.gauge("gauge").set(1.0);
    registry.maxGauge("maxGauge").set(1.0);
  }

  private Meter[] snapshot() {
    Meter[] ms = registry.stream().toArray(Meter[]::new);
    Assertions.assertEquals(5, ms.length, "expected exactly the five meters created by populate");
    return ms;
  }

  @Test
  public void everyMeterTypeIsRemovable() {
    populate();
    Set<String> seen = new HashSet<>();
    for (Meter m : snapshot()) {
      Assertions.assertTrue(m instanceof RemovableMeter, m.getClass() + " is not removable");
      Assertions.assertFalse(((RemovableMeter) m).isRemoved(), m.id() + " is marked already");
      seen.add(m.getClass().getSimpleName());
    }
    // Named explicitly, so a type that stopped being an AtlasMeter is noticed rather than
    // quietly leaving the loop with nothing to check.
    Set<String> expected = new HashSet<>();
    expected.add("AtlasCounter");
    expected.add("AtlasTimer");
    expected.add("AtlasDistributionSummary");
    expected.add("AtlasGauge");
    expected.add("AtlasMaxGauge");
    Assertions.assertEquals(expected, seen);
  }

  @Test
  public void cleanupPassMarksExpiredMeters() {
    populate();
    Meter[] before = snapshot();

    clock.setWallTime(TTL + 1);
    registry.removeExpiredMeters();

    for (Meter m : before) {
      Assertions.assertTrue(((RemovableMeter) m).isRemoved(),
          m.id() + " was removed without being marked");
      Assertions.assertNull(registry.get(m.id()), m.id() + " is still registered");
    }
  }

  @Test
  public void markSurvivesAClockRewind() {
    populate();
    Meter[] before = snapshot();

    clock.setWallTime(TTL + 1);
    registry.removeExpiredMeters();

    // Wind the clock back so the TTL check no longer fires. The mark has to be independent of
    // it, which is the whole point of the flag over hasExpired().
    clock.setWallTime(0L);
    for (Meter m : before) {
      Assertions.assertFalse(m.hasExpired(), "precondition: the TTL check no longer fires");
      Assertions.assertTrue(((RemovableMeter) m).isRemoved(),
          m.id() + " lost its mark when the clock moved");
    }
  }

  @Test
  public void meterSurvivingTheCleanupPassIsNotMarked() {
    populate();
    clock.setWallTime(TTL + 1);
    // Keep one meter active so the pass leaves it alone.
    registry.counter("counter").increment();

    registry.removeExpiredMeters();

    Meter kept = registry.get(registry.createId("counter"));
    Assertions.assertNotNull(kept);
    Assertions.assertFalse(((RemovableMeter) kept).isRemoved());
  }

  @Test
  public void closeMarks() {
    populate();
    Meter[] before = snapshot();

    // AtlasRegistry overrides close(), so this covers the one removal path it does not inherit.
    registry.close();

    for (Meter m : before) {
      Assertions.assertTrue(((RemovableMeter) m).isRemoved(),
          m.id() + " was dropped by close() without being marked");
    }
  }
}
