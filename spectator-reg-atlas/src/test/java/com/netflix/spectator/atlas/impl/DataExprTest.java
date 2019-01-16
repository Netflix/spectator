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
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Tag;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;


public class DataExprTest {

  private final Registry registry = new DefaultRegistry();

  private DataExpr parse(String expr) {
    DataExpr de = Parser.parseDataExpr(expr);
    Assertions.assertEquals(expr, de.toString());
    return de;
  }

  private List<TagsValuePair> data(String name, double... vs) {
    List<Measurement> ms = new ArrayList<>();
    for (int i = 0; i < vs.length; ++i) {
      String pos = String.format("%03d", i);
      String value = String.format("%f", vs[i]);
      ms.add(new Measurement(registry.createId(name, "i", pos, "v", value), 0L, vs[i]));
    }
    return ms.stream().map(this::newTagsValuePair).collect(Collectors.toList());
  }

  private TagsValuePair newTagsValuePair(Measurement m) {
    Map<String, String> tags = new HashMap<>();
    for (Tag t : m.id().tags()) {
      tags.put(t.key(), t.value());
    }
    tags.put("name", m.id().name());
    return new TagsValuePair(tags, m.value());
  }

  private Iterable<TagsValuePair> evalNoCheck(DataExpr expr, Iterable<TagsValuePair> input) {
    DataExpr.Aggregator aggr = expr.aggregator(expr.query().exactTags(), false);
    for (TagsValuePair p : input) {
      aggr.update(p);
    }
    return aggr.result();
  }

  @Test
  public void sumEmpty() {
    DataExpr expr = parse(":true,:sum");
    Assertions.assertFalse(expr.eval(Collections.emptyList()).iterator().hasNext());
  }

  @Test
  public void minEmpty() {
    DataExpr expr = parse(":true,:min");
    Assertions.assertFalse(expr.eval(Collections.emptyList()).iterator().hasNext());
  }

  @Test
  public void maxEmpty() {
    DataExpr expr = parse(":true,:max");
    Assertions.assertFalse(expr.eval(Collections.emptyList()).iterator().hasNext());
  }

  @Test
  public void countEmpty() {
    DataExpr expr = parse(":true,:count");
    Assertions.assertFalse(expr.eval(Collections.emptyList()).iterator().hasNext());
  }

  private void aggrData(String aggr, double expected, boolean shouldCheckQuery) {
    DataExpr expr = parse("name,foo,:eq," + aggr);
    List<TagsValuePair> ms = data("foo", 1.0, 2.0, 3.0, 1.0);
    ms.addAll(data("bar", 42.0));

    Map<String, String> expectedTags = new HashMap<>();
    expectedTags.put("name", "foo");

    Iterable<TagsValuePair> vs = shouldCheckQuery ? expr.eval(ms) : evalNoCheck(expr, ms);
    int count = 0;
    for (TagsValuePair v : vs) {
      ++count;
      Assertions.assertEquals(expectedTags, v.tags());
      Assertions.assertEquals(expected, v.value(), 1e-12);
    }
    Assertions.assertEquals(1, count);
  }

  private void aggrData(String aggr, double expected) {
    aggrData(aggr, expected, true);
  }

  @Test
  public void sumData() {
    aggrData(":sum", 7.0);
  }

  @Test
  public void sumDataNoCheck() {
    aggrData(":sum", 49.0, false);
  }

  @Test
  public void minData() {
    aggrData(":min", 1.0);
  }

  @Test
  public void minDataNoCheck() {
    aggrData(":min", 1.0, false);
  }

  @Test
  public void maxData() {
    aggrData(":max", 3.0);
  }

  @Test
  public void maxDataNoCheck() {
    aggrData(":max", 42.0, false);
  }

  @Test
  public void countData() {
    aggrData(":count", 4.0);
  }

  @Test
  public void countDataNoCheck() {
    aggrData(":count", 5.0, false);
  }

  @Test
  public void groupByNameData() {
    aggrData(":sum,(,name,),:by", 7.0);
  }

  @Test
  public void groupByNameDataNoCheck() {
    // Note, this test shows a problem if the query is not checked properly when
    // using shouldCheckQuery = false. There are two names in the group by, but
    // only one shows up because name is restricted in the query and that overrides
    // the value from the group by. If the query had been checked, then the mismatched
    // names would not be possible.
    aggrData(":sum,(,name,),:by", 49.0, false);
  }

  private void groupingData(String aggr) {
    groupingData(aggr, true);
  }

  private void groupingData(String aggr, boolean shouldCheckQuery) {
    DataExpr expr = parse("name,foo,:eq,:sum," + aggr);
    List<TagsValuePair> ms = data("foo", 1.0, 2.0, 3.0, 1.0);
    ms.addAll(data("bar", 42.0));

    Iterable<TagsValuePair> vs = shouldCheckQuery ? expr.eval(ms) : evalNoCheck(expr, ms);
    int count = 0;
    for (TagsValuePair v : vs) {
      ++count;
      Assertions.assertEquals(2, v.tags().size());
      if (shouldCheckQuery) {
        Assertions.assertEquals("foo", v.tags().get("name"));
      }
      double tv = Double.parseDouble(v.tags().get("v"));
      Assertions.assertEquals((tv < 2.0) ? 2.0 : tv, v.value(), 1e-12);
    }
    Assertions.assertEquals(shouldCheckQuery ? 3 : 4, count);
  }

  @Test
  public void groupByValueData() {
    groupingData("(,v,),:by");
  }

  @Test
  public void groupByValueDataNoCheck() {
    groupingData("(,v,),:by", false);
  }

  @Test
  public void groupByUnknownData() {
    DataExpr expr = parse("name,foo,:eq,:sum,(,a,v,),:by");
    List<TagsValuePair> ms = data("foo", 1.0, 2.0, 3.0, 1.0);
    ms.addAll(data("bar", 42.0));

    Iterable<TagsValuePair> vs = expr.eval(ms);
    Assertions.assertFalse(vs.iterator().hasNext());
  }

  @Test
  public void rollupKeepData() {
    groupingData("(,v,name,),:rollup-keep");
  }

  @Test
  public void rollupKeepDataNoCheck() {
    groupingData("(,v,name,),:rollup-keep", false);
  }

  @Test
  public void rollupKeepUnknownData() {
    groupingData("(,a,v,name,),:rollup-keep");
  }

  @Test
  public void rollupDropData() {
    groupingData("(,i,),:rollup-drop");
  }

  @Test
  public void rollupDropDataNoCheck() {
    groupingData("(,i,),:rollup-drop", false);
  }

  @Test
  public void allData() {
    DataExpr expr = parse("name,foo,:eq,:all");
    List<TagsValuePair> ms = data("foo", 1.0, 2.0, 3.0, 1.0);
    ms.addAll(data("bar", 42.0));

    Iterable<TagsValuePair> vs = expr.eval(ms);
    Assertions.assertEquals(4, StreamSupport.stream(vs.spliterator(), false).count());
  }

  @Test
  public void allDataNoCheck() {
    DataExpr expr = parse("name,foo,:eq,:all");
    List<TagsValuePair> ms = data("foo", 1.0, 2.0, 3.0, 1.0);
    ms.addAll(data("bar", 42.0));

    Iterable<TagsValuePair> vs = evalNoCheck(expr, ms);
    Assertions.assertEquals(5, StreamSupport.stream(vs.spliterator(), false).count());
  }

  @Test
  public void notData() {
    DataExpr expr = parse("name,foo,:eq,:not,:all");
    List<TagsValuePair> ms = data("foo", 1.0, 2.0, 3.0, 1.0);
    ms.addAll(data("bar", 42.0));

    Iterable<TagsValuePair> vs = expr.eval(ms);
    Assertions.assertEquals(1, StreamSupport.stream(vs.spliterator(), false).count());
  }

  @Test
  public void inWithGroupBy() {
    // https://github.com/Netflix/spectator/issues/391
    parse("statistic,(,totalAmount,totalTime,),:in,name,jvm.gc.pause,:eq,:and,:sum,(,nf.asg,nf.node,),:by");
  }

  @Test
  public void nestedInClauses() {
    Set<String> values = new TreeSet<>();
    values.add("key");
    values.add("(");
    values.add("a");
    values.add("b");
    values.add(")");
    values.add(":in");
    DataExpr expected = new DataExpr.Sum(new Query.In("key", values));
    DataExpr actual = Parser.parseDataExpr("key,(,key,(,a,b,),:in,),:in,:sum");
    Assertions.assertEquals(expected, actual);
  }

  @Test
  public void multiNestedInClauses() {
    Set<String> values = new TreeSet<>(
        Arrays.asList("key,(,a,(,b,(,c,),),(,),),:in".split(",")));
    DataExpr expected = new DataExpr.Sum(new Query.In("key", values));
    DataExpr actual = Parser.parseDataExpr("key,(,key,(,a,(,b,(,c,),),(,),),:in,),:in,:sum");
    Assertions.assertEquals(expected, actual);
  }

  @Test
  public void mismatchedOpenParen() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> Parser.parseDataExpr("key,(,key,(,),:in,:sum"));
  }

  @Test
  public void mismatchedClosingParen() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> Parser.parseDataExpr("key,(,key,),),:in,:sum"));
  }

  @Test
  public void allEqualsContract() {
    EqualsVerifier
        .forClass(DataExpr.All.class)
        .suppress(Warning.NULL_FIELDS)
        .verify();
  }

  @Test
  public void sumEqualsContract() {
    EqualsVerifier
        .forClass(DataExpr.Sum.class)
        .suppress(Warning.NULL_FIELDS)
        .verify();
  }

  @Test
  public void minEqualsContract() {
    EqualsVerifier
        .forClass(DataExpr.Min.class)
        .suppress(Warning.NULL_FIELDS)
        .verify();
  }

  @Test
  public void maxEqualsContract() {
    EqualsVerifier
        .forClass(DataExpr.Max.class)
        .suppress(Warning.NULL_FIELDS)
        .verify();
  }

  @Test
  public void countEqualsContract() {
    EqualsVerifier
        .forClass(DataExpr.Count.class)
        .suppress(Warning.NULL_FIELDS)
        .verify();
  }

  @Test
  public void byEqualsContract() {
    EqualsVerifier
        .forClass(DataExpr.GroupBy.class)
        .suppress(Warning.NULL_FIELDS)
        .verify();
  }

  @Test
  public void dropEqualsContract() {
    EqualsVerifier
        .forClass(DataExpr.DropRollup.class)
        .suppress(Warning.NULL_FIELDS)
        .verify();
  }

  @Test
  public void keepEqualsContract() {
    EqualsVerifier
        .forClass(DataExpr.KeepRollup.class)
        .suppress(Warning.NULL_FIELDS)
        .verify();
  }
}
