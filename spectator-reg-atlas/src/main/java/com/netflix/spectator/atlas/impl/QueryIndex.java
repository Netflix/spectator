/*
 * Copyright 2014-2025 Netflix, Inc.
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Index that to efficiently match an {@link com.netflix.spectator.api.Id} against a set of
 * queries that are known in advance. The index is thread safe for queries. Updates to the
 * index should be done from a single thread at a time.
 */
@SuppressWarnings("PMD.LinguisticNaming")
public final class QueryIndex<T> {

  public static class CacheValue<V> {

    private final long version;
    private final List<QueryIndex<V>> indices;

    public CacheValue(long version, List<QueryIndex<V>> indices) {
      this.version = version;
      this.indices = indices;
    }

    public long version() {
      return version;
    }

    public List<QueryIndex<V>> indices() {
      return indices;
    }
  }

  /**
   * Supplier to create a new instance of a cache used for other checks. The default should
   * be fine for most uses, but heavy uses with many expressions and high throughput may
   * benefit from an alternate implementation.
   */
  @FunctionalInterface
  public interface CacheSupplier<V> extends Supplier<Cache<String, CacheValue<V>>> {
  }

  /** Default supplier based on a simple LFU cache. */
  public static class DefaultCacheSupplier<V> implements CacheSupplier<V> {

    private final Registry registry;

    DefaultCacheSupplier(Registry registry) {
      this.registry = registry;
    }

    @Override
    public Cache<String, CacheValue<V>> get() {
      return Cache.lfu(registry, "QueryIndex", 100, 1000);
    }
  }

  /**
   * Dedup values as they are consumed. This is used to avoid processing the same result
   * multiple times in the case of OR clauses where multiple match the same data point.
   */
  private static class DedupConsumer<T> implements Consumer<T> {

    private final Consumer<T> consumer;
    private final Set<T> alreadySeen;

    DedupConsumer(Consumer<T> consumer) {
      this.consumer = consumer;
      this.alreadySeen = new HashSet<>();
    }

    @Override public void accept(T t) {
      if (alreadySeen.add(t)) {
        consumer.accept(t);
      }
    }
  }

  /**
   * Return a new instance of an index that is empty. The default caching behavior will be
   * used.
   */
  public static <V> QueryIndex<V> newInstance(Registry registry) {
    return newInstance(new DefaultCacheSupplier<>(registry));
  }

  /**
   * Return a new instance of an index that is empty. The caches will be used to cache the
   * results of regex or other checks to try and avoid scans with repeated string values
   * across many ids.
   */
  public static <V> QueryIndex<V> newInstance(CacheSupplier<V> cacheSupplier) {
    return new QueryIndex<>(cacheSupplier, "name");
  }

  /**
   * Return a new instance of an index that is empty and doesn't have an explicit key set.
   * Used internally rather than {@link #newInstance(CacheSupplier)} which sets the key to {@code name}
   * so the root node will be correct for traversing the id.
   */
  private static <V> QueryIndex<V> empty(CacheSupplier<V> cacheSupplier) {
    return new QueryIndex<>(cacheSupplier, null);
  }

  /**
   * Compare the strings and put {@code name} first and then normally sort the other keys.
   * This allows the {@link Id} to be traversed in order while performing the lookup.
   */
  private static int compare(String k1, String k2) {
    return compare(k1, k2, "name".equals(k2));
  }

  /**
   * Variant of {@link #compare(String, String)} where the caller has already determined
   * whether {@code k2} is the {@code name} key. In the hot matching path {@code k2} is the
   * fixed key for a node, so this check can be hoisted out of the per-tag loop rather than
   * recomputed on every comparison.
   */
  private static int compare(String k1, String k2, boolean k2IsName) {
    if ("name".equals(k1)) {
      return k2IsName ? 0 : -1;
    } else {
      return k2IsName ? 1 : k1.compareTo(k2);
    }
  }

  /**
   * Compare a tag key at a given position against the fixed key for a node while traversing
   * an id. Only position 0 holds the synthesized {@code name} key, so it needs the full
   * name-first comparison. Positions {@code >= 1} come from the tag list, which is sorted by
   * {@link String#compareTo}, so the name-first special-casing can be skipped there: even if
   * a tag is literally keyed {@code name}, any {@code keyRef} that sorts before {@code name}
   * cannot appear after that entry in the sorted scan, so a plain comparison never skips a
   * match.
   */
  private static int compareTagKey(String k, String keyRef, boolean keyRefIsName, int position) {
    if (position == 0) {
      return compare(k, keyRef, keyRefIsName);
    } else {
      return keyRefIsName ? 1 : k.compareTo(keyRef);
    }
  }

  private final CacheSupplier<T> cacheSupplier;

  private volatile String key;

  // Checks for :eq clauses
  private final ConcurrentHashMap<String, QueryIndex<T>> equalChecks;

  // Checks for other key queries, e.g. :re, :in, :gt, :lt, etc. Prefix tree is used to
  // filter regex and in clauses. The matching is cached to avoid expensive regex checks
  // as much as possible.
  private final ConcurrentHashMap<Query.KeyQuery, QueryIndex<T>> otherChecks;
  private final PrefixTree otherChecksTree;
  private final Cache<String, CacheValue<T>> otherChecksCache;
  private final AtomicLong otherChecksVersion;

  // Index for :has queries
  private volatile QueryIndex<T> hasKeyIdx;

  // Index for queries that do not have a clause for a given key
  private volatile QueryIndex<T> otherKeysIdx;

  // Index for :not queries to capture entries where a key is missing
  private volatile QueryIndex<T> missingKeysIdx;

  // Matches for this level of the tree
  private final Set<T> matches;

  /** Create a new instance. */
  private QueryIndex(CacheSupplier<T> cacheSupplier, String key) {
    this.cacheSupplier = cacheSupplier;
    this.key = key;
    this.equalChecks = new ConcurrentHashMap<>();
    this.otherChecks = new ConcurrentHashMap<>();
    this.otherChecksTree = new PrefixTree();
    this.otherChecksCache = cacheSupplier.get();
    this.otherChecksVersion = new AtomicLong();
    this.hasKeyIdx = null;
    this.otherKeysIdx = null;
    this.missingKeysIdx = null;
    this.matches = new CopyOnWriteArraySet<>();
  }

  private List<Query.KeyQuery> sort(Query query) {
    List<Query.KeyQuery> result = new ArrayList<>();
    for (Query q : query.andList()) {
      result.add((Query.KeyQuery) q);
    }
    result.sort((q1, q2) -> compare(q1.key(), q2.key()));
    return mergeSameKey(result);
  }

  /**
   * Merge clauses that apply to the same key within a conjunction. The list must already be sorted
   * by key so that same-key clauses are adjacent. Equivalence-preserving simplifications are applied
   * to reduce the number of entries that end up in a {@link Query.CompositeKeyQuery} (and the
   * per-value dispatch and index size that come with it):
   *
   * <ul>
   *   <li>a positive {@code eq} pins the value, so the run reduces to that {@code eq} when the other
   *       same-key clauses all accept the value;</li>
   *   <li>{@code !a && !b == !(a || b)} for negated regexes on the same key (and case sensitivity)
   *       collapses to a single negated alternation;</li>
   *   <li>{@code !eq(a) && !eq(b) == !in(a, b)} for negated equals/ins collapses to a single
   *       negated {@code in}.</li>
   * </ul>
   *
   * <p>Clauses that are not part of a mergeable group (other positive checks, other negated types,
   * or a lone member of a group) are passed through unchanged. Returns {@code null} if the
   * conjunction is statically contradictory (e.g. two different {@code eq} values for one key) and
   * can be skipped entirely; the same merge runs for {@code add} and {@code remove}, so a dropped
   * conjunction is consistently absent from the index.</p>
   */
  // null is the signal for "conjunction is false"; an empty list would instead mean "no
  // constraints" (matches everything), so the two cannot be conflated.
  @SuppressWarnings("PMD.ReturnEmptyCollectionRatherThanNull")
  private static List<Query.KeyQuery> mergeSameKey(List<Query.KeyQuery> sorted) {
    final int n = sorted.size();
    if (n < 2) {
      return sorted;
    }
    List<Query.KeyQuery> result = new ArrayList<>(n);
    int i = 0;
    while (i < n) {
      final String key = sorted.get(i).key();
      int j = i + 1;
      while (j < n && sorted.get(j).key().equals(key)) {
        ++j;
      }
      if (j - i == 1) {
        result.add(sorted.get(i));
      } else if (mergeRun(key, sorted.subList(i, j), result)) {
        // Run is contradictory, so the whole conjunction is FALSE; drop it from the index.
        return null;
      }
      i = j;
    }
    return result;
  }

  /**
   * Merge a run of two or more clauses that share the same key, appending the result to {@code out}.
   * Returns true if the run is contradictory, meaning the enclosing conjunction can never match.
   */
  private static boolean mergeRun(String key, List<Query.KeyQuery> run, List<Query.KeyQuery> out) {
    // A positive equality pins the value, so every other same-key clause becomes statically
    // decidable. When a single equality value satisfies all the other clauses the run reduces to
    // that eq (e.g. foo,a,:eq,foo,!=b reduces to foo,a,:eq). When the run is contradictory -- two
    // different eq values, or an eq value rejected by another clause -- the conjunction can never
    // match and is reported as such so the caller can drop it.
    Set<String> eqValues = positiveEqualValues(run);
    if (eqValues.size() == 1 && allAccept(eqValues.iterator().next(), run)) {
      out.add(new Query.Equal(key, eqValues.iterator().next()));
      return false;
    } else if (!eqValues.isEmpty()) {
      return true;
    }

    // No positive equality: merge negated clauses on the same key.
    // !a && !b == !(a || b); !eq(a) && !eq(b) == !in(a, b).
    // Negated regexes are grouped by operator name (so :re and :reic never mix) and, within a name,
    // by value (so duplicate clauses collapse); each value keeps its original clause so a singleton
    // group can be emitted as-is without recompiling its pattern.
    Map<String, Map<String, Query.KeyQuery>> negRegex = new LinkedHashMap<>();
    List<Query.KeyQuery> negEqIn = new ArrayList<>();
    List<Query.KeyQuery> others = new ArrayList<>();
    for (Query.KeyQuery kq : run) {
      classifyForMerge(kq, negRegex, negEqIn, others);
    }
    emitMergedRegex(key, negRegex, out);
    emitMergedEqIn(key, negEqIn, out);
    out.addAll(others);
    return false;
  }

  /** Distinct values of positive (non-inverted) {@link Query.Equal} clauses in the run. */
  private static Set<String> positiveEqualValues(List<Query.KeyQuery> run) {
    Set<String> values = new LinkedHashSet<>();
    for (Query.KeyQuery kq : run) {
      if (kq instanceof Query.Equal) {
        values.add(((Query.Equal) kq).value());
      }
    }
    return values;
  }

  /** True if every clause in the run accepts {@code value} (so they are all implied by {@code eq}). */
  private static boolean allAccept(String value, List<Query.KeyQuery> run) {
    for (Query.KeyQuery kq : run) {
      if (!kq.matches(value)) {
        return false;
      }
    }
    return true;
  }

  /** Bucket a clause into the negated-regex, negated-eq/in, or pass-through group. */
  private static void classifyForMerge(
      Query.KeyQuery kq,
      Map<String, Map<String, Query.KeyQuery>> negRegex,
      List<Query.KeyQuery> negEqIn,
      List<Query.KeyQuery> others) {
    Query inner = (kq instanceof Query.InvertedKeyQuery) ? kq.not() : null;
    if (inner instanceof Query.Regex) {
      Query.Regex re = (Query.Regex) inner;
      negRegex.computeIfAbsent(re.name(), k -> new LinkedHashMap<>()).putIfAbsent(re.value(), kq);
    } else if (inner instanceof Query.Equal || inner instanceof Query.In) {
      negEqIn.add(kq);
    } else {
      others.add(kq);
    }
  }

  /**
   * {@code !a && !b == !(a || b)}. Each alternative carries its own leading anchor: {@link Query.Regex}
   * compiles the value with a prepended {@code "^"}, so joining with {@code "|^"} makes every branch
   * independently start-anchored, preserving each original clause's semantics regardless of internal
   * alternation. A group of one is emitted unchanged (the original clause, so its pattern is not
   * recompiled). The case-sensitivity of a rebuilt alternation is taken from the operator name:
   * {@code :reic} is the only ignore-case regex operator, so a new ignore-case operator with a
   * different name would need updating here.
   */
  private static void emitMergedRegex(
      String key, Map<String, Map<String, Query.KeyQuery>> negRegex, List<Query.KeyQuery> out) {
    for (Map.Entry<String, Map<String, Query.KeyQuery>> e : negRegex.entrySet()) {
      Map<String, Query.KeyQuery> byValue = e.getValue();
      if (byValue.size() == 1) {
        out.add(byValue.values().iterator().next());
      } else {
        String name = e.getKey();
        String value = String.join("|^", byValue.keySet());
        out.add(new Query.InvertedKeyQuery(new Query.Regex(key, value, ":reic".equals(name), name)));
      }
    }
  }

  /** {@code !eq(a) && !eq(b) == !in(a, b)}; a lone negated equal/in is emitted unchanged. */
  private static void emitMergedEqIn(String key, List<Query.KeyQuery> negEqIn, List<Query.KeyQuery> out) {
    if (negEqIn.size() == 1) {
      out.add(negEqIn.get(0));
    } else if (negEqIn.size() >= 2) {
      Set<String> union = new LinkedHashSet<>();
      for (Query.KeyQuery kq : negEqIn) {
        Query inner = kq.not();
        if (inner instanceof Query.Equal) {
          union.add(((Query.Equal) inner).value());
        } else {
          union.addAll(((Query.In) inner).values());
        }
      }
      out.add(new Query.InvertedKeyQuery(new Query.In(key, union)));
    }
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
        continue;
      } else {
        List<Query.KeyQuery> queries = sort(q);
        if (queries != null) {
          add(queries, 0, value);
        }
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
          QueryIndex<T> idx = equalChecks.computeIfAbsent(v, id -> QueryIndex.empty(cacheSupplier));
          idx.add(queries, j, value);
        } else if (kq instanceof Query.Has) {
          if (hasKeyIdx == null) {
            hasKeyIdx = QueryIndex.empty(cacheSupplier);
          }
          hasKeyIdx.add(queries, j, value);
        } else {
          QueryIndex<T> idx = otherChecks.computeIfAbsent(kq, id -> QueryIndex.empty(cacheSupplier));
          idx.add(queries, j, value);
          if (otherChecksTree.put(kq)) {
            otherChecksVersion.incrementAndGet();
          }

          // Not queries should match if the key is missing from the id, so they need to
          // be included in the other keys sub-tree as well. Check this by seeing if it will
          // match an empty map as there could be a variety of inverted types.
          if (kq.matches(Collections.emptyMap())) {
            if (missingKeysIdx == null) {
              missingKeysIdx = QueryIndex.empty(cacheSupplier);
            }
            missingKeysIdx.add(queries, j, value);
          }
        }
      } else {
        if (otherKeysIdx == null) {
          otherKeysIdx = QueryIndex.empty(cacheSupplier);
        }
        otherKeysIdx.add(queries, i, value);
      }
    } else {
      matches.add(value);
    }
  }

  /**
   * Remove the specified value associated with a specific query from the index. Returns
   * true if a value was successfully removed.
   */
  public boolean remove(Query query, T value) {
    boolean result = false;
    for (Query q : query.dnfList()) {
      if (q == Query.TRUE) {
        result |= matches.remove(value);
      } else if (q == Query.FALSE) {
        continue;
      } else {
        List<Query.KeyQuery> queries = sort(q);
        if (queries != null) {
          result |= remove(queries, 0, value);
        }
      }
    }
    return result;
  }

  private boolean remove(List<Query.KeyQuery> queries, int i, T value) {
    boolean result = false;
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

      if (key != null && key.equals(kq.key())) {
        if (kq instanceof Query.Equal) {
          String v = ((Query.Equal) kq).value();
          QueryIndex<T> idx = equalChecks.get(v);
          if (idx != null) {
            result |= idx.remove(queries, j, value);
            if (idx.isEmpty())
              equalChecks.remove(v);
          }
        } else if (kq instanceof Query.Has) {
          if (hasKeyIdx != null) {
            result |= hasKeyIdx.remove(queries, j, value);
            if (hasKeyIdx.isEmpty())
              hasKeyIdx = null;
          }
        } else {
          QueryIndex<T> idx = otherChecks.get(kq);
          if (idx != null && idx.remove(queries, j, value)) {
            result = true;
            if (idx.isEmpty()) {
              otherChecks.remove(kq);
              if (otherChecksTree.remove(kq)) {
                otherChecksVersion.incrementAndGet();
              }
            }
          }

          // Not queries should match if the key is missing from the id, so they need to
          // be included in the other keys sub-tree as well. Check this by seeing if it will
          // match an empty map as there could be a variety of inverted types.
          if (kq.matches(Collections.emptyMap()) && missingKeysIdx != null) {
            result |= missingKeysIdx.remove(queries, j, value);
            if (missingKeysIdx.isEmpty())
              missingKeysIdx = null;
          }
        }
      } else if (otherKeysIdx != null) {
        result |= otherKeysIdx.remove(queries, i, value);
        if (otherKeysIdx.isEmpty())
          otherKeysIdx = null;
      }
    } else {
      result |= matches.remove(value);
    }

    return result;
  }

  /** Get cached matches for the value or compute a new one. */
  private List<QueryIndex<T>> otherChecksComputeIfAbsent(String value) {
    // Most nodes only have :eq children and no other checks. Short circuit before touching
    // the cache so the hot matching path avoids the cache lookup and its associated counter
    // updates (hit/miss plus per-entry frequency), which otherwise dominate when scanning ids.
    if (otherChecks.isEmpty()) {
      return Collections.emptyList();
    }
    CacheValue<T> cacheValue = otherChecksCache.get(value);
    long version = otherChecksVersion.get();
    if (cacheValue != null && cacheValue.version == version) {
      // Cached value on consistent version of other checks, use the cached value
      return cacheValue.indices;
    } else {
      // Compute a new value
      List<QueryIndex<T>> tmp = new ArrayList<>();
      otherChecksTree.forEach(value, kq -> {
        if (kq instanceof Query.In || matches(kq, value)) {
          QueryIndex<T> idx = otherChecks.get(kq);
          if (idx != null) {
            tmp.add(idx);
          }
        }
      });
      otherChecksCache.put(value, new CacheValue<>(version, tmp));
      return tmp;
    }
  }

  /**
   * Returns true if this index is empty and wouldn't match any ids.
   */
  public boolean isEmpty() {
    return matches.isEmpty()
        && equalChecks.values().stream().allMatch(QueryIndex::isEmpty)
        && otherChecks.values().stream().allMatch(QueryIndex::isEmpty)
        && isEmpty(hasKeyIdx)
        && isEmpty(otherKeysIdx)
        && isEmpty(missingKeysIdx);
  }

  private boolean isEmpty(QueryIndex<T> idx) {
    return idx == null || idx.isEmpty();
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
    forEachMatch(id, 0, new DedupConsumer<>(consumer));
  }

  private void forEachMatch(Id tags, int i, Consumer<T> consumer) {
    // Matches for this level
    matches.forEach(consumer);

    final String keyRef = key;
    if (keyRef != null) {

      boolean keyPresent = false;

      // keyRef is fixed for this node, so the "name" check only needs to be done once
      // here rather than on every comparison within the loop below.
      final boolean keyRefIsName = "name".equals(keyRef);

      final int tagsSize = tags.size();
      for (int j = i; j < tagsSize; ++j) {
        String k = tags.getKey(j);
        String v = tags.getValue(j);
        int cmp = compareTagKey(k, keyRef, keyRefIsName, j);
        if (cmp == 0) {
          final int nextPos = j + 1;
          keyPresent = true;

          // Find exact matches
          QueryIndex<T> eqIdx = equalChecks.get(v);
          if (eqIdx != null) {
            eqIdx.forEachMatch(tags, nextPos, consumer);
          }

          // Scan for matches with other conditions
          List<QueryIndex<T>> otherMatches = otherChecksComputeIfAbsent(v);

          // Enhanced for loop typically results in iterator being allocated. Using
          // size/get avoids the allocation and has better throughput.
          final int n = otherMatches.size();
          for (int p = 0; p < n; ++p) {
            otherMatches.get(p).forEachMatch(tags, nextPos, consumer);
          }

          // Check matches for has key
          final QueryIndex<T> hasKeyIdxRef = hasKeyIdx;
          if (hasKeyIdxRef != null) {
            hasKeyIdxRef.forEachMatch(tags, j, consumer);
          }
        }

        // Quit loop if the key was found or not present
        if (cmp >= 0) {
          break;
        }
      }

      // Check matches with other keys
      final QueryIndex<T> otherKeysIdxRef = otherKeysIdx;
      if (otherKeysIdxRef != null) {
        otherKeysIdxRef.forEachMatch(tags, i, consumer);
      }

      // Check matches with missing keys
      final QueryIndex<T> missingKeysIdxRef = missingKeysIdx;
      if (missingKeysIdxRef != null && !keyPresent) {
        missingKeysIdxRef.forEachMatch(tags, i, consumer);
      }
    }
  }

  /**
   * Find all values where the corresponding queries match the specified tags. This can be
   * used if the tags are not already structured as a spectator Id.
   *
   * @param tags
   *     Function to look up the value for a given tag key. The function should return
   *     {@code null} if there is no value for the key.
   * @return
   *     List of all matching values for the id.
   */
  public List<T> findMatches(Function<String, String> tags) {
    List<T> result = new ArrayList<>();
    forEachMatch(tags, result::add);
    return result;
  }

  /**
   * Invoke the consumer for all values where the corresponding queries match the specified tags.
   * This can be used if the tags are not already structured as a spectator Id.
   *
   * @param tags
   *     Function to look up the value for a given tag key. The function should return
   *     {@code null} if there is no value for the key.
   * @param consumer
   *     Function to invoke for values associated with a query that matches the id.
   */
  public void forEachMatch(Function<String, String> tags, Consumer<T> consumer) {
    forEachMatchImpl(tags, new DedupConsumer<>(consumer));
  }

  private void forEachMatchImpl(Function<String, String> tags, Consumer<T> consumer) {
    // Matches for this level
    matches.forEach(consumer);

    boolean keyPresent = false;
    final String keyRef = key;
    if (keyRef != null) {
      String v = tags.apply(keyRef);
      if (v != null) {
        keyPresent = true;

        // Find exact matches
        QueryIndex<T> eqIdx = equalChecks.get(v);
        if (eqIdx != null) {
          eqIdx.forEachMatch(tags, consumer);
        }

        // Scan for matches with other conditions
        List<QueryIndex<T>> otherMatches = otherChecksComputeIfAbsent(v);

        // Enhanced for loop typically results in iterator being allocated. Using
        // size/get avoids the allocation and has better throughput.
        final int n = otherMatches.size();
        for (int p = 0; p < n; ++p) {
          otherMatches.get(p).forEachMatch(tags, consumer);
        }

        // Check matches for has key
        final QueryIndex<T> hasKeyIdxRef = hasKeyIdx;
        if (hasKeyIdxRef != null) {
          hasKeyIdxRef.forEachMatch(tags, consumer);
        }
      }
    }

    // Check matches with other keys
    final QueryIndex<T> otherKeysIdxRef = otherKeysIdx;
    if (otherKeysIdxRef != null) {
      otherKeysIdxRef.forEachMatch(tags, consumer);
    }

    // Check matches with missing keys
    final QueryIndex<T> missingKeysIdxRef = missingKeysIdx;
    if (missingKeysIdxRef != null && !keyPresent) {
      missingKeysIdxRef.forEachMatch(tags, consumer);
    }
  }

  /**
   * Check the set of tags, which could be a partial set, and return true if it is possible
   * that it would match some set of expressions. This method can be used as a cheap pre-filter
   * check. In some cases this can be useful to avoid expensive transforms to get the final
   * set of tags for matching.
   *
   * @param tags
   *     Partial set of tags to check against the index. Function is used to look up the
   *     value for a given tag key. The function should return {@code null} if there is no
   *     value for the key.
   * @return
   *     True if it is possible there would be a match based on the partial set of tags.
   */
  public boolean couldMatch(Function<String, String> tags) {
    // Matches for this level
    if (!matches.isEmpty()) {
      return true;
    }

    boolean keyPresent = false;
    final String keyRef = key;
    if (keyRef != null) {
      String v = tags.apply(keyRef);
      if (v != null) {
        keyPresent = true;

        // Check exact matches
        QueryIndex<T> eqIdx = equalChecks.get(v);
        if (eqIdx != null && eqIdx.couldMatch(tags)) {
          return true;
        }

        // Scan for matches with other conditions
        if (!otherChecks.isEmpty()) {
          boolean otherMatches = otherChecksTree.exists(v, kq -> {
            if (kq instanceof Query.In || couldMatch(kq, v)) {
              QueryIndex<T> idx = otherChecks.get(kq);
              return idx != null && idx.couldMatch(tags);
            }
            return false;
          });
          if (otherMatches) {
            return true;
          }
        }

        // Check matches for has key
        final QueryIndex<T> hasKeyIdxRef = hasKeyIdx;
        if (hasKeyIdxRef != null && hasKeyIdxRef.couldMatch(tags)) {
          return true;
        }
      }
    }

    // Check matches with other keys
    final QueryIndex<T> otherKeysIdxRef = otherKeysIdx;
    if (otherKeysIdxRef != null && otherKeysIdxRef.couldMatch(tags)) {
      return true;
    }

    // Check matches with missing keys
    return !keyPresent;
  }

  private boolean matches(Query.KeyQuery kq, String value) {
    if (kq instanceof Query.Regex) {
      Query.Regex re = (Query.Regex) kq;
      return re.pattern().matchesAfterPrefix(value);
    } else {
      return kq.matches(value);
    }
  }

  private boolean couldMatch(Query.KeyQuery kq, String value) {
    if (kq instanceof Query.Regex) {
      // For this possible matches prefix check is sufficient, avoid full regex to
      // keep the pre-filter checks cheap.
      return true;
    } else {
      return kq.matches(value);
    }
  }

  /**
   * Find hot spots in the index where there is a large set of linear matches, e.g. a bunch
   * of regex queries for a given key.
   *
   * @param threshold
   *     Threshold for the number of entries in the other checks sub-tree to be considered
   *     a hot spot.
   * @param consumer
   *     Function that will be invoked with a path and set of queries for the hot spot.
   */
  public void findHotSpots(int threshold, BiConsumer<List<String>, List<Query.KeyQuery>> consumer) {
    Deque<String> path = new ArrayDeque<>();
    findHotSpots(threshold, path, consumer);
  }

  private void findHotSpots(
      int threshold,
      Deque<String> path,
      BiConsumer<List<String>, List<Query.KeyQuery>> consumer
  ) {
    final String keyRef = key;
    if (keyRef != null) {
      path.addLast("K=" + keyRef);

      equalChecks.forEach((v, idx) -> {
        path.addLast(keyRef + "," + v + ",:eq");
        idx.findHotSpots(threshold, path, consumer);
        path.removeLast();
      });

      path.addLast("other-checks");
      if (otherChecks.size() > threshold) {
        List<Query.KeyQuery> queries = new ArrayList<>(otherChecks.keySet());
        consumer.accept(new ArrayList<>(path), queries);
      }
      otherChecks.forEach((q, idx) -> {
        path.addLast(q.toString());
        idx.findHotSpots(threshold, path, consumer);
        path.removeLast();
      });
      path.removeLast();

      final QueryIndex<T> hasKeyIdxRef = hasKeyIdx;
      if (hasKeyIdxRef != null) {
        path.addLast("has");
        hasKeyIdxRef.findHotSpots(threshold, path, consumer);
        path.removeLast();
      }

      path.removeLast();
    }

    final QueryIndex<T> otherKeysIdxRef = otherKeysIdx;
    if (otherKeysIdxRef != null) {
      path.addLast("other-keys");
      otherKeysIdxRef.findHotSpots(threshold, path, consumer);
      path.removeLast();
    }

    final QueryIndex<T> missingKeysIdxRef = missingKeysIdx;
    if (missingKeysIdxRef != null) {
      path.addLast("missing-keys");
      missingKeysIdxRef.findHotSpots(threshold, path, consumer);
      path.removeLast();
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
    final String keyRef = key;
    if (keyRef != null) {
      indent(builder, n).append("key: [").append(keyRef).append("]\n");
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
    final QueryIndex<T> hasKeyIdxRef = hasKeyIdx;
    if (hasKeyIdxRef != null) {
      indent(builder, n).append("has key:\n");
      hasKeyIdxRef.buildString(builder, n + 1);
    }
    final QueryIndex<T> otherKeysIdxRef = otherKeysIdx;
    if (otherKeysIdxRef != null) {
      indent(builder, n).append("other keys:\n");
      otherKeysIdxRef.buildString(builder, n + 1);
    }
    final QueryIndex<T> missingKeysIdxRef = missingKeysIdx;
    if (missingKeysIdxRef != null) {
      indent(builder, n).append("missing keys:\n");
      missingKeysIdxRef.buildString(builder, n + 1);
    }
    if (!matches.isEmpty()) {
      indent(builder, n).append("matches:\n");
      for (T value : matches) {
        indent(builder, n).append("- [").append(value).append("]\n");
      }
    }
  }
}
