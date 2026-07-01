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

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Registry;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * Direct-mapped implementation of {@link Cache}. An open-addressed table where a hash collision
 * evicts the current occupant rather than probing, so eviction is O(1) and amortized into each put
 * with no periodic compaction sweep. Each slot holds an immutable key/value entry published through
 * an {@link AtomicReferenceArray}, making a read a single volatile array load plus a key compare and
 * a write a single {@code getAndSet} — no locks and no per-read frequency bookkeeping on the hot
 * path.
 *
 * <p>The table is allocated lazily on the first write and grows on demand: it starts at
 * {@code initialSize} and doubles up to {@code maxSize} as the working set fills it (when occupancy
 * crosses 3/4 of the current length). This lets a cache that is never written cost nothing and one
 * with a small working set stay small, so memory tracks live entries rather than the configured
 * maximum. Use {@code initialSize == maxSize} for a fixed-capacity, non-growing cache.
 *
 * <p>Writes are best-effort: a collision evicts whatever occupied the slot, concurrent writes to the
 * same slot or a value racing a resize may be dropped, and a resize itself may drop entries that
 * collide when rehashed into the larger table (self-healing, since they are recomputed on the next
 * miss). A dropped entry only causes a later miss (and recompute), never an incorrect result, so
 * this is well suited to memoizing a pure function. It is a poor fit where a value must remain
 * resident once written.
 *
 * <p><b>This class is an internal implementation detail only intended for use within spectator.
 * It is subject to change without notice.</b></p>
 */
class DirectMappedCache<K, V> implements Cache<K, V> {

  private final Counter hits;
  private final Counter misses;
  private final Counter resizes;

  private final int initialLen;
  private final int maxLen;

  // null until the first put; holds the slot array directly so a read is a single load with no
  // wrapper indirection. Volatile so a resize (which swaps in a larger array) is safely published.
  private volatile AtomicReferenceArray<Entry<K, V>> table;

  // Filled-slot count for the current table, reset on resize. Approximate under races (only used as
  // a grow-trigger hint) and updated only while the table can still grow.
  private final AtomicInteger occupied;

  // Guards lazy allocation and resize, which are rare; the get/put steady path stays lock-free.
  private final Lock lock;

  DirectMappedCache(Registry registry, String id, int initialSize, int maxSize) {
    this.hits = registry.counter("spectator.cache.requests", "id", id, "result", "hit");
    this.misses = registry.counter("spectator.cache.requests", "id", id, "result", "miss");
    this.resizes = registry.counter("spectator.cache.resizes", "id", id);
    this.maxLen = tableSize(Math.max(1, maxSize));
    this.initialLen = Math.min(tableSize(Math.max(1, initialSize)), maxLen);
    this.occupied = new AtomicInteger();
    this.lock = new ReentrantLock();
    this.table = null;
  }

  private AtomicReferenceArray<Entry<K, V>> allocate() {
    lock.lock();
    try {
      if (table == null) {
        table = new AtomicReferenceArray<>(initialLen);
      }
      return table;
    } finally {
      lock.unlock();
    }
  }

  private void grow(AtomicReferenceArray<Entry<K, V>> old) {
    lock.lock();
    try {
      // Recheck under lock: another thread may have already grown past this generation.
      if (table == old && old.length() < maxLen) {
        int newLen = old.length() << 1;
        AtomicReferenceArray<Entry<K, V>> next = new AtomicReferenceArray<>(newLen);
        int mask = newLen - 1;
        int occ = 0;
        for (int i = 0; i < old.length(); ++i) {
          Entry<K, V> e = old.get(i);
          if (e != null) {
            int j = mix(e.key.hashCode()) & mask;
            if (next.get(j) == null) {
              ++occ;
            }
            next.set(j, e); // collisions within the 2x array are rare; evict as usual
          }
        }
        occupied.set(occ);
        resizes.increment();
        table = next;
      }
    } finally {
      lock.unlock();
    }
  }

  private void putEntry(K key, V value) {
    // Read the volatile field once: a concurrent clear() could null it between a check and a reuse,
    // and operating on a stale-but-non-null array only drops a best-effort write (never an NPE).
    AtomicReferenceArray<Entry<K, V>> t = table;
    if (t == null) {
      t = allocate();
    }
    int len = t.length();
    Entry<K, V> prev = t.getAndSet(mix(key.hashCode()) & (len - 1), new Entry<>(key, value));
    // Filling a free slot is the only event that raises occupancy; skip the check once at max size.
    if (prev == null && len < maxLen && occupied.incrementAndGet() >= len - (len >>> 2)) {
      grow(t);
    }
  }

  @Override public V get(K key) {
    AtomicReferenceArray<Entry<K, V>> t = table;
    if (t != null) {
      Entry<K, V> e = t.get(mix(key.hashCode()) & (t.length() - 1));
      if (e != null && e.key.equals(key)) {
        hits.increment();
        return e.value;
      }
    }
    misses.increment();
    return null;
  }

  @Override public V peek(K key) {
    AtomicReferenceArray<Entry<K, V>> t = table;
    if (t == null) {
      return null;
    }
    Entry<K, V> e = t.get(mix(key.hashCode()) & (t.length() - 1));
    return (e != null && e.key.equals(key)) ? e.value : null;
  }

  @Override public void put(K key, V value) {
    putEntry(key, value);
  }

  @Override public V computeIfAbsent(K key, Function<K, V> f) {
    AtomicReferenceArray<Entry<K, V>> t = table;
    if (t != null) {
      Entry<K, V> e = t.get(mix(key.hashCode()) & (t.length() - 1));
      if (e != null && e.key.equals(key)) {
        hits.increment();
        return e.value;
      }
    }
    misses.increment();
    V value = f.apply(key);
    putEntry(key, value);
    return value;
  }

  @Override public void clear() {
    lock.lock();
    try {
      table = null;
      occupied.set(0);
    } finally {
      lock.unlock();
    }
  }

  @Override public int size() {
    AtomicReferenceArray<Entry<K, V>> t = table;
    if (t == null) {
      return 0;
    }
    int n = 0;
    for (int i = 0; i < t.length(); ++i) {
      if (t.get(i) != null) {
        ++n;
      }
    }
    return n;
  }

  @Override public Map<K, V> asMap() {
    Map<K, V> m = new HashMap<>();
    AtomicReferenceArray<Entry<K, V>> t = table;
    if (t != null) {
      for (int i = 0; i < t.length(); ++i) {
        Entry<K, V> e = t.get(i);
        if (e != null) {
          m.put(e.key, e.value);
        }
      }
    }
    return m;
  }

  /** Smallest power of two &gt;= max(1, n), capped at 2^30 so the doubling cannot overflow int. */
  private static int tableSize(int n) {
    if (n >= (1 << 30)) {
      return 1 << 30;
    }
    int c = 1;
    while (c < n) {
      c <<= 1;
    }
    return c;
  }

  /**
   * murmur3 fmix32 avalanche. {@code hashCode} values commonly cluster in the low bits that a raw
   * mask keys on, which would manufacture conflict misses; mixing spreads them across the range.
   */
  private static int mix(int h0) {
    int h = h0;
    h ^= (h >>> 16);
    h *= 0x85ebca6b;
    h ^= (h >>> 13);
    h *= 0xc2b2ae35;
    h ^= (h >>> 16);
    return h;
  }

  private static final class Entry<K, V> {
    private final K key;
    private final V value;

    Entry(K key, V value) {
      this.key = key;
      this.value = value;
    }
  }
}
