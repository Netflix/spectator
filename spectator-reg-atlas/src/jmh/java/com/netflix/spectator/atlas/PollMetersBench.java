/*
 * Copyright 2014-2020 Netflix, Inc.
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
import com.netflix.spectator.api.histogram.PercentileTimer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
public class PollMetersBench {

  private ManualClock clock;
  private AtlasRegistry registry;

  @Setup
  public void setup() {
    clock = new ManualClock();
    registry = new AtlasRegistry(clock, System::getProperty);
    Random r = new Random(42);
    for (int i = 0; i < 100_000; ++i) {
      switch (r.nextInt(8)) {
        case 0:
          registry.timer(randomId(r)).record(42, TimeUnit.MILLISECONDS);
          break;
        case 1:
          registry.distributionSummary(randomId(r)).record(42);
          break;
        case 2:
          registry.gauge(randomId(r)).set(42.0);
          break;
        case 3:
          registry.maxGauge(randomId(r)).set(42.0);
          break;
        case 4:
          PercentileTimer.builder(registry)
              .withId(randomId(r))
              .build()
              .record(r.nextInt(60_000), TimeUnit.MILLISECONDS);
          break;
        default:
          registry.counter(randomId(r)).increment();
          break;
      }
    }
  }

  private Id randomId(Random r) {
    Id tmp = Id.create(randomString(r, 2 + r.nextInt(120)));
    int n = r.nextInt(20);
    for (int i = 0; i < n; ++i) {
      String k = randomString(r, 2 + r.nextInt(60));
      String v = randomString(r, 2 + r.nextInt(120));
      tmp = tmp.withTag(k, v);
    }
    return tmp;
  }

  private String randomString(Random r, int len) {
    StringBuilder builder = new StringBuilder(len);
    for (int i = 0; i < len; ++i) {
      builder.append(randomChar(r));
    }
    return builder.toString();
  }

  private char randomChar(Random r) {
    final int range = '~' - '!';
    return (char) ('!' + r.nextInt(range));
  }

  @Benchmark
  public void pollMeters(Blackhole bh) {
    long t = clock.wallTime() + 1;
    clock.setWallTime(t);
    registry.pollMeters(t);
    bh.consume(registry.getBatches(t));
  }
}
