/**
 * Copyright 2015 Netflix, Inc.
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
package com.netflix.spectator.tdigest;

import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Timer;
import com.typesafe.config.ConfigFactory;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@State(Scope.Benchmark)
public class Record {

  private final long[] values = new long[1000000];

  private final Registry registry = new DefaultRegistry();
  private final TDigestConfig config = new TDigestConfig(ConfigFactory.parseString("polling-frequency = 60s"));
  private final Registry digestRegistry = new TDigestRegistry(registry, config);

  private final Timer defaultTimer = registry.timer("jmh");
  private final Timer digestTimer = digestRegistry.timer("jmh");

  private AtomicInteger pos = new AtomicInteger();

  @Setup(Level.Iteration)
  public void setup() {
    Random random = new Random();
    for (int i = 0; i < values.length; ++i) {
      values[i] = random.nextLong();
    }
  }

  @Threads(1)
  @Benchmark
  public void defaultRecord_T1(Blackhole bh) {
    int i = pos.getAndIncrement() % values.length;
    defaultTimer.record(values[i], TimeUnit.SECONDS);
    bh.consume(defaultTimer.totalTime());
  }

  @Threads(2)
  @Benchmark
  public void defaultRecord_T2(Blackhole bh) {
    int i = pos.getAndIncrement() % values.length;
    defaultTimer.record(values[i], TimeUnit.SECONDS);
    bh.consume(defaultTimer.totalTime());
  }

  @Threads(4)
  @Benchmark
  public void defaultRecord_T4(Blackhole bh) {
    int i = pos.getAndIncrement() % values.length;
    defaultTimer.record(values[i], TimeUnit.SECONDS);
    bh.consume(defaultTimer.totalTime());
  }

  @Threads(8)
  @Benchmark
  public void defaultRecord_T8(Blackhole bh) {
    int i = pos.getAndIncrement() % values.length;
    defaultTimer.record(values[i], TimeUnit.SECONDS);
    bh.consume(defaultTimer.totalTime());
  }

  @Threads(1)
  @Benchmark
  public void digestRecord_T1(Blackhole bh) {
    int i = pos.getAndIncrement() % values.length;
    digestTimer.record(values[i], TimeUnit.SECONDS);
    bh.consume(digestTimer.totalTime());
  }

  @Threads(2)
  @Benchmark
  public void digestRecord_T2(Blackhole bh) {
    int i = pos.getAndIncrement() % values.length;
    digestTimer.record(values[i], TimeUnit.SECONDS);
    bh.consume(digestTimer.totalTime());
  }

  @Threads(4)
  @Benchmark
  public void digestRecord_T4(Blackhole bh) {
    int i = pos.getAndIncrement() % values.length;
    digestTimer.record(values[i], TimeUnit.SECONDS);
    bh.consume(digestTimer.totalTime());
  }

  @Threads(8)
  @Benchmark
  public void digestRecord_T8(Blackhole bh) {
    int i = pos.getAndIncrement() % values.length;
    digestTimer.record(values[i], TimeUnit.SECONDS);
    bh.consume(digestTimer.totalTime());
  }

  @TearDown
  public void tearDown() {
    long recorded = registry.counter("spectator.tdigest.samples", "id", "recorded").count();
    long dropped = registry.counter("spectator.tdigest.samples", "id", "dropped").count();
    double percent = 100.0 * dropped / (dropped + recorded);
    System.out.printf("recorded: %d, dropped: %d, percent-dropped: %.2f%%%n", recorded, dropped, percent);
  }

  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
        .include(".*")
        .forks(1)
        .build();
    new Runner(opt).run();
  }
}
