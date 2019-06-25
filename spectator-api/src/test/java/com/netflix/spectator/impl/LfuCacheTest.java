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
package com.netflix.spectator.impl;

import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Registry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LfuCacheTest {

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

  private long compactions() {
    return registry
        .counter("spectator.cache.compactions", "id", "test")
        .count();
  }

  private Cache<String, String> create(int baseSize, int compactionSize) {
    return Cache.lfu(registry, "test", baseSize, compactionSize);
  }

  private String[] createTestData() {
    int n = 10000;
    String[] values = new String[n];

    int i = 0;
    int amount = 140; // should have ~98% hit rate with base size > 140
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

  @Test
  public void computeIfAbsent() {
    Cache<String, String> cache = create(1, 2);
    Assertions.assertEquals("A", cache.computeIfAbsent("a", String::toUpperCase));
    Assertions.assertEquals(0, hits());
    Assertions.assertEquals(1, misses());
  }

  @Test
  public void computeIfAbsentRepeat() {
    Cache<String, String> cache = create(1, 2);
    Assertions.assertEquals("A", cache.computeIfAbsent("a", String::toUpperCase));
    Assertions.assertEquals("A", cache.computeIfAbsent("a", s -> {
      // Shouldn't get called since key is cached
      throw new RuntimeException("fail");
    }));
    Assertions.assertEquals(1, hits());
    Assertions.assertEquals(1, misses());
  }

  @Test
  public void computeIfAbsentCompaction() {
    Cache<String, String> cache = create(1, 2);
    Assertions.assertEquals("A", cache.computeIfAbsent("a", String::toUpperCase));
    Assertions.assertEquals("B", cache.computeIfAbsent("b", String::toUpperCase));
    Assertions.assertEquals("B", cache.computeIfAbsent("b", String::toUpperCase));
    Assertions.assertEquals(2, cache.size());

    Assertions.assertEquals("C", cache.computeIfAbsent("c", String::toUpperCase));
    Assertions.assertEquals(1, hits());
    Assertions.assertEquals(3, misses());
    Assertions.assertEquals(1, compactions());
    Assertions.assertEquals(1, cache.size());

    Map<String, String> expected = new HashMap<>();
    expected.put("b", "B");
    Assertions.assertEquals(expected, cache.asMap());
  }

  @Test
  public void computeIfAbsentHitRate() {
    Cache<String, String> cache = create(100, 1000);
    for (String s : createTestData()) {
      cache.computeIfAbsent(s, String::toUpperCase);
    }
    Assertions.assertEquals(9730, hits());
    Assertions.assertEquals(270, misses());
    Assertions.assertEquals(0, compactions());
  }
}
