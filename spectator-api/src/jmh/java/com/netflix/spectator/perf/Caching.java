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
package com.netflix.spectator.perf;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.impl.Cache;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Compare performance of simple caching implementation to Caffeine. In some cases we have
 * see Caffeine/Guava implementations cause quite a bit of thread contention for concurrent
 * access of a LoadingCache.
 *
 * Also the enabling the registry causes updating the hits/misses counters for each access.
 * This can potentially be quite a bit of overhead compared to just the lookup, so enabling
 * the monitoring may significantly reduce performance.
 */
public class Caching {

  @State(Scope.Benchmark)
  public static class AllMissesState {

    private final Registry registry = new NoopRegistry();

    final Cache<String, String> spectator = Cache.lfu(registry, "jmh", 200, 1000);

    final LoadingCache<String, String> caffeine = Caffeine.newBuilder()
        .recordStats()
        .maximumSize(1000)
        .build(String::toUpperCase);

    private final String[] missData = createTestData(10000);
    private AtomicInteger pos = new AtomicInteger();

    private String[] createTestData(int n) {
      String[] values = new String[n];
      for (int i = 0; i < values.length; ++i) {
        values[i] = UUID.randomUUID().toString();
      }
      return values;
    }

    public String next() {
      int i = Integer.remainderUnsigned(pos.getAndIncrement(), missData.length);
      return missData[i];
    }
  }

  @State(Scope.Benchmark)
  public static class TypicalState {

    private final Registry registry = new NoopRegistry();

    final Cache<String, String> spectator = Cache.lfu(registry, "jmh", 200, 1000);

    final LoadingCache<String, String> caffeine = Caffeine.newBuilder()
        .recordStats()
        .maximumSize(1000)
        .build(String::toUpperCase);

    private final String[] data = createTestData();
    private AtomicInteger pos = new AtomicInteger();

    private String[] createTestData() {
      int n = 10000;
      String[] values = new String[n];

      int i = 0;
      int amount = 140;
      while (i < n && amount > 0) {
        String v = UUID.randomUUID().toString();
        for (int j = 0; j < amount; ++j) {
          values[i] = v;
          ++i;
        }
        --amount;
      }

      for (; i < values.length; ++i) {
        values[i] = UUID.randomUUID().toString();
      }

      List<String> list = Arrays.asList(values);
      Collections.shuffle(list);

      return list.toArray(new String[0]);
    }

    public String next() {
      int i = Integer.remainderUnsigned(pos.getAndIncrement(), data.length);
      return data[i];
    }
  }

  @Threads(8)
  @Benchmark
  public void allMissesSpectator(Blackhole bh, AllMissesState state) {
    String s = state.next();
    bh.consume(state.spectator.computeIfAbsent(s, String::toUpperCase));
  }

  @Threads(8)
  @Benchmark
  public void allMissesCaffeine(Blackhole bh, AllMissesState state) {
    String s = state.next();
    bh.consume(state.caffeine.get(s));
  }

  @Threads(8)
  @Benchmark
  public void typicalSpectator(Blackhole bh, TypicalState state) {
    String s = state.next();
    bh.consume(state.spectator.computeIfAbsent(s, String::toUpperCase));
  }

  @Threads(8)
  @Benchmark
  public void typicalCaffeine(Blackhole bh, TypicalState state) {
    String s = state.next();
    bh.consume(state.caffeine.get(s));
  }
}
