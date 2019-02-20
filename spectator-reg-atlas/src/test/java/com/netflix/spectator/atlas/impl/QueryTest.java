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

import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Registry;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


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
}
