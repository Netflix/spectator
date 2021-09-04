/*
 * Copyright 2014-2021 Netflix, Inc.
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
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

  // Collections that are reused for each compaction operation
  private final PriorityQueue<Snapshot<K>> mostFrequentItems;
  private final List<K> mostFrequentKeys;

  private final AtomicInteger size;

  private final Lock lock;

  LfuCache(Registry registry, String id, int baseSize, int compactionSize) {
    this.hits = registry.counter("spectator.cache.requests", "id", id, "result", "hit");
    this.misses = registry.counter("spectator.cache.requests", "id", id, "result", "miss");
    this.compactions = registry.counter("spectator.cache.compactions", "id", id);
    data = new ConcurrentHashMap<>();
    this.baseSize = baseSize;
    this.compactionSize = compactionSize;
    this.mostFrequentItems = new PriorityQueue<>(baseSize, SNAPSHOT_COMPARATOR);
    this.mostFrequentKeys = new ArrayList<>(baseSize);
    this.size = new AtomicInteger();
    this.lock = new ReentrantLock();
  }

  private void addIfMoreFrequent(K key, Pair<V> value) {
    long count = value.snapshot();
    if (mostFrequentItems.size() >= baseSize) {
      // Queue is full, add new item if it is more frequently used than the least
      // frequent item currently in the queue.
      Snapshot<K> leastFrequentItem = mostFrequentItems.peek();
      if (leastFrequentItem != null && count > leastFrequentItem.count()) {
        mostFrequentItems.poll();
        mostFrequentItems.offer(new Snapshot<>(key, count));
      }
    } else {
      mostFrequentItems.offer(new Snapshot<>(key, count));
    }
  }

  private void compact() {
    int numToRemove = size.get() - baseSize;
    if (numToRemove > 0) {
      mostFrequentItems.clear();
      mostFrequentKeys.clear();

      data.forEach(this::addIfMoreFrequent);
      mostFrequentItems.forEach(s -> mostFrequentKeys.add(s.get()));
      data.keySet().retainAll(mostFrequentKeys);

      size.set(data.size());
      compactions.increment();
    }
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
    private final V value;
    private final LongAdder count;

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

    long snapshot() {
      return count.sum();
    }
  }

  private static class Snapshot<K> {
    private final K key;
    private final long count;

    Snapshot(K key, long count) {
      this.key = key;
      this.count = count;
    }

    K get() {
      return key;
    }

    long count() {
      return count;
    }
  }

  // Comparator for finding the least frequent items with the priority queue
  private static final Comparator<Snapshot<?>> SNAPSHOT_COMPARATOR =
      (a, b) -> Long.compare(b.count(), a.count());
}
