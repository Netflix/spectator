/*
 * Copyright 2014-2016 Netflix, Inc.
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
package com.netflix.spectator.atlas;

import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Registry;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;


@RunWith(JUnit4.class)
public class QueryTest {

  private final Registry registry = new DefaultRegistry();

  private Query parse(String expr) {
    Query q1 = Parser.parseQuery(expr);
    Query q2 = Parser.parseQuery(expr);
    Assert.assertEquals(q1, q2);
    Assert.assertEquals(expr, q1.toString());
    return q1;
  }

  @Test
  public void trueQuery() {
    Query q = parse(":true");
    Assert.assertTrue(q.matches(registry.createId("foo")));
  }

  @Test
  public void falseQuery() {
    Query q = parse(":false");
    Assert.assertFalse(q.matches(registry.createId("foo")));
  }

  @Test
  public void eqQuery() {
    Query q = parse("name,foo,:eq");
    Assert.assertTrue(q.matches(registry.createId("foo")));
    Assert.assertFalse(q.matches(registry.createId("bar")));
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
    Assert.assertFalse(q.matches(registry.createId("bar")));
    Assert.assertTrue(q.matches(registry.createId("foo", "bar", "baz")));
    Assert.assertFalse(q.matches(registry.createId("foo", "baz", "baz")));
  }

  @Test
  public void hasEqualsContract() {
    EqualsVerifier
        .forClass(Query.Has.class)
        .suppress(Warning.NULL_FIELDS)
        .verify();
  }

  @Test(expected = IllegalArgumentException.class)
  public void inQueryEmpty() {
    parse("name,(,),:in");
  }

  @Test
  public void inQuery() {
    Query q = parse("name,(,bar,foo,),:in");
    Assert.assertTrue(q.matches(registry.createId("foo")));
    Assert.assertTrue(q.matches(registry.createId("bar")));
    Assert.assertFalse(q.matches(registry.createId("baz")));
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
    Assert.assertFalse(q.matches(registry.createId("foo")));
    Assert.assertTrue(q.matches(registry.createId("faa")));
    Assert.assertFalse(q.matches(registry.createId("fzz")));
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
    Assert.assertTrue(q.matches(registry.createId("foo")));
    Assert.assertTrue(q.matches(registry.createId("faa")));
    Assert.assertFalse(q.matches(registry.createId("fzz")));
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
    Assert.assertFalse(q.matches(registry.createId("foo")));
    Assert.assertFalse(q.matches(registry.createId("faa")));
    Assert.assertTrue(q.matches(registry.createId("fzz")));
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
    Assert.assertTrue(q.matches(registry.createId("foo")));
    Assert.assertFalse(q.matches(registry.createId("faa")));
    Assert.assertTrue(q.matches(registry.createId("fzz")));
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
    Assert.assertTrue(q.matches(registry.createId("foo")));
    Assert.assertFalse(q.matches(registry.createId("abcfoo")));
    Assert.assertTrue(q.matches(registry.createId("foobar")));
  }

  @Test
  public void reicQuery() {
    Query q = parse("name,foO,:reic");
    Assert.assertTrue(q.matches(registry.createId("fOo")));
    Assert.assertFalse(q.matches(registry.createId("abcFoo")));
    Assert.assertTrue(q.matches(registry.createId("Foobar")));
  }

  @Test
  public void reEqualsContract() {
    EqualsVerifier
        .forClass(Query.Regex.class)
        .suppress(Warning.NULL_FIELDS)
        .verify();
  }

  @Test
  public void andQuery() {
    Query q = parse("name,foo,:eq,bar,baz,:eq,:and");
    Assert.assertFalse(q.matches(registry.createId("foo")));
    Assert.assertFalse(q.matches(registry.createId("bar")));
    Assert.assertTrue(q.matches(registry.createId("foo", "bar", "baz")));
    Assert.assertFalse(q.matches(registry.createId("bar", "bar", "baz")));
    Assert.assertFalse(q.matches(registry.createId("foo", "bar", "def")));
    Assert.assertFalse(q.matches(registry.createId("foo", "abc", "def")));
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
    Assert.assertTrue(q.matches(registry.createId("foo")));
    Assert.assertFalse(q.matches(registry.createId("bar")));
    Assert.assertTrue(q.matches(registry.createId("foo", "bar", "baz")));
    Assert.assertTrue(q.matches(registry.createId("bar", "bar", "baz")));
    Assert.assertTrue(q.matches(registry.createId("foo", "bar", "def")));
    Assert.assertTrue(q.matches(registry.createId("foo", "abc", "def")));
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
    Assert.assertFalse(q.matches(registry.createId("foo")));
    Assert.assertTrue(q.matches(registry.createId("bar")));
  }

  @Test
  public void notEqualsContract() {
    EqualsVerifier
        .forClass(Query.Not.class)
        .suppress(Warning.NULL_FIELDS)
        .verify();
  }
}
