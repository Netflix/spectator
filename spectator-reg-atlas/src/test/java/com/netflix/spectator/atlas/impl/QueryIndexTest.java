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

import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class QueryIndexTest {

  private final Registry registry = new DefaultRegistry();

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

  private List<Query> findMatches(QueryIndex<Query> idx, Id id) {
    return sort(idx.findMatches(id));
  }

  @Test
  public void empty() {
    QueryIndex<Integer> idx = QueryIndex.newInstance(registry);
    Assertions.assertTrue(idx.findMatches(id("a")).isEmpty());
  }

  private static final Query SIMPLE_QUERY = Parser.parseQuery("name,a,:eq,key,b,:eq,:and");

  private QueryIndex<Query> simpleIdx() {
    return QueryIndex.<Query>newInstance(registry).add(SIMPLE_QUERY, SIMPLE_QUERY);
  }

  @Test
  public void simpleMissingKey() {
    Id id = id("a", "foo", "bar");
    Assertions.assertTrue(simpleIdx().findMatches(id).isEmpty());
  }

  @Test
  public void simpleMatches() {
    Id id1 = id("a", "key", "b");
    Id id2 = id("a", "foo", "bar", "key", "b");
    Assertions.assertEquals(list(SIMPLE_QUERY), simpleIdx().findMatches(id1));
    Assertions.assertEquals(list(SIMPLE_QUERY), simpleIdx().findMatches(id2));
  }

  @Test
  public void simpleNameDoesNotMatch() {
    Id id = id("b", "foo", "bar");
    Assertions.assertTrue(simpleIdx().findMatches(id).isEmpty());
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
    return QueryIndex.<Query>newInstance(registry).add(HASKEY_QUERY, HASKEY_QUERY);
  }

  @Test
  public void hasKeyMissingKey() {
    Id id = id("a", "key", "b", "foo", "bar");
    Assertions.assertTrue(hasKeyIdx().findMatches(id).isEmpty());
  }

  @Test
  public void hasKeyMatches() {
    Id id1 = id("a", "key", "b", "c", "12345");
    Id id2 = id("a", "foo", "bar", "key", "b", "c", "foobar");
    Assertions.assertEquals(list(HASKEY_QUERY), hasKeyIdx().findMatches(id1));
    Assertions.assertEquals(list(HASKEY_QUERY), hasKeyIdx().findMatches(id2));
  }

  @Test
  public void hasKeyRepeat() {
    Id id1 = id("a", "key", "b", "c", "12345");
    QueryIndex<Query> idx = hasKeyIdx();
    for (int i = 0; i < 10; ++i) {
      // Subsequent checks for :has operation should come from cache
      Assertions.assertEquals(list(HASKEY_QUERY), idx.findMatches(id1));
    }
  }

  private static final Query IN_QUERY = Parser.parseQuery("name,a,:eq,key,(,b,c,),:in,:and");

  private QueryIndex<Query> inIdx() {
    return QueryIndex.<Query>newInstance(registry).add(IN_QUERY, IN_QUERY);
  }

  @Test
  public void inMissingKey() {
    Id id = id("a", "key2", "b", "foo", "bar");
    Assertions.assertTrue(inIdx().findMatches(id).isEmpty());
  }

  @Test
  public void inMatches() {
    Id id1 = id("a", "key", "b", "c", "12345");
    Id id2 = id("a", "foo", "bar", "key", "c", "c", "foobar");
    Assertions.assertEquals(list(IN_QUERY), inIdx().findMatches(id1));
    Assertions.assertEquals(list(IN_QUERY), inIdx().findMatches(id2));
  }

  @Test
  public void inValueNotInSet() {
    Id id = id("a", "key", "d", "c", "12345");
    Assertions.assertTrue(inIdx().findMatches(id).isEmpty());
  }

  @Test
  public void trueMatches() {
    QueryIndex<Query> idx = QueryIndex.<Query>newInstance(registry).add(Query.TRUE, Query.TRUE);
    Id id1 = id("a", "key", "b", "c", "12345");
    Id id2 = id("a", "foo", "bar", "key", "b", "c", "foobar");
    Assertions.assertEquals(list(Query.TRUE), idx.findMatches(id1));
    Assertions.assertEquals(list(Query.TRUE), idx.findMatches(id2));
  }

  @Test
  public void falseDoesNotMatch() {
    QueryIndex<Query> idx = QueryIndex.<Query>newInstance(registry).add(Query.FALSE, Query.FALSE);
    Assertions.assertTrue(idx.isEmpty());
  }

  @Test
  public void removals() {
    QueryIndex<Query> idx = QueryIndex.newInstance(registry);
    idx.add(SIMPLE_QUERY, SIMPLE_QUERY);
    idx.add(HASKEY_QUERY, HASKEY_QUERY);
    idx.add(IN_QUERY, IN_QUERY);

    Id id1 = id("a", "key", "b", "c", "12345");
    Assertions.assertEquals(list(SIMPLE_QUERY, IN_QUERY, HASKEY_QUERY), findMatches(idx, id1));

    Query q = Parser.parseQuery("name,a,:eq");
    Assertions.assertFalse(idx.remove(q, q));
    Assertions.assertEquals(list(SIMPLE_QUERY, IN_QUERY, HASKEY_QUERY), findMatches(idx, id1));

    Assertions.assertTrue(idx.remove(IN_QUERY, IN_QUERY));
    Assertions.assertEquals(list(SIMPLE_QUERY, HASKEY_QUERY), findMatches(idx, id1));

    Assertions.assertTrue(idx.remove(SIMPLE_QUERY, SIMPLE_QUERY));
    Assertions.assertEquals(list(HASKEY_QUERY), findMatches(idx, id1));

    Assertions.assertTrue(idx.remove(HASKEY_QUERY, HASKEY_QUERY));
    Assertions.assertTrue(idx.isEmpty());
    Assertions.assertTrue(idx.findMatches(id1).isEmpty());

    idx.add(SIMPLE_QUERY, SIMPLE_QUERY);
    Assertions.assertEquals(list(SIMPLE_QUERY), findMatches(idx, id1));
  }

  private boolean remove(QueryIndex<Query> idx, Query value) {
    return idx.remove(value, value);
  }

  @Test
  public void removalsUsingQuery() {
    QueryIndex<Query> idx = QueryIndex.newInstance(registry);
    idx.add(SIMPLE_QUERY, SIMPLE_QUERY);
    idx.add(HASKEY_QUERY, HASKEY_QUERY);
    idx.add(IN_QUERY, IN_QUERY);

    Id id1 = id("a", "key", "b", "c", "12345");
    Assertions.assertEquals(list(SIMPLE_QUERY, IN_QUERY, HASKEY_QUERY), findMatches(idx, id1));

    Assertions.assertFalse(remove(idx, Parser.parseQuery("name,a,:eq")));
    Assertions.assertEquals(list(SIMPLE_QUERY, IN_QUERY, HASKEY_QUERY), findMatches(idx, id1));

    Assertions.assertTrue(remove(idx, IN_QUERY));
    Assertions.assertEquals(list(SIMPLE_QUERY, HASKEY_QUERY), findMatches(idx, id1));

    Assertions.assertTrue(remove(idx, SIMPLE_QUERY));
    Assertions.assertEquals(list(HASKEY_QUERY), findMatches(idx, id1));

    Assertions.assertTrue(remove(idx, HASKEY_QUERY));
    Assertions.assertTrue(idx.isEmpty());
    Assertions.assertTrue(idx.findMatches(id1).isEmpty());

    idx.add(SIMPLE_QUERY, SIMPLE_QUERY);
    Assertions.assertEquals(list(SIMPLE_QUERY), idx.findMatches(id1));
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
    QueryIndex<Query> idx = QueryIndex.newInstance(registry);
    idx.add(q, q);
    Assertions.assertEquals(list(q), idx.findMatches(id("a", "key", "b")));
    Assertions.assertEquals(list(q), idx.findMatches(id("b", "key", "b")));
  }

  @Test
  public void inClauseExpansion() {
    // If the :in clauses are fully expanded with a cross-product, then this will cause an OOM
    // error because of the combinatorial explosion of simple queries (10k * 10k * 10k).
    Query q1 = new Query.In("a", set(10000));
    Query q2 = new Query.In("b", set(10000));
    Query q3 = new Query.In("c", set(10000));
    Query query = q1.and(q2).and(q3);
    QueryIndex<Query> idx = QueryIndex.<Query>newInstance(registry).add(query, query);

    Id id1 = id("cpu", "a", "1", "b", "9999", "c", "727");
    Assertions.assertEquals(list(query), idx.findMatches(id1));
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

    QueryIndex<Query> idx = QueryIndex.<Query>newInstance(registry)
        .add(cpuUsage, cpuUsage)
        .add(diskUsage, diskUsage);
    for (Query q : diskUsagePerNode) {
      idx.add(q, q);
    }

    // Matching
    Assertions.assertEquals(
        list(cpuUsage),
        idx.findMatches(id("cpuUsage", "nf.node", "unknown")));
    Assertions.assertEquals(
        list(cpuUsage),
        idx.findMatches(id("cpuUsage", "nf.node", "i-00099")));
    Assertions.assertEquals(
        list(diskUsage),
        idx.findMatches(id("diskUsage", "nf.node", "unknown")));
    Assertions.assertEquals(
        list(diskUsage, diskUsagePerNode.get(diskUsagePerNode.size() - 1)),
        idx.findMatches(id("diskUsage", "nf.node", "i-00099")));

    // Shouldn't match
    Assertions.assertTrue(
        idx.findMatches(id("memoryUsage", "nf.node", "i-00099")).isEmpty());
  }

  @Test
  public void multipleClausesForSameKey() {
    Query q = Parser.parseQuery("name,abc.*,:re,name,.*def,:re,:and");
    QueryIndex<Query> idx = QueryIndex.<Query>newInstance(registry).add(q, q);

    // Doesn't match prefix check
    Assertions.assertTrue(idx.findMatches(id("foodef")).isEmpty());

    // Doesn't match suffix check
    Assertions.assertTrue(idx.findMatches(id("abcbar")).isEmpty());

    // Matches both
    Assertions.assertFalse(idx.findMatches(id("abcdef")).isEmpty());
    Assertions.assertFalse(idx.findMatches(id("abc def")).isEmpty());
  }

  @Test
  public void notEqClause() {
    Query q = Parser.parseQuery("name,cpu,:eq,id,user,:eq,:not,:and");
    QueryIndex<Query> idx = QueryIndex.<Query>newInstance(registry).add(q, q);

    Assertions.assertFalse(idx.findMatches(id("cpu", "id", "system")).isEmpty());
    Assertions.assertTrue(idx.findMatches(id("cpu", "id", "user")).isEmpty());
  }

  @Test
  public void notEqMissingKey() {
    Query q = Parser.parseQuery("name,cpu,:eq,id,user,:eq,:not,:and");
    QueryIndex<Query> idx = QueryIndex.<Query>newInstance(registry).add(q, q);
    Assertions.assertFalse(idx.findMatches(id("cpu")).isEmpty());
  }

  @Test
  public void notEqMissingKeyMiddle() {
    Query q = Parser.parseQuery("name,cpu,:eq,mm,foo,:eq,:not,:and,zz,bar,:eq,:and");
    QueryIndex<Query> idx = QueryIndex.<Query>newInstance(registry).add(q, q);

    Assertions.assertFalse(idx.findMatches(id("cpu", "zz", "bar")).isEmpty());
  }

  @Test
  public void notEqMissingKeyEnd() {
    Query q = Parser.parseQuery("name,cpu,:eq,zz,foo,:eq,:not,:and");
    QueryIndex<Query> idx = QueryIndex.<Query>newInstance(registry).add(q, q);

    Assertions.assertFalse(idx.findMatches(id("cpu")).isEmpty());
  }

  @Test
  public void multiNotEqClause() {
    Query q = Parser.parseQuery("name,cpu,:eq,id,system,:eq,:and,id,user,:eq,:not,:and");
    QueryIndex<Query> idx = QueryIndex.<Query>newInstance(registry).add(q, q);

    Assertions.assertFalse(idx.findMatches(id("cpu", "id", "system")).isEmpty());
    Assertions.assertTrue(idx.findMatches(id("cpu", "id", "user")).isEmpty());
  }

  @Test
  public void notInClause() {
    Query q = Parser.parseQuery("name,cpu,:eq,id,(,user,iowait,),:in,:not,:and");
    QueryIndex<Query> idx = QueryIndex.<Query>newInstance(registry).add(q, q);

    Assertions.assertFalse(idx.findMatches(id("cpu", "id", "system")).isEmpty());
    Assertions.assertTrue(idx.findMatches(id("cpu", "id", "user")).isEmpty());
    Assertions.assertTrue(idx.findMatches(id("cpu", "id", "iowait")).isEmpty());
  }

  @Test
  public void multiNotInClause() {
    Query q = Parser.parseQuery("name,cpu,:eq,id,system,:eq,:and,id,(,user,iowait,),:in,:not,:and");
    QueryIndex<Query> idx = QueryIndex.<Query>newInstance(registry).add(q, q);

    Assertions.assertFalse(idx.findMatches(id("cpu", "id", "system")).isEmpty());
    Assertions.assertTrue(idx.findMatches(id("cpu", "id", "user")).isEmpty());
    Assertions.assertTrue(idx.findMatches(id("cpu", "id", "iowait")).isEmpty());
  }

  @Test
  public void doubleNotsSameKey() {
    Query q = Parser.parseQuery("a,1,:eq,b,2,:eq,:and,c,3,:eq,:not,:and,c,4,:eq,:not,:and");
    QueryIndex<Query> idx = QueryIndex.<Query>newInstance(registry).add(q, q);
    Assertions.assertFalse(idx.findMatches(id("cpu", "a", "1", "b", "2", "c", "5")).isEmpty());
    Assertions.assertTrue(idx.findMatches(id("cpu", "a", "1", "b", "2", "c", "3")).isEmpty());
    Assertions.assertTrue(idx.findMatches(id("cpu", "a", "1", "b", "2", "c", "4")).isEmpty());
    Assertions.assertFalse(idx.findMatches(id("cpu", "a", "1", "b", "2")).isEmpty());
  }

  @Test
  public void removalOfNotQuery() {
    Query q = Parser.parseQuery("name,cpu,:eq,id,user,:eq,:not,:and");
    QueryIndex<Query> idx = QueryIndex.<Query>newInstance(registry).add(q, q);
    Assertions.assertTrue(idx.remove(q, q));
    Assertions.assertTrue(idx.isEmpty());
  }

  @Test
  public void removalOfNotQueryUsingQuery() {
    Query q = Parser.parseQuery("name,cpu,:eq,id,user,:eq,:not,:and");
    QueryIndex<Query> idx = QueryIndex.<Query>newInstance(registry).add(q, q);
    Assertions.assertTrue(remove(idx, q));
    Assertions.assertTrue(idx.isEmpty());
  }

  @Test
  public void removalPrefixRegexSubtree() {
    Query q1 = Parser.parseQuery("name,test,:eq,a,foo,:re,:and,b,bar,:eq,:and");
    Query q2 = Parser.parseQuery("name,test,:eq,a,foo,:re,:and");
    QueryIndex<Query> idx = QueryIndex.<Query>newInstance(registry)
        .add(q1, q1)
        .add(q2, q2);

    Id id = id("test", "a", "foo", "b", "bar");

    Set<Query> expected = new HashSet<>();
    expected.add(q1);
    expected.add(q2);
    Assertions.assertEquals(expected, new HashSet<>(idx.findMatches(id)));

    idx.remove(q1, q1);
    expected.remove(q1);
    Assertions.assertEquals(expected, new HashSet<>(idx.findMatches(id)));
  }

  @Test
  public void toStringMethod() {
    QueryIndex<Query> idx = QueryIndex.newInstance(registry);
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
}