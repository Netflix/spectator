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
package com.netflix.spectator.perf;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.impl.Cache;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Concurrency benchmark for the LFU {@link Cache} under an access pattern modeled on a metric
 * stream processor (e.g. the LWC bridge): every thread processes a "payload" as a run of
 * {@code BATCH_SIZE} repeated lookups of one value (within-payload tag affinity) before rotating
 * to another value drawn from {@code keySpace} distinct values. The {@code get}-then-{@code put}
 * on a miss mirrors how the query index actually uses the cache.
 *
 * <p>This is the scenario where the per-read frequency-counter cost shows up: a very high volume
 * of hits concentrated on a few hot caches. The two cache types are configured with the same
 * bound ({@link #CACHE_SIZE}) so the comparison is apples-to-apples, and {@code keySpace} relative
 * to that bound controls churn:
 * <ul>
 *   <li>100   - fits the cache, no eviction: isolates the steady-state read path.</li>
 *   <li>20000 - exceeds it: entries are created and evicted continuously.</li>
 * </ul>
 *
 * <p>The {@code caffeine} method is included for contrast; its per-cache maintenance lock does not
 * scale to this concurrency under churn. Runs at {@link Threads#MAX} (all available processors),
 * since the cost being measured is a concurrency effect. To run only this benchmark set
 * {@code includes = ['.*LfuCacheBench.*']} in the {@code jmh} block of build.gradle.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Threads(Threads.MAX)
@State(Scope.Benchmark)
public class LfuCacheBench {

  private static final String VALUE = "v";
  private static final int BATCH_SIZE = 1000;
  private static final int CACHE_SIZE = 1000;

  @Param({"100", "20000"})
  public int keySpace;

  private Cache<String, String> lfuCache;
  private com.github.benmanes.caffeine.cache.Cache<String, String> caffeineCache;
  private String[] keys;

  @Setup(Level.Trial)
  public void setup() {
    lfuCache = Cache.lfu(new NoopRegistry(), "jmh", CACHE_SIZE / 10, CACHE_SIZE);
    caffeineCache = Caffeine.newBuilder().maximumSize(CACHE_SIZE).build();
    keys = new String[keySpace];
    for (int i = 0; i < keySpace; ++i) {
      keys[i] = "app" + i + "-main-v042";
    }
  }

  /** Per-thread cursor tracking the current payload's value and the lookups remaining in it. */
  @State(Scope.Thread)
  public static class Cursor {
    int remaining;
    String key;
  }

  private String nextKey(Cursor cursor) {
    if (cursor.remaining <= 0) {
      cursor.key = keys[ThreadLocalRandom.current().nextInt(keySpace)];
      cursor.remaining = BATCH_SIZE;
    }
    --cursor.remaining;
    return cursor.key;
  }

  @Benchmark
  public String lfu(Cursor cursor) {
    String key = nextKey(cursor);
    String v = lfuCache.get(key);
    if (v == null) {
      v = VALUE;
      lfuCache.put(key, v);
    }
    return v;
  }

  @Benchmark
  public String caffeine(Cursor cursor) {
    String key = nextKey(cursor);
    String v = caffeineCache.getIfPresent(key);
    if (v == null) {
      v = VALUE;
      caffeineCache.put(key, v);
    }
    return v;
  }
}
