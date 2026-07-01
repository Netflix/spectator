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
package com.netflix.spectator.impl;

import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Registry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

public class DirectMappedCacheTest {

  private Registry registry;

  @BeforeEach
  public void before() {
    registry = new DefaultRegistry();
  }

  private long hits() {
    return registry
        .counter("spectator.cache.requests", "id", "test", "result", "hit")
        .count();
  }

  private long misses() {
    return registry
        .counter("spectator.cache.requests", "id", "test", "result", "miss")
        .count();
  }

  private long resizes() {
    return registry
        .counter("spectator.cache.resizes", "id", "test")
        .count();
  }

  private Cache<String, String> create(int initialSize, int maxSize) {
    return Cache.directMapped(registry, "test", initialSize, maxSize);
  }

  @Test
  public void emptyBeforeFirstPut() {
    // Lazy: no allocation, no entries, and a lookup is a miss.
    Cache<String, String> cache = create(64, 1024);
    Assertions.assertEquals(0, cache.size());
    Assertions.assertNull(cache.get("a"));
    Assertions.assertEquals(0, hits());
    Assertions.assertEquals(1, misses());
  }

  @Test
  public void putGet() {
    Cache<String, String> cache = create(64, 1024);
    cache.put("a", "A");
    Assertions.assertEquals("A", cache.get("a"));
    Assertions.assertEquals(1, hits());
    Assertions.assertEquals(0, misses());
  }

  @Test
  public void peekDoesNotCount() {
    Cache<String, String> cache = create(64, 1024);
    cache.put("a", "A");
    Assertions.assertEquals("A", cache.peek("a"));
    Assertions.assertNull(cache.peek("b"));
    Assertions.assertEquals(0, hits());
    Assertions.assertEquals(0, misses());
  }

  @Test
  public void computeIfAbsent() {
    Cache<String, String> cache = create(64, 1024);
    Assertions.assertEquals("A", cache.computeIfAbsent("a", String::toUpperCase));
    Assertions.assertEquals(0, hits());
    Assertions.assertEquals(1, misses());
  }

  @Test
  public void computeIfAbsentRepeat() {
    Cache<String, String> cache = create(64, 1024);
    Assertions.assertEquals("A", cache.computeIfAbsent("a", String::toUpperCase));
    // Same key, same slot untouched by any other write -> deterministic hit, function not called.
    Assertions.assertEquals("A", cache.computeIfAbsent("a", s -> {
      throw new RuntimeException("fail");
    }));
    Assertions.assertEquals(1, hits());
    Assertions.assertEquals(1, misses());
  }

  @Test
  public void clear() {
    Cache<String, String> cache = create(64, 1024);
    cache.put("a", "A");
    Assertions.assertEquals(1, cache.size());
    cache.clear();
    Assertions.assertEquals(0, cache.size());
    Assertions.assertNull(cache.get("a"));
  }

  @Test
  public void growsUpToMax() {
    Cache<String, String> cache = create(2, 64);
    for (int i = 0; i < 10_000; ++i) {
      String v = UUID.randomUUID().toString();
      cache.put(v, v);
    }
    // Grew from the initial size and stays bounded by the max regardless of churn.
    Assertions.assertTrue(resizes() > 0, "expected at least one resize");
    Assertions.assertTrue(cache.size() <= 64, "size must stay within max capacity");
    // Still functional: a fresh entry is readable immediately after it is written.
    cache.put("fresh", "FRESH");
    Assertions.assertEquals("FRESH", cache.get("fresh"));
  }

  @Test
  public void nonGrowingWhenInitialEqualsMax() {
    Cache<String, String> cache = create(16, 16);
    for (int i = 0; i < 10_000; ++i) {
      String v = UUID.randomUUID().toString();
      cache.put(v, v);
    }
    Assertions.assertEquals(0, resizes());
    Assertions.assertTrue(cache.size() <= 16, "size must stay within fixed capacity");
  }

  @Test
  public void highHitRateWhenWorkingSetFits() {
    // A fixed table far larger than the working set keeps the load factor low, so conflict misses
    // are rare and nearly every distinct key stays resident. (A growing table would instead settle
    // near a 0.5 load factor, where direct-mapping's conflict misses drop ~20% of a uniform scan.)
    int n = 100;
    Cache<String, String> cache = create(8192, 8192);
    String[] keys = new String[n];
    for (int i = 0; i < n; ++i) {
      keys[i] = UUID.randomUUID().toString();
      cache.put(keys[i], keys[i]);
    }
    int found = 0;
    for (String k : keys) {
      if (k.equals(cache.get(k))) {
        ++found;
      }
    }
    Assertions.assertTrue(found >= 95, "expected nearly all keys resident, found " + found);
  }

  @Test
  public void largeMaxSizeDoesNotHang() {
    // Regression: tableSize() must cap the power-of-two search so a large cap does not overflow into
    // an infinite loop in the constructor. Would hang before the cap was added.
    Assertions.assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
      Cache<String, String> cache = Cache.directMapped(registry, "test", 1, Integer.MAX_VALUE);
      cache.put("a", "A");
      Assertions.assertEquals("A", cache.get("a"));
    });
  }

  @Test
  public void resizeKeepsTableConsistent() {
    // Insert enough distinct keys to force several doublings, then assert the rehash left the table
    // internally consistent: every entry asMap() reports must be retrievable via get() at its
    // masked slot. A wrong-mask or dropping rehash would leave entries asMap sees but get cannot
    // find. (A growing direct-mapped table does lose some entries to collisions on each rehash,
    // self-healing under repeated access, so this checks consistency rather than a survival count.)
    Cache<String, String> cache = create(2, 4096);
    for (int i = 0; i < 200; ++i) {
      String v = UUID.randomUUID().toString();
      cache.put(v, v);
    }
    Assertions.assertTrue(resizes() > 0, "expected resizes growing from 2 toward 4096");
    Map<String, String> snapshot = cache.asMap();
    Assertions.assertFalse(snapshot.isEmpty(), "expected resident entries after growth");
    for (Map.Entry<String, String> e : snapshot.entrySet()) {
      Assertions.assertEquals(e.getValue(), cache.get(e.getKey()),
          "asMap reported an entry that get() cannot find at its slot: " + e.getKey());
    }
  }

  @Test
  public void concurrentPutAndClearDoNotThrow() throws Exception {
    // Regression: put()/computeIfAbsent() must read the volatile table once so a concurrent clear()
    // cannot null it between a check and a use. Before the fix this raced to a NullPointerException.
    Cache<String, String> cache = create(64, 1024);
    int workers = 8;
    int iterations = 20_000;
    ExecutorService pool = Executors.newFixedThreadPool(workers + 1);
    AtomicReference<Throwable> error = new AtomicReference<>();
    CountDownLatch start = new CountDownLatch(1);
    List<Future<?>> futures = new ArrayList<>();
    for (int w = 0; w < workers; ++w) {
      final int id = w;
      futures.add(pool.submit(() -> {
        try {
          start.await();
          for (int i = 0; i < iterations; ++i) {
            String k = "k" + id + "-" + (i & 1023);
            cache.put(k, k);
            cache.get(k);
            cache.computeIfAbsent(k, x -> x);
          }
        } catch (Throwable e) {
          error.compareAndSet(null, e);
        }
      }));
    }
    futures.add(pool.submit(() -> {
      try {
        start.await();
        for (int i = 0; i < iterations; ++i) {
          cache.clear();
        }
      } catch (Throwable e) {
        error.compareAndSet(null, e);
      }
    }));
    start.countDown();
    for (Future<?> f : futures) {
      f.get();
    }
    pool.shutdown();
    Assertions.assertNull(error.get(), () -> "unexpected exception: " + error.get());
  }
}
