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

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Registry;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * LFU implementation of {@link Cache}.
 */
class LfuCache<K, V> implements Cache<K, V> {

  private final Counter hits;
  private final Counter misses;
  private final Counter compactions;

  private final ConcurrentHashMap<K, Pair<V>> data;
  private final int baseSize;
  private final int compactionSize;

  private final AtomicInteger size;

  private final Lock lock;

  LfuCache(Registry registry, String id, int baseSize, int compactionSize) {
    this.hits = registry.counter("spectator.cache.requests", "id", id, "result", "hit");
    this.misses = registry.counter("spectator.cache.requests", "id", id, "result", "miss");
    this.compactions = registry.counter("spectator.cache.compactions", "id", id);
    data = new ConcurrentHashMap<>();
    this.baseSize = baseSize;
    this.compactionSize = compactionSize;
    this.size = new AtomicInteger();
    this.lock = new ReentrantLock();
  }

  private void compact() {
    Map<K, Snapshot<V>> snapshot = new HashMap<>();
    data.forEach((k, v) -> snapshot.put(k, v.snapshot()));

    snapshot.entrySet()
        .stream()
        .sorted(Comparator.comparingLong(e -> e.getValue().negativeCount()))
        .skip(baseSize)
        .forEach(e -> data.remove(e.getKey()));

    size.set(data.size());
    compactions.increment();
  }

  private void tryCompact() {
    if (lock.tryLock()) {
      try {
        compact();
      } finally {
        lock.unlock();
      }
    }
  }

  @Override
  public V get(K key) {
    Pair<V> value = data.get(key);
    if (value == null) {
      misses.increment();
      return null;
    } else {
      hits.increment();
      return value.get();
    }
  }

  @Override public V peek(K key) {
    Pair<V> value = data.get(key);
    return value == null ? null : value.peek();
  }

  @Override public void put(K key, V value) {
    Pair<V> prev = data.put(key, new Pair<>(value));
    if (prev == null && size.incrementAndGet() > compactionSize) {
      tryCompact();
    }
  }

  @Override public V computeIfAbsent(K key, Function<K, V> f) {
    Pair<V> value = data.get(key);
    if (value == null) {
      misses.increment();
      Pair<V> tmp = new Pair<>(f.apply(key));
      value = data.putIfAbsent(key, tmp);
      if (value == null) {
        value = tmp;
        if (size.incrementAndGet() > compactionSize) {
          tryCompact();
        }
      }
    } else {
      hits.increment();
    }
    return value.get();
  }

  @Override public void clear() {
    size.set(0);
    data.clear();
  }

  @Override public int size() {
    return size.get();
  }

  @Override public Map<K, V> asMap() {
    return data.entrySet()
        .stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().peek()));
  }

  private static class Pair<V> {
    private V value;
    private LongAdder count;

    Pair(V value) {
      this.value = value;
      this.count = new LongAdder();
    }

    V get() {
      count.increment();
      return value;
    }

    V peek() {
      return value;
    }

    Snapshot<V> snapshot() {
      return new Snapshot<>(value, count.sum());
    }
  }

  private static class Snapshot<V> {
    private V value;
    private long count;

    Snapshot(V value, long count) {
      this.value = value;
      this.count = count;
    }

    V get() {
      return value;
    }

    long negativeCount() {
      return -count;
    }
  }
}
