/*
 * Copyright 2014-2022 Netflix, Inc.
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
package com.netflix.spectator.atlas.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Consumer;

/**
 * Simple tree for finding all values associated with a prefix that matches the search
 * key. The prefix is a simple ascii string. If unsupported characters are used in the
 * prefix or search key, then the prefix match will only check up to the unsupported
 * character and the caller will need to perform further checks on the returned value.
 */
final class PrefixTree<T> {

  private static final int FIRST_CHAR = ' ';
  private static final int LAST_CHAR = '~';
  private static final int TABLE_SIZE = LAST_CHAR - FIRST_CHAR + 1;

  private static int indexOf(char c) {
    int i = c - FIRST_CHAR;
    return (i >= TABLE_SIZE) ? -1 : i;
  }

  private final AtomicReferenceArray<PrefixTree<T>> children;
  private final Set<T> values;

  /** Create a new instance. */
  PrefixTree() {
    children = new AtomicReferenceArray<>(TABLE_SIZE);
    values = ConcurrentHashMap.newKeySet();
  }

  private PrefixTree<T> computeIfAbsent(int i) {
    PrefixTree<T> child = children.get(i);
    if (child == null) {
      synchronized (this) {
        child = children.get(i);
        if (child == null) {
          child = new PrefixTree<>();
          children.set(i, child);
        }
      }
    }
    return child;
  }

  /**
   * Put a value into the tree.
   *
   * @param prefix
   *     ASCII string that represents a prefix for the search key.
   * @param value
   *     Value to associate with the prefix.
   */
  void put(String prefix, T value) {
    if (prefix == null)
      values.add(value);
    else
      put(prefix, 0, value);
  }

  private void put(String prefix, int pos, T value) {
    if (pos == prefix.length()) {
      values.add(value);
    } else {
      int i = indexOf(prefix.charAt(pos));
      if (i < 0) {
        values.add(value);
      } else {
        PrefixTree<T> child = computeIfAbsent(i);
        child.put(prefix, pos + 1, value);
      }
    }
  }

  /**
   * Remove a value from the tree with the associated prefix.
   *
   * @param prefix
   *     ASCII string that represents a prefix for the search key.
   * @param value
   *     Value to associate with the prefix.
   * @return
   *     Returns true if a value was removed from the tree.
   */
  boolean remove(String prefix, T value) {
    if (prefix == null)
      return values.remove(value);
    else
      return remove(prefix, 0, value);
  }

  private boolean remove(String prefix, int pos, T value) {
    if (pos == prefix.length()) {
      return values.remove(value);
    } else {
      int i = indexOf(prefix.charAt(pos));
      if (i < 0) {
        return values.remove(value);
      } else {
        PrefixTree<T> child = children.get(i);
        if (child == null) {
          return false;
        } else {
          boolean result = child.remove(prefix, pos + 1, value);
          if (result && child.isEmpty()) {
            synchronized (this) {
              // Check that the children array still has the reference to the
              // same child object. The entry may have been replaced by another
              // thread.
              if (child == children.get(i) && child.isEmpty()) {
                children.set(i, null);
              }
            }
          }
          return result;
        }
      }
    }
  }

  /**
   * Get a list of values associated with a prefix of the search key.
   *
   * @param key
   *     Key to compare against the prefixes.
   * @return
   *     Values associated with a matching prefix.
   */
  List<T> get(String key) {
    List<T> results = new ArrayList<>();
    forEach(key, results::add);
    return results;
  }

  /**
   * Invokes the consumer function for each value associated with a prefix of the search key.
   *
   * @param key
   *     Key to compare against the prefixes.
   * @param consumer
   *     Function to call for matching values.
   */
  void forEach(String key, Consumer<T> consumer) {
    forEach(key, 0, consumer);
  }

  private void forEach(String key, int pos, Consumer<T> consumer) {
    values.forEach(consumer);
    if (pos < key.length()) {
      int i = indexOf(key.charAt(pos));
      if (i >= 0) {
        PrefixTree<T> child = children.get(i);
        if (child != null) {
          child.forEach(key, pos + 1, consumer);
        }
      }
    }
  }

  /**
   * Returns true if the tree is empty.
   */
  boolean isEmpty() {
    if (values.isEmpty()) {
      for (int i = 0; i < TABLE_SIZE; ++i) {
        if (children.get(i) != null) {
          return false;
        }
      }
      return true;
    } else {
      return false;
    }
  }

  /**
   * Returns the overall number of values in the tree. The size is computed on demand
   * by traversing the tree, so this call may be expensive.
   */
  int size() {
    int sz = values.size();
    for (int i = 0; i < TABLE_SIZE; ++i) {
      PrefixTree<T> child = children.get(i);
      if (child != null) {
        sz += child.size();
      }
    }
    return sz;
  }
}
