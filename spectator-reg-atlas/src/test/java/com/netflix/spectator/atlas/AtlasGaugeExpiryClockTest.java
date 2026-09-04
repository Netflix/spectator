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

import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.ManualClock;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Meter;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A gauge reports step aligned timestamps, but the alignment belongs to the timestamp it is
 * polled with, not to a clock of its own. Tracking activity on a floored clock made the sweep,
 * which uses the raw clock, believe the gauge was up to a step older than it was; reading a
 * floored clock for the reported timestamp made the gauge answer for whatever interval the wall
 * clock was in rather than the one the registry asked about.
 */
public class AtlasGaugeExpiryClockTest {

  private static final long TTL = 900_000L;
  private static final long LWC_STEP = 5_000L;

  private ManualClock clock;
  private AtlasRegistry registry;

  private AtlasConfig newConfig() {
    Map<String, String> props = new LinkedHashMap<>();
    props.put("atlas.enabled", "false");
    props.put("atlas.meterTTL", Duration.ofMillis(TTL).toString());
    props.put("atlas.lwc.step", Duration.ofMillis(LWC_STEP).toString());

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

  @Test
  public void gaugeIsNotSweptBeforeItsTtl() {
    // Set part way through a step, so a floored last-update time would be earlier than the real
    // one by almost a whole step.
    clock.setWallTime(LWC_STEP - 1);
    Id id = registry.createId("test");
    registry.gauge(id).set(1.0);

    // Still inside the TTL measured from when the gauge was actually set (TTL + 1 - (STEP - 1)
    // is less than TTL). Flooring the update time to the step boundary would put it just outside.
    clock.setWallTime(TTL + 1);

    registry.removeExpiredMeters();

    Assertions.assertNotNull(registry.get(id), "gauge was swept before its TTL elapsed");
  }

  @Test
  public void gaugeIsStillSweptOnceItsTtlElapses() {
    clock.setWallTime(LWC_STEP - 1);
    Id id = registry.createId("test");
    registry.gauge(id).set(1.0);

    clock.setWallTime(LWC_STEP - 1 + TTL + 1);
    registry.removeExpiredMeters();

    Assertions.assertNull(registry.get(id), "gauge outlived its TTL");
  }

  @Test
  public void sweepAgreesWithTheGaugesOwnExpiryCheck() {
    clock.setWallTime(LWC_STEP - 1);
    Id id = registry.createId("test");
    registry.gauge(id).set(1.0);
    Meter gauge = registry.get(id);

    // Whatever the clock says, the registry must not remove a meter that does not consider
    // itself expired: a held reference resolves through hasExpired(), so the two disagreeing
    // leaves the reference stranded on a meter the registry no longer reports.
    boolean swept = false;
    for (long t = LWC_STEP; t <= LWC_STEP + TTL + LWC_STEP; t += 250L) {
      clock.setWallTime(t);
      boolean expiredBefore = gauge.hasExpired();
      registry.removeExpiredMeters();
      if (registry.get(id) == null) {
        Assertions.assertTrue(expiredBefore,
            "swept at " + t + " while the gauge reported itself as not expired");
        swept = true;
        break;
      }
    }
    // Without this the loop finishing without ever removing the gauge would leave the test
    // passing on no assertions at all.
    Assertions.assertTrue(swept, "gauge was never swept, so the two were never compared");
  }

  @Test
  public void measurementTimestampsStayStepAligned() {
    clock.setWallTime(LWC_STEP - 1);
    Id id = registry.createId("test");
    registry.gauge(id).set(42.0);

    clock.setWallTime(LWC_STEP + 1234L);
    Meter gauge = registry.get(id);
    int count = 0;
    for (Measurement m : gauge.measure()) {
      // The exact value, not just "a multiple of the step": at this clock time the coarser
      // publishing step and a hard coded zero are both multiples of the lwc step, so a modulo
      // check cannot tell the right alignment from either of them.
      Assertions.assertEquals(LWC_STEP, m.timestamp(),
          "timestamp " + m.timestamp() + " is not aligned to the lwc step");
      Assertions.assertEquals(42.0, m.value(), 1e-12);
      ++count;
    }
    Assertions.assertEquals(1, count);
  }

  @Test
  public void gaugeTimestampFollowsThePollTimestamp() {
    clock.setWallTime(LWC_STEP - 1);
    Id id = registry.createId("test");
    registry.gauge(id).set(7.0);

    // The shutdown flush moves the registry clock to the next boundary and polls the meters for
    // it, so the gauge has to report the interval it was polled for. Reading a clock of its own
    // instead would put the value in an interval the consolidator has already passed and the
    // final value would never be published.
    long t = LWC_STEP * 3;
    AtlasMeter gauge = (AtlasMeter) registry.get(id);
    List<Measurement> ms = new ArrayList<>();
    gauge.measure(t, (mid, timestamp, value) -> ms.add(new Measurement(mid, timestamp, value)));

    Assertions.assertEquals(1, ms.size());
    Assertions.assertEquals(t, ms.get(0).timestamp());
    Assertions.assertEquals(7.0, ms.get(0).value(), 1e-12);
  }

  @Test
  public void maxGaugeIsUnaffected() {
    clock.setWallTime(LWC_STEP - 1);
    Id id = registry.createId("test.max");
    Gauge g = registry.maxGauge(id);
    g.set(1.0);

    clock.setWallTime(TTL + 1);
    registry.removeExpiredMeters();

    Assertions.assertNotNull(registry.get(id), "max gauge was swept before its TTL elapsed");
  }
}
