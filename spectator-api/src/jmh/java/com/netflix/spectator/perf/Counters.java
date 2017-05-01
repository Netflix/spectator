/*
 * Copyright 2014-2017 Netflix, Inc.
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
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Summary of results on m4.16xlarge:
 *
 * <pre>
 *  ### T1 Composite(Empty)
 *
 *  ```
 *  Benchmark                Mode  Cnt          Score         Error  Units
 *  Counters.cached         thrpt   10  222995027.222 ± 5812215.685  ops/s
 *  Counters.lookup         thrpt   10   34596370.526 ± 7975715.214  ops/s
 *  Counters.random         thrpt   10    5699426.669 ±  604639.108  ops/s
 *  ```
 *
 *  ### T1 Composite(Noop)
 *
 *  ```
 *  Benchmark                Mode  Cnt          Score         Error  Units
 *  Counters.cached         thrpt   10  221034201.857 ± 9204618.077  ops/s
 *  Counters.lookup         thrpt   10   33410400.013 ± 7828970.416  ops/s
 *  Counters.random         thrpt   10    5977032.777 ±  679753.009  ops/s
 *  ```
 *
 *  ### T1 Composite(Default)
 *
 *  ```
 *  Benchmark                Mode  Cnt         Score         Error  Units
 *  Counters.cached         thrpt   10  61043422.331 ± 3085269.565  ops/s
 *  Counters.lookup         thrpt   10  25989379.563 ± 4981909.126  ops/s
 *  Counters.random         thrpt   10   4299422.647 ±  394069.294  ops/s
 *  ```
 *
 *  ### T1 Composite(Noop, Noop)
 *
 *  ```
 *  Benchmark                Mode  Cnt         Score         Error  Units
 *  Counters.cached         thrpt   10  65781502.616 ± 3124211.952  ops/s
 *  Counters.lookup         thrpt   10  23914193.535 ± 6256980.210  ops/s
 *  Counters.random         thrpt   10   3907696.564 ±  383335.366  ops/s
 *  ```
 *
 *  ### T1 Composite(Default, Default)
 *
 *  ```
 *  Benchmark                Mode  Cnt         Score         Error  Units
 *  Counters.cached         thrpt   10  37594426.749 ± 1302829.135  ops/s
 *  Counters.lookup         thrpt   10  17151030.656 ± 3776435.406  ops/s
 *  Counters.random         thrpt   10   2228890.157 ±  186029.279  ops/s
 *  ```
 * </pre>
 */
@State(Scope.Thread)
public class Counters {

  @State(Scope.Benchmark)
  public static class Data {
    Registry registry = new DefaultRegistry();
    Counter cached = registry.counter("cached");
    String[] names;
    String[] newNames;

    public Data() {
      names = new String[100000];
      newNames = new String[100000];
      for (int i = 0; i < 100000; ++i) {
        names[i] = UUID.randomUUID().toString();
        registry.counter(names[i]).increment();
        newNames[i] = UUID.randomUUID().toString();
      }
    }
  }

  @State(Scope.Thread)
  public static class Metrics {
    private Random random = ThreadLocalRandom.current();

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

  private long incrementAndGet(Counter c) {
    c.increment();
    return c.count();
  }

  @Benchmark
  public long cached(Data data) {
    return incrementAndGet(data.cached);
  }

  @Benchmark
  public long lookup(Data data) {
    return incrementAndGet(data.registry.counter("lookup"));
  }

  @Benchmark
  public long random(Data data, Metrics metrics) {
    return incrementAndGet(metrics.get(data));
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
