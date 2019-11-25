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
package com.netflix.spectator.atlas.impl;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.impl.Cache;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Index that to efficiently match an {@link com.netflix.spectator.api.Id} against a set of
 * queries that are known in advance. The index is thread safe for queries. Updates to the
 * index should be done from a single thread at a time.
 */
public final class QueryIndex<T> {

  /**
   * Return a new instance of an index that is empty.
   */
  public static <V> QueryIndex<V> newInstance(Registry registry) {
    return new QueryIndex<>(registry, "name");
  }

  /**
   * Return a new instance of an index that is empty and doesn't have an explicit key set.
   * Used internally rather than {@link #newInstance(Registry)} which sets the key to {@code name}
   * so the root node will be correct for traversing the id.
   */
  private static <V> QueryIndex<V> empty(Registry registry) {
    return new QueryIndex<>(registry, null);
  }

  /**
   * Compare the strings and put {@code name} first and then normally sort the other keys.
   * This allows the {@link Id} to be traversed in order while performing the lookup.
   */
  private static int compare(String k1, String k2) {
    if ("name".equals(k1) && "name".equals(k2)) {
      return 0;
    } else if ("name".equals(k1)) {
      return -1;
    } else if ("name".equals(k2)) {
      return 1;
    } else {
      return k1.compareTo(k2);
    }
  }

  private final Registry registry;

  private volatile String key;

  private final ConcurrentHashMap<String, QueryIndex<T>> equalChecks;

  private final ConcurrentHashMap<Query.KeyQuery, QueryIndex<T>> otherChecks;
  private final Cache<String, List<QueryIndex<T>>> otherChecksCache;

  private volatile QueryIndex<T> otherKeysIdx;
  private volatile QueryIndex<T> missingKeysIdx;

  private final Set<T> matches;

  /** Create a new instance. */
  private QueryIndex(Registry registry, String key) {
    this.registry = registry;
    this.key = key;
    this.equalChecks = new ConcurrentHashMap<>();
    this.otherChecks = new ConcurrentHashMap<>();
    this.otherChecksCache = Cache.lfu(registry, "QueryIndex", 100, 1000);
    this.otherKeysIdx = null;
    this.missingKeysIdx = null;
    this.matches = ConcurrentHashMap.newKeySet();
  }

  private List<Query.KeyQuery> sort(Query query) {
    List<Query.KeyQuery> result = new ArrayList<>();
    for (Query q : query.andList()) {
      result.add((Query.KeyQuery) q);
    }
    result.sort((q1, q2) -> compare(q1.key(), q2.key()));
    return result;
  }

  /**
   * Add a value that should match for the specified query.
   *
   * @param query
   *     Query that corresponds to the value.
   * @param value
   *     Value to return for ids that match the query.
   * @return
   *     This index so it can be used in a fluent manner.
   */
  public QueryIndex<T> add(Query query, T value) {
    for (Query q : query.dnfList()) {
      if (q == Query.TRUE) {
        matches.add(value);
      } else if (q == Query.FALSE) {
        break;
      } else {
        add(sort(q), 0, value);
      }
    }
    return this;
  }

  private void add(List<Query.KeyQuery> queries, int i, T value) {
    if (i < queries.size()) {
      Query.KeyQuery kq = queries.get(i);

      // Check for additional queries based on the same key and combine into a
      // composite if needed
      Query.CompositeKeyQuery composite = null;
      int j = i + 1;
      while (j < queries.size()) {
        Query.KeyQuery q = queries.get(j);
        if (kq.key().equals(q.key())) {
          if (composite == null) {
            composite = new Query.CompositeKeyQuery(kq);
            kq = composite;
          }
          composite.add(q);
          ++j;
        } else {
          break;
        }
      }

      if (key == null) {
        key = kq.key();
      }

      if (key.equals(kq.key())) {
        if (kq instanceof Query.Equal) {
          String v = ((Query.Equal) kq).value();
          QueryIndex<T> idx = equalChecks.computeIfAbsent(v, id -> QueryIndex.empty(registry));
          idx.add(queries, j, value);
        } else {
          QueryIndex<T> idx = otherChecks.computeIfAbsent(kq, id -> QueryIndex.empty(registry));
          idx.add(queries, j, value);
          otherChecksCache.clear();

          // Not queries should match if the key is missing from the id, so they need to
          // be included in the other keys sub-tree as well
          if (kq instanceof Query.InvertedKeyQuery) {
            if (missingKeysIdx == null) {
              missingKeysIdx = QueryIndex.empty(registry);
            }
            missingKeysIdx.add(queries, j, value);
          }
        }
      } else {
        if (otherKeysIdx == null) {
          otherKeysIdx = QueryIndex.empty(registry);
        }
        otherKeysIdx.add(queries, i, value);
      }
    } else {
      matches.add(value);
    }
  }

  private <K> boolean remove(ConcurrentHashMap<K, QueryIndex<T>> map, T value) {
    boolean removed = false;
    Iterator<Map.Entry<K, QueryIndex<T>>> it = map.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<K, QueryIndex<T>> entry = it.next();
      QueryIndex<T> idx = entry.getValue();
      if (idx.remove(value)) {
        removed = true;
        if (idx.isEmpty()) {
          it.remove();
        }
      }
    }
    return removed;
  }

  private boolean removeFromOtherChecks(T value) {
    if (remove(otherChecks, value)) {
      otherChecksCache.clear();
      return true;
    } else {
      return false;
    }
  }

  private boolean removeFromOtherKeysIdx(T value) {
    if (otherKeysIdx != null && otherKeysIdx.remove(value)) {
      if (otherKeysIdx.isEmpty()) {
        otherKeysIdx = null;
      }
      return true;
    } else {
      return false;
    }
  }

  private boolean removeFromMissingKeysIdx(T value) {
    if (missingKeysIdx != null && missingKeysIdx.remove(value)) {
      if (missingKeysIdx.isEmpty()) {
        missingKeysIdx = null;
      }
      return true;
    } else {
      return false;
    }
  }

  /**
   * Remove the specified value from the index. Returns true if a value was successfully
   * removed.
   */
  public boolean remove(T value) {
    // Note, we use | instead of || because a value could get added on multiple paths
    // and so the short circuiting behavior is not desirable here.
    return matches.remove(value)
        | remove(equalChecks, value)
        | removeFromOtherChecks(value)
        | removeFromOtherKeysIdx(value)
        | removeFromMissingKeysIdx(value);
  }

  /**
   * Returns true if this index is empty and wouldn't match any ids.
   */
  public boolean isEmpty() {
    return matches.isEmpty()
        && equalChecks.values().stream().allMatch(QueryIndex::isEmpty)
        && otherChecks.values().stream().allMatch(QueryIndex::isEmpty)
        && (otherKeysIdx == null || otherKeysIdx.isEmpty())
        && (missingKeysIdx == null || missingKeysIdx.isEmpty());
  }

  /**
   * Find all values where the corresponding queries match the specified id.
   *
   * @param id
   *     Id to check against the queries.
   * @return
   *     List of all matching values for the id.
   */
  public List<T> findMatches(Id id) {
    List<T> result = new ArrayList<>();
    forEachMatch(id, result::add);
    return result;
  }

  /**
   * Invoke the consumer for all values where the corresponding queries match the specified id.
   *
   * @param id
   *     Id to check against the queries.
   * @param consumer
   *     Function to invoke for values associated with a query that matches the id.
   */
  public void forEachMatch(Id id, Consumer<T> consumer) {
    forEachMatch(id, 0, consumer);
  }

  private void forEachMatch(Id tags, int i, Consumer<T> consumer) {
    // Matches for this level
    matches.forEach(consumer);

    if (key != null) {

      boolean keyPresent = false;

      for (int j = i; j < tags.size(); ++j) {
        String k = tags.getKey(j);
        String v = tags.getValue(j);
        int cmp = compare(k, key);
        if (cmp == 0) {
          keyPresent = true;

          // Find exact matches
          QueryIndex<T> eqIdx = equalChecks.get(v);
          if (eqIdx != null) {
            eqIdx.forEachMatch(tags, i + 1, consumer);
          }

          // Scan for matches with other conditions
          List<QueryIndex<T>> otherMatches = otherChecksCache.get(v);
          if (otherMatches == null) {
            List<QueryIndex<T>> tmp = new ArrayList<>();
            otherChecks.forEach((kq, idx) -> {
              if (kq.matches(v)) {
                tmp.add(idx);
                idx.forEachMatch(tags, i + 1, consumer);
              }
            });
            otherChecksCache.put(v, tmp);
          } else {
            for (QueryIndex<T> idx : otherMatches) {
              idx.forEachMatch(tags, i + 1, consumer);
            }
          }
        } else if (cmp > 0) {
          break;
        }
      }

      // Check matches with other keys
      if (otherKeysIdx != null) {
        otherKeysIdx.forEachMatch(tags, i, consumer);
      }

      // Check matches with missing keys
      if (missingKeysIdx != null && !keyPresent) {
        missingKeysIdx.forEachMatch(tags, i, consumer);
      }
    }
  }

  @Override public String toString() {
    StringBuilder builder = new StringBuilder();
    buildString(builder, 0);
    return builder.toString();
  }

  private StringBuilder indent(StringBuilder builder, int n) {
    for (int i = 0; i < n * 4; ++i) {
      builder.append(' ');
    }
    return builder;
  }

  private void buildString(StringBuilder builder, int n) {
    if (key != null) {
      indent(builder, n).append("key: [").append(key).append("]\n");
    }
    if (!equalChecks.isEmpty()) {
      indent(builder, n).append("equal checks:\n");
      equalChecks.forEach((v, idx) -> {
        indent(builder, n).append("- [").append(v).append("]\n");
        idx.buildString(builder, n + 1);
      });
    }
    if (!otherChecks.isEmpty()) {
      indent(builder, n).append("other checks:\n");
      otherChecks.forEach((kq, idx) -> {
        indent(builder, n).append("- [").append(kq).append("]\n");
        idx.buildString(builder, n + 1);
      });
    }
    if (otherKeysIdx != null) {
      indent(builder, n).append("other keys:\n");
      otherKeysIdx.buildString(builder, n + 1);
    }
    if (missingKeysIdx != null) {
      indent(builder, n).append("missing keys:\n");
      missingKeysIdx.buildString(builder, n + 1);
    }
    if (!matches.isEmpty()) {
      indent(builder, n).append("matches:\n");
      for (T value : matches) {
        indent(builder, n).append("- [").append(value).append("]\n");
      }
    }
  }
}
