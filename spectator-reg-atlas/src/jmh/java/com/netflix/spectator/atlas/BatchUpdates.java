/*
 * Copyright 2014-2022 Netflix, Inc.
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

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DistributionSummary;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Timer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * <pre>
 * Benchmark                          Mode  Cnt      Score      Error   Units
 *
 * noInstrumentation                 thrpt    5   3978.248 ±  136.863   ops/s
 *
 * counter                           thrpt    5     14.138 ±    0.229   ops/s
 * counterBatch                      thrpt    5    464.445 ±    8.175   ops/s
 *
 * distSummary                       thrpt    5      9.383 ±    0.732   ops/s
 * distSummaryBatch                  thrpt    5    353.769 ±   10.698   ops/s
 *
 * timer                             thrpt    5     10.505 ±    0.170   ops/s
 * timerBatch                        thrpt    5    336.505 ±    3.538   ops/s
 * </pre>
 */
@State(Scope.Thread)
public class BatchUpdates {

  private Registry registry;

  @Setup
  public void setup() {
    registry = new AtlasRegistry(Clock.SYSTEM, System::getProperty);
  }

  @TearDown
  public void tearDown() {
    registry = null;
  }

  @Benchmark
  public void noInstrumentation(Blackhole bh) {
    long sum = 0L;
    for (int i = 0; i < 1_000_000; ++i) {
      sum += i;
    }
    bh.consume(sum);
  }

  @Benchmark
  public void counter(Blackhole bh) {
    Counter c = registry.counter("test");
    long sum = 0L;
    for (int i = 0; i < 1_000_000; ++i) {
      sum += i;
      c.increment();
    }
    bh.consume(sum);
  }

  @Benchmark
  public void counterBatch(Blackhole bh) throws Exception {
    Counter c = registry.counter("test");
    try (Counter.BatchUpdater b = c.batchUpdater(100_000)) {
      long sum = 0L;
      for (int i = 0; i < 1_000_000; ++i) {
        sum += i;
        b.increment();
      }
      bh.consume(sum);
    }
  }

  @Benchmark
  public void timer(Blackhole bh) {
    Timer t = registry.timer("test");
    long sum = 0L;
    for (int i = 0; i < 1_000_000; ++i) {
      sum += i;
      t.record(i, TimeUnit.MILLISECONDS);
    }
    bh.consume(sum);
  }

  @Benchmark
  public void timerBatch(Blackhole bh) throws Exception {
    Timer t = registry.timer("test");
    try (Timer.BatchUpdater b = t.batchUpdater(100_000)) {
      long sum = 0L;
      for (int i = 0; i < 1_000_000; ++i) {
        sum += i;
        b.record(i, TimeUnit.MILLISECONDS);
      }
      bh.consume(sum);
    }
  }

  @Benchmark
  public void distSummary(Blackhole bh) {
    DistributionSummary d = registry.distributionSummary("test");
    long sum = 0L;
    for (int i = 0; i < 1_000_000; ++i) {
      sum += i;
      d.record(i);
    }
    bh.consume(sum);
  }

  @Benchmark
  public void distSummaryBatch(Blackhole bh) throws Exception {
    DistributionSummary d = registry.distributionSummary("test");
    try (DistributionSummary.BatchUpdater b = d.batchUpdater(100_000)) {
      long sum = 0L;
      for (int i = 0; i < 1_000_000; ++i) {
        sum += i;
        b.record(i);
      }
      bh.consume(sum);
    }
  }
}
