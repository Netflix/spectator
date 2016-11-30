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
package com.netflix.spectator.perf;

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Registry;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

@State(Scope.Thread)
public class Counters {

  @State(Scope.Benchmark)
  public static class Data {
    Registry registry = new DefaultRegistry();
    Counter cached = registry.counter("cached");
    String[] names;
    String[] newNames;

    public Data() {
      newNames = new String[100000];
      for (int i = 0; i < 100000; ++i) {
        registry.counter(UUID.randomUUID().toString()).increment();
        newNames[i] = UUID.randomUUID().toString();
      }
      names = registry.counters()
          .map(c -> c.id().name())
          .collect(Collectors.toList())
          .toArray(new String[]{});
    }
  }

  @State(Scope.Thread)
  public static class Metrics {
    private Random random = new Random();

    Counter get(Data data) {
      // Assumes about 5% of lookups will be for a new or expired counter. This is
      // mostly just to have some activity that will cause an addition to the map
      // mixed in with the reads.
      String name = (random.nextDouble() < 0.05)
          ? data.newNames[random.nextInt(data.newNames.length)]
          : data.names[random.nextInt(data.names.length)];
      return data.registry.counter(name);
    }
  }

  @Threads(1)
  @Benchmark
  public void cached_T001(Data data) {
    data.cached.increment();
  }

  @Threads(1)
  @Benchmark
  public void lookup_T001(Data data) {
    data.registry.counter("lookup").increment();
  }

  @Threads(1)
  @Benchmark
  public void random_T001(Data data, Metrics metrics) {
    metrics.get(data).increment();
  }

  @Threads(2)
  @Benchmark
  public void cached_T002(Data data) {
    data.cached.increment();
  }

  @Threads(2)
  @Benchmark
  public void lookup_T002(Data data) {
    data.registry.counter("lookup").increment();
  }

  @Threads(2)
  @Benchmark
  public void random_T002(Data data, Metrics metrics) {
    metrics.get(data).increment();
  }

  @Threads(4)
  @Benchmark
  public void cached_T004(Data data) {
    data.cached.increment();
  }

  @Threads(4)
  @Benchmark
  public void lookup_T004(Data data) {
    data.registry.counter("lookup").increment();
  }

  @Threads(4)
  @Benchmark
  public void random_T004(Data data, Metrics metrics) {
    metrics.get(data).increment();
  }

  @Threads(8)
  @Benchmark
  public void cached_T008(Data data) {
    data.cached.increment();
  }

  @Threads(8)
  @Benchmark
  public void lookup_T008(Data data) {
    data.registry.counter("lookup").increment();
  }

  @Threads(8)
  @Benchmark
  public void random_T008(Data data, Metrics metrics) {
    metrics.get(data).increment();
  }

  @Threads(16)
  @Benchmark
  public void cached_T016(Data data) {
    data.cached.increment();
  }

  @Threads(16)
  @Benchmark
  public void lookup_T016(Data data) {
    data.registry.counter("lookup").increment();
  }

  @Threads(16)
  @Benchmark
  public void random_T016(Data data, Metrics metrics) {
    metrics.get(data).increment();
  }

  @Threads(32)
  @Benchmark
  public void cached_T032(Data data) {
    data.cached.increment();
  }

  @Threads(32)
  @Benchmark
  public void lookup_T032(Data data) {
    data.registry.counter("lookup").increment();
  }

  @Threads(32)
  @Benchmark
  public void random_T032(Data data, Metrics metrics) {
    metrics.get(data).increment();
  }

  @Threads(64)
  @Benchmark
  public void cached_T064(Data data) {
    data.cached.increment();
  }

  @Threads(64)
  @Benchmark
  public void lookup_T064(Data data) {
    data.registry.counter("lookup").increment();
  }

  @Threads(64)
  @Benchmark
  public void random_T064(Data data, Metrics metrics) {
    metrics.get(data).increment();
  }

  @Threads(128)
  @Benchmark
  public void cached_T128(Data data) {
    data.cached.increment();
  }

  @Threads(128)
  @Benchmark
  public void lookup_T128(Data data) {
    data.registry.counter("lookup").increment();
  }

  @Threads(128)
  @Benchmark
  public void random_T128(Data data, Metrics metrics) {
    metrics.get(data).increment();
  }

  @Threads(256)
  @Benchmark
  public void cached_T256(Data data) {
    data.cached.increment();
  }

  @Threads(256)
  @Benchmark
  public void lookup_T256(Data data) {
    data.registry.counter("lookup").increment();
  }

  @Threads(256)
  @Benchmark
  public void random_T256(Data data, Metrics metrics) {
    metrics.get(data).increment();
  }

  @TearDown
  public void check(Data data) {
    final long cv = data.cached.count();
    final long lv = data.registry.counter("lookup").count();
    assert cv > 0 || lv > 0 : "counters haven't been incremented";
  }

  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
        .include(".*")
        .forks(1)
        .build();
    new Runner(opt).run();
  }
}
