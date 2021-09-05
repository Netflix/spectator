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
package com.netflix.spectator.atlas.impl;

import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Registry;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;


public class QueryTest {

  private final Registry registry = new DefaultRegistry();

  private Query parse(String expr) {
    Query q1 = Parser.parseQuery(expr);
    Query q2 = Parser.parseQuery(expr);
    Assertions.assertEquals(q1, q2);
    Assertions.assertEquals(expr, q1.toString());
    return q1;
  }

  @Test
  public void trueQuery() {
    Query q = parse(":true");
    Assertions.assertTrue(q.matches(registry.createId("foo")));
  }

  @Test
  public void falseQuery() {
    Query q = parse(":false");
    Assertions.assertFalse(q.matches(registry.createId("foo")));
  }

  @Test
  public void eqQuery() {
    Query q = parse("name,foo,:eq");
    Assertions.assertTrue(q.matches(registry.createId("foo")));
    Assertions.assertFalse(q.matches(registry.createId("bar")));
  }

  @Test
  public void eqEqualsContract() {
    EqualsVerifier
        .forClass(Query.Equal.class)
        .suppress(Warning.NULL_FIELDS)
        .verify();
  }

  @Test
  public void hasQuery() {
    Query q = parse("bar,:has");
    Assertions.assertFalse(q.matches(registry.createId("bar")));
    Assertions.assertTrue(q.matches(registry.createId("foo", "bar", "baz")));
    Assertions.assertFalse(q.matches(registry.createId("foo", "baz", "baz")));
  }

  @Test
  public void hasEqualsContract() {
    EqualsVerifier
        .forClass(Query.Has.class)
        .suppress(Warning.NULL_FIELDS)
        .verify();
  }

  @Test
  public void inQueryEmpty() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> parse("name,(,),:in"));
  }

  @Test
  public void inQuery() {
    Query q = parse("name,(,bar,foo,),:in");
    Assertions.assertTrue(q.matches(registry.createId("foo")));
    Assertions.assertTrue(q.matches(registry.createId("bar")));
    Assertions.assertFalse(q.matches(registry.createId("baz")));
  }

  @Test
  public void inEqualsContract() {
    EqualsVerifier
        .forClass(Query.In.class)
        .suppress(Warning.NULL_FIELDS)
        .verify();
  }

  @Test
  public void ltQuery() {
    Query q = parse("name,foo,:lt");
    Assertions.assertFalse(q.matches(registry.createId("foo")));
    Assertions.assertTrue(q.matches(registry.createId("faa")));
    Assertions.assertFalse(q.matches(registry.createId("fzz")));
  }

  @Test
  public void ltEqualsContract() {
    EqualsVerifier
        .forClass(Query.LessThan.class)
        .suppress(Warning.NULL_FIELDS)
        .verify();
  }

  @Test
  public void leQuery() {
    Query q = parse("name,foo,:le");
    Assertions.assertTrue(q.matches(registry.createId("foo")));
    Assertions.assertTrue(q.matches(registry.createId("faa")));
    Assertions.assertFalse(q.matches(registry.createId("fzz")));
  }

  @Test
  public void leEqualsContract() {
    EqualsVerifier
        .forClass(Query.LessThanEqual.class)
        .suppress(Warning.NULL_FIELDS)
        .verify();
  }

  @Test
  public void gtQuery() {
    Query q = parse("name,foo,:gt");
    Assertions.assertFalse(q.matches(registry.createId("foo")));
    Assertions.assertFalse(q.matches(registry.createId("faa")));
    Assertions.assertTrue(q.matches(registry.createId("fzz")));
  }

  @Test
  public void gtEqualsContract() {
    EqualsVerifier
        .forClass(Query.GreaterThan.class)
        .suppress(Warning.NULL_FIELDS)
        .verify();
  }

  @Test
  public void geQuery() {
    Query q = parse("name,foo,:ge");
    Assertions.assertTrue(q.matches(registry.createId("foo")));
    Assertions.assertFalse(q.matches(registry.createId("faa")));
    Assertions.assertTrue(q.matches(registry.createId("fzz")));
  }

  @Test
  public void geEqualsContract() {
    EqualsVerifier
        .forClass(Query.GreaterThanEqual.class)
        .suppress(Warning.NULL_FIELDS)
        .verify();
  }

  @Test
  public void reQuery() {
    Query q = parse("name,foo,:re");
    Assertions.assertTrue(q.matches(registry.createId("foo")));
    Assertions.assertFalse(q.matches(registry.createId("abcfoo")));
    Assertions.assertTrue(q.matches(registry.createId("foobar")));
  }

  @Test
  public void reQueryAlwaysMatches() {
    Query q = Parser.parseQuery("name,.*,:re");
    Assertions.assertEquals(Parser.parseQuery("name,:has"), q);
  }

  @Test
  public void reicQuery() {
    Query q = parse("name,foO,:reic");
    Assertions.assertTrue(q.matches(registry.createId("fOo")));
    Assertions.assertFalse(q.matches(registry.createId("abcFoo")));
    Assertions.assertTrue(q.matches(registry.createId("Foobar")));
  }

  @Test
  public void reEqualsContract() {
    EqualsVerifier
        .forClass(Query.Regex.class)
        .suppress(Warning.NULL_FIELDS, Warning.ALL_FIELDS_SHOULD_BE_USED)
        .verify();
  }

  @Test
  public void andQuery() {
    Query q = parse("name,foo,:eq,bar,baz,:eq,:and");
    Assertions.assertFalse(q.matches(registry.createId("foo")));
    Assertions.assertFalse(q.matches(registry.createId("bar")));
    Assertions.assertTrue(q.matches(registry.createId("foo", "bar", "baz")));
    Assertions.assertFalse(q.matches(registry.createId("bar", "bar", "baz")));
    Assertions.assertFalse(q.matches(registry.createId("foo", "bar", "def")));
    Assertions.assertFalse(q.matches(registry.createId("foo", "abc", "def")));
  }

  @Test
  public void andEqualsContract() {
    EqualsVerifier
        .forClass(Query.And.class)
        .suppress(Warning.NULL_FIELDS)
        .verify();
  }

  @Test
  public void orQuery() {
    Query q = parse("name,foo,:eq,bar,baz,:eq,:or");
    Assertions.assertTrue(q.matches(registry.createId("foo")));
    Assertions.assertFalse(q.matches(registry.createId("bar")));
    Assertions.assertTrue(q.matches(registry.createId("foo", "bar", "baz")));
    Assertions.assertTrue(q.matches(registry.createId("bar", "bar", "baz")));
    Assertions.assertTrue(q.matches(registry.createId("foo", "bar", "def")));
    Assertions.assertTrue(q.matches(registry.createId("foo", "abc", "def")));
  }

  @Test
  public void orEqualsContract() {
    EqualsVerifier
        .forClass(Query.Or.class)
        .suppress(Warning.NULL_FIELDS)
        .verify();
  }

  @Test
  public void notQuery() {
    Query q = parse("name,foo,:eq,:not");
    Assertions.assertFalse(q.matches(registry.createId("foo")));
    Assertions.assertTrue(q.matches(registry.createId("bar")));
  }

  @Test
  public void notEqualsContract() {
    EqualsVerifier
        .forClass(Query.Not.class)
        .suppress(Warning.NULL_FIELDS)
        .verify();
  }

  @Test
  public void andOptimizationTrue() {
    Query q1 = new Query.Has("a");
    Query q2 = new Query.Has("b");
    Assertions.assertEquals(new Query.And(q1, q2), q1.and(q2));
    Assertions.assertEquals(q2, Query.TRUE.and(q2));
    Assertions.assertEquals(q1, q1.and(Query.TRUE));
  }

  @Test
  public void andOptimizationFalse() {
    Query q1 = new Query.Has("a");
    Query q2 = new Query.Has("b");
    Assertions.assertEquals(new Query.And(q1, q2), q1.and(q2));
    Assertions.assertEquals(Query.FALSE, Query.FALSE.and(q2));
    Assertions.assertEquals(Query.FALSE, q1.and(Query.FALSE));
  }

  @Test
  public void orOptimizationTrue() {
    Query q1 = new Query.Has("a");
    Query q2 = new Query.Has("b");
    Assertions.assertEquals(new Query.Or(q1, q2), q1.or(q2));
    Assertions.assertEquals(Query.TRUE, Query.TRUE.or(q2));
    Assertions.assertEquals(Query.TRUE, q1.or(Query.TRUE));
  }

  @Test
  public void orOptimizationFalse() {
    Query q1 = new Query.Has("a");
    Query q2 = new Query.Has("b");
    Assertions.assertEquals(new Query.Or(q1, q2), q1.or(q2));
    Assertions.assertEquals(q2, Query.FALSE.or(q2));
    Assertions.assertEquals(q1, q1.or(Query.FALSE));
  }

  @Test
  public void notOptimizationTrue() {
    Assertions.assertEquals(Query.FALSE, Query.TRUE.not());
  }

  @Test
  public void notOptimizationFalse() {
    Assertions.assertEquals(Query.TRUE, Query.FALSE.not());
  }

  @Test
  public void notOptimizationAnd() {
    Query q1 = new Query.Has("a");
    Query q2 = new Query.Has("b");
    Query expected = new Query.Or(q1.not(), q2.not());
    Assertions.assertEquals(expected, new Query.And(q1, q2).not());
  }

  @Test
  public void notOptimizationOr() {
    Query q1 = new Query.Has("a");
    Query q2 = new Query.Has("b");
    Query expected = new Query.And(q1.not(), q2.not());
    Assertions.assertEquals(expected, new Query.Or(q1, q2).not());
  }

  @Test
  public void notOptimizationNot() {
    Query.KeyQuery q = new Query.Has("a");
    Assertions.assertEquals(q, new Query.Not(q).not());
    Assertions.assertEquals(q, new Query.InvertedKeyQuery(q).not());
    Assertions.assertEquals(q, q.not().not());
    Assertions.assertTrue(q.not() instanceof Query.InvertedKeyQuery);
  }

  @Test
  public void parseOptimizationAnd() {
    Assertions.assertEquals(Query.TRUE, Parser.parseQuery(":true,:true,:and"));
    Assertions.assertEquals(Query.FALSE, Parser.parseQuery(":true,:false,:and"));
    Assertions.assertEquals(Query.FALSE, Parser.parseQuery(":false,:true,:and"));
    Assertions.assertEquals(Query.FALSE, Parser.parseQuery(":false,:false,:and"));
  }

  @Test
  public void parseOptimizationOr() {
    Assertions.assertEquals(Query.TRUE, Parser.parseQuery(":true,:true,:or"));
    Assertions.assertEquals(Query.TRUE, Parser.parseQuery(":true,:false,:or"));
    Assertions.assertEquals(Query.TRUE, Parser.parseQuery(":false,:true,:or"));
    Assertions.assertEquals(Query.FALSE, Parser.parseQuery(":false,:false,:or"));
  }

  @Test
  public void parseOptimizationNot() {
    Assertions.assertEquals(Query.TRUE, Parser.parseQuery(":true,:not,:not"));
    Assertions.assertEquals(Query.FALSE, Parser.parseQuery(":false,:not,:not"));
  }

  @Test
  public void dnfListA() {
    Query a = new Query.Has("a");
    Assertions.assertEquals(Collections.singletonList(a), a.dnfList());
  }

  @Test
  public void dnfListAnd() {
    Query a = new Query.Has("a");
    Query b = new Query.Has("b");
    Query q = a.and(b);
    Assertions.assertEquals(Collections.singletonList(q), q.dnfList());
  }

  private List<Query> qs(Query... queries) {
    return Arrays.asList(queries);
  }

  @Test
  public void dnfListOrAnd() {
    Query a = new Query.Has("a");
    Query b = new Query.Has("b");
    Query c = new Query.Has("c");
    Query q = a.or(b).and(c);
    Assertions.assertEquals(qs(a.and(c), b.and(c)), q.dnfList());
  }

  @Test
  public void dnfListOrOrAnd() {
    Query a = new Query.Has("a");
    Query b = new Query.Has("b");
    Query c = new Query.Has("c");
    Query d = new Query.Has("d");
    Query q = a.or(b).and(c.or(d));
    List<Query> expected = qs(
        a.and(c),
        a.and(d),
        b.and(c),
        b.and(d)
    );
    Assertions.assertEquals(expected, q.dnfList());
  }

  @Test
  public void dnfListNotOr() {
    Query a = new Query.Has("a");
    Query b = new Query.Has("b");
    Query q = new Query.Not(a.or(b));
    Assertions.assertEquals(qs(a.not().and(b.not())), q.dnfList());
  }

  @Test
  public void dnfListNotAnd() {
    Query a = new Query.Has("a");
    Query b = new Query.Has("b");
    Query q = new Query.Not(a.and(b));
    Assertions.assertEquals(qs(a.not(), b.not()), q.dnfList());
  }

  @Test
  public void dnfListNotSimple() {
    Query a = new Query.Has("a");
    Query q = new Query.Not(a);
    Assertions.assertEquals(qs(q), q.dnfList());
  }

  @Test
  public void dnfListSimplifiesToKeyQueries() {
    Random r = new Random(42);
    for (int i = 0; i < 1000; ++i) {
      Query query = DataGenerator.randomQuery(r, 5);
      for (Query dnfQ : query.dnfList()) {
        for (Query q : dnfQ.andList()) {
          Assertions.assertTrue(
              q instanceof Query.KeyQuery || q == Query.TRUE || q == Query.FALSE,
              "[" + q + "] is not a KeyQuery, extracted from [" + query + "]"
          );
        }
      }
    }
  }

  private Map<String, String> tags(String... vs) {
    Map<String, String> tmp = new LinkedHashMap<>();
    for (int i = 0; i < vs.length; i += 2) {
      tmp.put(vs[i], vs[i + 1]);
    }
    return tmp;
  }

  @Test
  public void simplifyEqualsMatch() {
    Query q = Parser.parseQuery("nf.cluster,foo,:eq");
    Assertions.assertEquals(Query.TRUE, q.simplify(tags("nf.cluster", "foo")));
  }

  @Test
  public void simplifyEqualsNoMatch() {
    Query q = Parser.parseQuery("nf.cluster,foo,:eq");
    Assertions.assertEquals(Query.FALSE, q.simplify(tags("nf.cluster", "bar")));
  }

  @Test
  public void simplifyEqualsNoValueForKey() {
    Query q = Parser.parseQuery("nf.cluster,foo,:eq");
    Assertions.assertSame(q, q.simplify(tags("nf.app", "foo")));
  }

  @Test
  public void simplifyAndMatchLeft() {
    Query q = Parser.parseQuery("nf.cluster,foo,:eq,name,cpu,:eq,:and");
    Query expected = Parser.parseQuery("name,cpu,:eq");
    Assertions.assertEquals(expected, q.simplify(tags("nf.cluster", "foo")));
  }

  @Test
  public void simplifyAndMatchRight() {
    Query q = Parser.parseQuery("name,cpu,:eq,nf.cluster,foo,:eq,:and");
    Query expected = Parser.parseQuery("name,cpu,:eq");
    Assertions.assertEquals(expected, q.simplify(tags("nf.cluster", "foo")));
  }

  @Test
  public void simplifyAndNoMatch() {
    Query q = Parser.parseQuery("nf.cluster,foo,:eq,name,cpu,:eq,:and");
    Assertions.assertSame(Query.FALSE, q.simplify(tags("nf.cluster", "bar")));
  }

  @Test
  public void simplifyAndNoValueForKey() {
    Query q = Parser.parseQuery("nf.cluster,foo,:eq,name,cpu,:eq,:and");
    Assertions.assertSame(q, q.simplify(tags("nf.app", "foo")));
  }

  @Test
  public void simplifyOrMatchLeft() {
    Query q = Parser.parseQuery("nf.cluster,foo,:eq,name,cpu,:eq,:or");
    Assertions.assertEquals(Query.TRUE, q.simplify(tags("nf.cluster", "foo")));
  }

  @Test
  public void simplifyOrMatchRight() {
    Query q = Parser.parseQuery("name,cpu,:eq,nf.cluster,foo,:eq,:or");
    Assertions.assertEquals(Query.TRUE, q.simplify(tags("nf.cluster", "foo")));
  }

  @Test
  public void simplifyOrNoMatch() {
    Query q = Parser.parseQuery("nf.cluster,foo,:eq,name,cpu,:eq,:or");
    Query expected = Parser.parseQuery("name,cpu,:eq");
    Assertions.assertEquals(expected, q.simplify(tags("nf.cluster", "bar")));
  }

  @Test
  public void simplifyOrNoValueForKey() {
    Query q = Parser.parseQuery("nf.cluster,foo,:eq,name,cpu,:eq,:or");
    Assertions.assertSame(q, q.simplify(tags("nf.app", "foo")));
  }

  @Test
  public void simplifyNotMatch() {
    Query q = Parser.parseQuery("name,cpu,:eq,nf.cluster,foo,:eq,:not,:and");
    Assertions.assertEquals(Query.FALSE, q.simplify(tags("nf.cluster", "foo")));
  }

  @Test
  public void simplifyNotNoMatch() {
    Query q = Parser.parseQuery("name,cpu,:eq,nf.cluster,foo,:eq,:not,:and");
    Query expected = Parser.parseQuery("name,cpu,:eq");
    Assertions.assertEquals(expected, q.simplify(tags("nf.cluster", "bar")));
  }

  @Test
  public void simplifyNotNoValueForKeyMatch() {
    Query q = Parser.parseQuery("name,cpu,:eq,nf.cluster,foo,:eq,:not,:and");
    Assertions.assertSame(q, q.simplify(tags("nf.app", "foo")));
  }

  @Test
  public void simplifyTrue() {
    Query q = Parser.parseQuery(":true");
    Assertions.assertSame(q, q.simplify(tags("nf.cluster", "foo")));
  }

  @Test
  public void simplifyFalse() {
    Query q = Parser.parseQuery(":false");
    Assertions.assertSame(q, q.simplify(tags("nf.cluster", "foo")));
  }
}
