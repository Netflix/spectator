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

import com.netflix.spectator.api.Registry;

import java.util.Map;
import java.util.function.Function;

/**
 * Simple in-memory cache based on ConcurrentHashMap with minimal dependencies.
 *
 * <p><b>This class is an internal implementation detail only intended for use within spectator.
 * It is subject to change without notice.</b></p>
 */
public interface Cache<K, V> {

  /**
   * Create a new cache instance that removes the least frequently used items when full.
   *
   * @param registry
   *     Registry to use for tracking stats about the cache.
   * @param id
   *     Used with metrics to indentify a particular instance of the cache.
   * @param baseSize
   *     Number of items that should always be kept around in the cache.
   * @param compactionSize
   *     Maximum size of the underlying map for the cache.
   * @return
   *     Instance of an LFU cache.
   */
  static <K1, V1> Cache<K1, V1> lfu(Registry registry, String id, int baseSize, int compactionSize) {
    return new LfuCache<>(registry, id, baseSize, compactionSize);
  }

  /**
   * Returns the cached value associated with the key or null if none is found.
   */
  V get(K key);

  /**
   * Like {@link #get(Object)}, but does not update the access count.
   */
  V peek(K key);

  /**
   * Add or overwrite the cache entry for the key.
   */
  void put(K key, V value);

  /**
   * Returns the cached value associated with the key if present, otherwise computes a value
   * using the provided function.
   *
   * @param key
   *     Id to use for looking up a value in the cache.
   * @param f
   *     Function to compute a value based on the key. This function may get called multiple
   *     times on a cache miss with some results getting discarded.
   * @return
   *     The value that was already cached or was just computed.
   */
  V computeIfAbsent(K key, Function<K, V> f);

  /**
   * Remove all entries from the cache.
   */
  void clear();

  int size();

  /**
   * Returns a map containing a snapshot of the cache.
   */
  Map<K, V> asMap();
}
