/*
 * Copyright 2014-2023 Netflix, Inc.
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

import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.impl.Cache;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;

public class QueryIndexTest {

  private final Registry registry = new DefaultRegistry();

  private final QueryIndex.CacheSupplier<Query> cacheSupplier = new QueryIndex.CacheSupplier<Query>() {
    @Override
    public Cache<String, List<QueryIndex<Query>>> get() {
      return new Cache<String, List<QueryIndex<Query>>>() {
        private final Map<String, List<QueryIndex<Query>>> data = new HashMap<>();

        @Override
        public List<QueryIndex<Query>> get(String key) {
          // Cache for a single call
          return data.remove(key);
        }

        @Override
        public List<QueryIndex<Query>> peek(String key) {
          return null;
        }

        @Override
        public void put(String key, List<QueryIndex<Query>> value) {
          data.put(key, value);
        }

        @Override
        public List<QueryIndex<Query>> computeIfAbsent(String key, Function<String, List<QueryIndex<Query>>> f) {
          return data.computeIfAbsent(key, f);
        }

        @Override
        public void clear() {
          data.clear();
        }

        @Override
        public int size() {
          return data.size();
        }

        @Override
        public Map<String, List<QueryIndex<Query>>> asMap() {
          return new HashMap<>(data);
        }
      };
    }
  };

  private Id id(String name, String... tags) {
    return registry.createId(name, tags);
  }

  private List<Query> sort(List<Query> vs) {
    vs.sort(Comparator.comparing(Object::toString));
    return vs;
  }

  private List<Query> list(Query... vs) {
    return sort(Arrays.asList(vs));
  }

  @Test
  public void empty() {
    QueryIndex<Query> idx = QueryIndex.newInstance(cacheSupplier);
    assertEquals(Collections.emptyList(), idx, id("a"));
  }

  private static final Query SIMPLE_QUERY = Parser.parseQuery("name,a,:eq,key,b,:eq,:and");

  private QueryIndex<Query> simpleIdx() {
    return QueryIndex.newInstance(cacheSupplier).add(SIMPLE_QUERY, SIMPLE_QUERY);
  }

  private void assertEquals(List<Query> expected, QueryIndex<Query> idx, Id id) {
    // Do multiple iterations just to exercise caching and cache expiration paths
    for (int i = 0; i < 4; ++i) {
      Assertions.assertEquals(expected, sort(idx.findMatches(id)));
    }
    for (int i = 0; i < 4; ++i) {
      Assertions.assertEquals(expected, sort(idx.findMatches(Query.toMap(id)::get)));
      // Only check could match case here since couldMatch should be a superset of actual
      // matches
      if (!expected.isEmpty()) {
        Assertions.assertTrue(idx.couldMatch(Query.toMap(id)::get));
      }
    }
  }

  @Test
  public void simpleMissingKey() {
    Id id = id("a", "foo", "bar");
    assertEquals(Collections.emptyList(), simpleIdx(), id);
  }

  @Test
  public void simpleMatches() {
    Id id1 = id("a", "key", "b");
    Id id2 = id("a", "foo", "bar", "key", "b");
    assertEquals(list(SIMPLE_QUERY), simpleIdx(), id1);
    assertEquals(list(SIMPLE_QUERY), simpleIdx(),id2);
  }

  @Test
  public void simpleNameDoesNotMatch() {
    Id id = id("b", "foo", "bar");
    assertEquals(Collections.emptyList(), simpleIdx(), id);
  }

  @Test
  public void simpleRemoveValue() {
    QueryIndex<Query> idx = simpleIdx();
    Assertions.assertTrue(idx.remove(SIMPLE_QUERY, SIMPLE_QUERY));
    Assertions.assertTrue(idx.isEmpty());

    Id id = id("a", "key", "b");
    Assertions.assertTrue(idx.findMatches(id).isEmpty());
  }

  private static final Query HASKEY_QUERY = Parser.parseQuery("name,a,:eq,key,b,:eq,:and,c,:has,:and");

  private QueryIndex<Query> hasKeyIdx() {
    return QueryIndex.newInstance(cacheSupplier).add(HASKEY_QUERY, HASKEY_QUERY);
  }

  @Test
  public void hasKeyMissingKey() {
    Id id = id("a", "key", "b", "foo", "bar");
    assertEquals(Collections.emptyList(), hasKeyIdx(), id);
  }

  @Test
  public void hasKeyMatches() {
    Id id1 = id("a", "key", "b", "c", "12345");
    Id id2 = id("a", "foo", "bar", "key", "b", "c", "foobar");
    assertEquals(list(HASKEY_QUERY), hasKeyIdx(), id1);
    assertEquals(list(HASKEY_QUERY), hasKeyIdx(), id2);
  }

  @Test
  public void hasKeyRepeat() {
    Id id1 = id("a", "key", "b", "c", "12345");
    QueryIndex<Query> idx = hasKeyIdx();
    for (int i = 0; i < 10; ++i) {
      // Subsequent checks for :has operation should come from cache
      assertEquals(list(HASKEY_QUERY), idx, id1);
    }
  }

  private static final Query IN_QUERY = Parser.parseQuery("name,a,:eq,key,(,b,c,),:in,:and");

  private QueryIndex<Query> inIdx() {
    return QueryIndex.newInstance(cacheSupplier).add(IN_QUERY, IN_QUERY);
  }

  @Test
  public void inMissingKey() {
    Id id = id("a", "key2", "b", "foo", "bar");
    assertEquals(Collections.emptyList(), inIdx(), id);
  }

  @Test
  public void inMatches() {
    Id id1 = id("a", "key", "b", "c", "12345");
    Id id2 = id("a", "foo", "bar", "key", "c", "c", "foobar");
    assertEquals(list(IN_QUERY), inIdx(), id1);
    assertEquals(list(IN_QUERY), inIdx(), id2);
  }

  @Test
  public void inValueNotInSet() {
    Id id = id("a", "key", "d", "c", "12345");
    assertEquals(Collections.emptyList(), inIdx(), id);
  }

  @Test
  public void trueMatches() {
    QueryIndex<Query> idx = QueryIndex.newInstance(cacheSupplier).add(Query.TRUE, Query.TRUE);
    Id id1 = id("a", "key", "b", "c", "12345");
    Id id2 = id("a", "foo", "bar", "key", "b", "c", "foobar");
    assertEquals(list(Query.TRUE), idx, id1);
    assertEquals(list(Query.TRUE), idx, id2);
  }

  @Test
  public void falseDoesNotMatch() {
    QueryIndex<Query> idx = QueryIndex.newInstance(cacheSupplier).add(Query.FALSE, Query.FALSE);
    Assertions.assertTrue(idx.isEmpty());
  }

  @Test
  public void removals() {
    QueryIndex<Query> idx = QueryIndex.newInstance(cacheSupplier);
    idx.add(SIMPLE_QUERY, SIMPLE_QUERY);
    idx.add(HASKEY_QUERY, HASKEY_QUERY);
    idx.add(IN_QUERY, IN_QUERY);

    Id id1 = id("a", "key", "b", "c", "12345");
    assertEquals(list(SIMPLE_QUERY, IN_QUERY, HASKEY_QUERY), idx, id1);

    Query q = Parser.parseQuery("name,a,:eq");
    Assertions.assertFalse(idx.remove(q, q));
    assertEquals(list(SIMPLE_QUERY, IN_QUERY, HASKEY_QUERY), idx, id1);

    Assertions.assertTrue(idx.remove(IN_QUERY, IN_QUERY));
    assertEquals(list(SIMPLE_QUERY, HASKEY_QUERY), idx, id1);

    Assertions.assertTrue(idx.remove(SIMPLE_QUERY, SIMPLE_QUERY));
    assertEquals(list(HASKEY_QUERY), idx, id1);

    Assertions.assertTrue(idx.remove(HASKEY_QUERY, HASKEY_QUERY));
    Assertions.assertTrue(idx.isEmpty());
    assertEquals(Collections.emptyList(), idx, id1);

    idx.add(SIMPLE_QUERY, SIMPLE_QUERY);
    assertEquals(list(SIMPLE_QUERY), idx, id1);
  }

  private boolean remove(QueryIndex<Query> idx, Query value) {
    return idx.remove(value, value);
  }

  @Test
  public void removalsUsingQuery() {
    QueryIndex<Query> idx = QueryIndex.newInstance(cacheSupplier);
    idx.add(SIMPLE_QUERY, SIMPLE_QUERY);
    idx.add(HASKEY_QUERY, HASKEY_QUERY);
    idx.add(IN_QUERY, IN_QUERY);

    Id id1 = id("a", "key", "b", "c", "12345");
    assertEquals(list(SIMPLE_QUERY, IN_QUERY, HASKEY_QUERY), idx, id1);

    Assertions.assertFalse(remove(idx, Parser.parseQuery("name,a,:eq")));
    assertEquals(list(SIMPLE_QUERY, IN_QUERY, HASKEY_QUERY), idx, id1);

    Assertions.assertTrue(remove(idx, IN_QUERY));
    assertEquals(list(SIMPLE_QUERY, HASKEY_QUERY), idx, id1);

    Assertions.assertTrue(remove(idx, SIMPLE_QUERY));
    assertEquals(list(HASKEY_QUERY), idx, id1);

    Assertions.assertTrue(remove(idx, HASKEY_QUERY));
    Assertions.assertTrue(idx.isEmpty());
    assertEquals(Collections.emptyList(), idx, id1);

    idx.add(SIMPLE_QUERY, SIMPLE_QUERY);
    assertEquals(list(SIMPLE_QUERY), idx, id1);
  }

  private Set<String> set(int n) {
    Set<String> tmp = new LinkedHashSet<>();
    for (int i = 0; i < n; ++i) {
      tmp.add("" + i);
    }
    return tmp;
  }

  @Test
  public void queryNormalization() {
    Query q = Parser.parseQuery("name,a,:eq,name,b,:eq,:or,key,b,:eq,:and");
    QueryIndex<Query> idx = QueryIndex.newInstance(cacheSupplier);
    idx.add(q, q);
    assertEquals(list(q), idx, id("a", "key", "b"));
    assertEquals(list(q), idx, id("b", "key", "b"));
  }

  @Test
  public void inClauseExpansion() {
    // If the :in clauses are fully expanded with a cross-product, then this will cause an OOM
    // error because of the combinatorial explosion of simple queries (10k * 10k * 10k).
    Query q1 = new Query.In("a", set(10000));
    Query q2 = new Query.In("b", set(10000));
    Query q3 = new Query.In("c", set(10000));
    Query query = q1.and(q2).and(q3);
    QueryIndex<Query> idx = QueryIndex.newInstance(cacheSupplier).add(query, query);

    Id id1 = id("cpu", "a", "1", "b", "9999", "c", "727");
    assertEquals(list(query), idx, id1);
  }

  @Test
  public void manyQueries() {
    // CpuUsage for all instances
    Query cpuUsage = Parser.parseQuery("name,cpuUsage,:eq");

    // DiskUsage query per node
    Query diskUsage = Parser.parseQuery("name,diskUsage,:eq");
    List<Query> diskUsagePerNode = new ArrayList<>();
    for (int i = 0; i < 100; ++i) {
      String node = String.format("i-%05d", i);
      Query q = new Query.And(new Query.Equal("nf.node", node), diskUsage);
      diskUsagePerNode.add(q);
    }

    QueryIndex<Query> idx = QueryIndex.newInstance(cacheSupplier)
        .add(cpuUsage, cpuUsage)
        .add(diskUsage, diskUsage);
    for (Query q : diskUsagePerNode) {
      idx.add(q, q);
    }

    // Matching
    assertEquals(
        list(cpuUsage),
        idx,
        id("cpuUsage", "nf.node", "unknown"));
    assertEquals(
        list(cpuUsage),
        idx,
        id("cpuUsage", "nf.node", "i-00099"));
    assertEquals(
        list(diskUsage),
        idx,
        id("diskUsage", "nf.node", "unknown"));
    assertEquals(
        list(diskUsage, diskUsagePerNode.get(diskUsagePerNode.size() - 1)),
        idx,
        id("diskUsage", "nf.node", "i-00099"));

    // Shouldn't match
    assertEquals(
        Collections.emptyList(),
        idx,
        id("memoryUsage", "nf.node", "i-00099"));
  }

  @Test
  public void multipleClausesForSameKey() {
    Query q = Parser.parseQuery("name,abc.*,:re,name,.*def,:re,:and");
    QueryIndex<Query> idx = QueryIndex.newInstance(cacheSupplier).add(q, q);

    // Doesn't match prefix check
    assertEquals(Collections.emptyList(), idx, id("foodef"));

    // Doesn't match suffix check
    assertEquals(Collections.emptyList(), idx, id("abcbar"));

    // Matches both
    assertEquals(list(q), idx, id("abcdef"));
    assertEquals(list(q), idx, id("abc def"));
  }

  @Test
  public void notEqClause() {
    Query q = Parser.parseQuery("name,cpu,:eq,id,user,:eq,:not,:and");
    QueryIndex<Query> idx = QueryIndex.newInstance(cacheSupplier).add(q, q);

    assertEquals(list(q), idx, id("cpu", "id", "system"));
    assertEquals(Collections.emptyList(), idx, id("cpu", "id", "user"));
  }

  @Test
  public void notEqMissingKey() {
    Query q = Parser.parseQuery("name,cpu,:eq,id,user,:eq,:not,:and");
    QueryIndex<Query> idx = QueryIndex.newInstance(cacheSupplier).add(q, q);
    assertEquals(list(q), idx, id("cpu"));
  }

  @Test
  public void notEqMissingKeyMiddle() {
    Query q = Parser.parseQuery("name,cpu,:eq,mm,foo,:eq,:not,:and,zz,bar,:eq,:and");
    QueryIndex<Query> idx = QueryIndex.newInstance(cacheSupplier).add(q, q);

    assertEquals(list(q), idx, id("cpu", "zz", "bar"));
  }

  @Test
  public void notEqMissingKeyEnd() {
    Query q = Parser.parseQuery("name,cpu,:eq,zz,foo,:eq,:not,:and");
    QueryIndex<Query> idx = QueryIndex.newInstance(cacheSupplier).add(q, q);

    assertEquals(list(q), idx, id("cpu"));
  }

  @Test
  public void multiNotEqClause() {
    Query q = Parser.parseQuery("name,cpu,:eq,id,system,:eq,:and,id,user,:eq,:not,:and");
    QueryIndex<Query> idx = QueryIndex.newInstance(cacheSupplier).add(q, q);

    assertEquals(list(q), idx, id("cpu", "id", "system"));
    assertEquals(Collections.emptyList(), idx, id("cpu", "id", "user"));
  }

  @Test
  public void notInClause() {
    Query q = Parser.parseQuery("name,cpu,:eq,id,(,user,iowait,),:in,:not,:and");
    QueryIndex<Query> idx = QueryIndex.newInstance(cacheSupplier).add(q, q);

    assertEquals(list(q), idx, id("cpu", "id", "system"));
    assertEquals(Collections.emptyList(), idx, id("cpu", "id", "user"));
    assertEquals(Collections.emptyList(), idx, id("cpu", "id", "iowait"));
  }

  @Test
  public void multiNotInClause() {
    Query q = Parser.parseQuery("name,cpu,:eq,id,system,:eq,:and,id,(,user,iowait,),:in,:not,:and");
    QueryIndex<Query> idx = QueryIndex.newInstance(cacheSupplier).add(q, q);

    assertEquals(list(q), idx, id("cpu", "id", "system"));
    assertEquals(Collections.emptyList(), idx, id("cpu", "id", "user"));
    assertEquals(Collections.emptyList(), idx, id("cpu", "id", "iowait"));
  }

  @Test
  public void doubleNotsSameKey() {
    Query q = Parser.parseQuery("a,1,:eq,b,2,:eq,:and,c,3,:eq,:not,:and,c,4,:eq,:not,:and");
    QueryIndex<Query> idx = QueryIndex.newInstance(cacheSupplier).add(q, q);
    assertEquals(list(q), idx, id("cpu", "a", "1", "b", "2", "c", "5"));
    assertEquals(Collections.emptyList(), idx, id("cpu", "a", "1", "b", "2", "c", "3"));
    assertEquals(Collections.emptyList(), idx, id("cpu", "a", "1", "b", "2", "c", "4"));
    assertEquals(list(q), idx, id("cpu", "a", "1", "b", "2"));
  }

  @Test
  public void removalOfNotQuery() {
    Query q = Parser.parseQuery("name,cpu,:eq,id,user,:eq,:not,:and");
    QueryIndex<Query> idx = QueryIndex.newInstance(cacheSupplier).add(q, q);
    Assertions.assertTrue(idx.remove(q, q));
    Assertions.assertTrue(idx.isEmpty());
  }

  @Test
  public void removalOfNotQueryUsingQuery() {
    Query q = Parser.parseQuery("name,cpu,:eq,id,user,:eq,:not,:and");
    QueryIndex<Query> idx = QueryIndex.newInstance(cacheSupplier).add(q, q);
    Assertions.assertTrue(remove(idx, q));
    Assertions.assertTrue(idx.isEmpty());
  }

  @Test
  public void removalPrefixRegexSubtree() {
    Query q1 = Parser.parseQuery("name,test,:eq,a,foo,:re,:and,b,bar,:eq,:and");
    Query q2 = Parser.parseQuery("name,test,:eq,a,foo,:re,:and");
    QueryIndex<Query> idx = QueryIndex.newInstance(cacheSupplier)
        .add(q1, q1)
        .add(q2, q2);

    Id id = id("test", "a", "foo", "b", "bar");

    assertEquals(list(q2, q1), idx, id);

    idx.remove(q1, q1);
    assertEquals(list(q2), idx, id);
  }

  @Test
  public void toStringMethod() {
    QueryIndex<Query> idx = QueryIndex.newInstance(cacheSupplier);
    idx.add(SIMPLE_QUERY, SIMPLE_QUERY);
    idx.add(HASKEY_QUERY, HASKEY_QUERY);
    idx.add(IN_QUERY, IN_QUERY);

    String expected = "key: [name]\n" +
        "equal checks:\n" +
        "- [a]\n" +
        "    key: [key]\n" +
        "    equal checks:\n" +
        "    - [b]\n" +
        "        matches:\n" +
        "        - [name,a,:eq,key,b,:eq,:and]\n" +
        "        - [name,a,:eq,key,(,b,c,),:in,:and]\n" +
        "    - [c]\n" +
        "        matches:\n" +
        "        - [name,a,:eq,key,(,b,c,),:in,:and]\n" +
        "    other keys:\n" +
        "        key: [c]\n" +
        "        has key:\n" +
        "            key: [key]\n" +
        "            equal checks:\n" +
        "            - [b]\n" +
        "                matches:\n" +
        "                - [name,a,:eq,key,b,:eq,:and,c,:has,:and]\n";

    String actual = idx.toString();

    Assertions.assertEquals(expected, actual);
  }

  @Test
  public void addRemoveFuzz() {
    Registry registry = new NoopRegistry();
    Random random = new Random(42);

    QueryIndex<Integer> idx = QueryIndex.newInstance(registry);
    for (int i = 0; i < 25; ++i) {
      int n = 1_000;
      List<Query> queries = new ArrayList<>(n);
      for (int j = 0; j < n; ++j) {
        queries.add(DataGenerator.randomQuery(random, 6));
      }

      for (int j = 0; j < n; ++j) {
        Query query = queries.get(j);
        idx.add(query, j);
      }
      for (int j = 0; j < n; ++j) {
        Query query = queries.get(j);
        Assertions.assertEquals(query != Query.FALSE, idx.remove(query, j));
        Assertions.assertFalse(idx.remove(query, j));
      }
      Assertions.assertTrue(idx.isEmpty());
    }
  }

  @Test
  public void findHotSpots() {
    Registry registry = new NoopRegistry();
    QueryIndex<Integer> idx = QueryIndex.newInstance(registry);
    for (int i = 0; i < 5; ++i) {
      Query q = Parser.parseQuery("name,foo,:re,i," + i + ",:re,:and");
      idx.add(q, i);
    }

    idx.findHotSpots(4, (path, queries) -> {
      Assertions.assertEquals(
          "K=name > other-checks > name,foo,:re > K=i > other-checks",
          String.join(" > ", path)
      );
      Assertions.assertEquals(5, queries.size());
    });
  }

  @Test
  public void couldMatchPartial() {
    Registry registry = new NoopRegistry();
    QueryIndex<Integer> idx = QueryIndex.newInstance(registry);
    Query q = Parser.parseQuery("name,foo,:eq,id,bar,:eq,:and,app,baz,:re,:and");
    idx.add(q, 42);

    Assertions.assertTrue(idx.couldMatch(Query.toMap(id("foo"))::get));
    Assertions.assertTrue(idx.couldMatch(Query.toMap(id("foo", "id", "bar"))::get));
    Assertions.assertTrue(idx.couldMatch(Query.toMap(id("foo", "app", "baz-main"))::get));

    // Since `id` is after `app` and `app` is missing in tags, it will short-circuit and
    // assume it could match
    Assertions.assertTrue(idx.couldMatch(Query.toMap(id("foo", "id", "baz"))::get));

    // Filtered out
    Assertions.assertFalse(idx.couldMatch(Query.toMap(id("foo2", "id", "bar"))::get));
    Assertions.assertFalse(idx.couldMatch(Query.toMap(id("foo", "app", "bar-main"))::get));
  }
}