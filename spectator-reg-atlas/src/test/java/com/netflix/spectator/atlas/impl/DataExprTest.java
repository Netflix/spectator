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
package com.netflix.spectator.atlas.impl;

import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Tag;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;


@RunWith(JUnit4.class)
public class DataExprTest {

  private final Registry registry = new DefaultRegistry();

  private DataExpr parse(String expr) {
    DataExpr de = Parser.parseDataExpr(expr);
    Assert.assertEquals(expr, de.toString());
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

  @Test
  public void sumEmpty() {
    DataExpr expr = parse(":true,:sum");
    Assert.assertFalse(expr.eval(Collections.emptyList()).iterator().hasNext());
  }

  @Test
  public void minEmpty() {
    DataExpr expr = parse(":true,:min");
    Assert.assertFalse(expr.eval(Collections.emptyList()).iterator().hasNext());
  }

  @Test
  public void maxEmpty() {
    DataExpr expr = parse(":true,:max");
    Assert.assertFalse(expr.eval(Collections.emptyList()).iterator().hasNext());
  }

  @Test
  public void countEmpty() {
    DataExpr expr = parse(":true,:count");
    Assert.assertFalse(expr.eval(Collections.emptyList()).iterator().hasNext());
  }

  private void aggrData(String aggr, double expected) {
    DataExpr expr = parse("name,foo,:eq," + aggr);
    List<TagsValuePair> ms = data("foo", 1.0, 2.0, 3.0, 1.0);
    ms.addAll(data("bar", 42.0));

    Map<String, String> expectedTags = new HashMap<>();
    expectedTags.put("name", "foo");

    Iterable<TagsValuePair> vs = expr.eval(ms);
    int count = 0;
    for (TagsValuePair v : vs) {
      ++count;
      Assert.assertEquals(expectedTags, v.tags());
      Assert.assertEquals(expected, v.value(), 1e-12);
    }
    Assert.assertEquals(1, count);
  }

  @Test
  public void sumData() {
    aggrData(":sum", 7.0);
  }

  @Test
  public void minData() {
    aggrData(":min", 1.0);
  }

  @Test
  public void maxData() {
    aggrData(":max", 3.0);
  }

  @Test
  public void countData() {
    aggrData(":count", 4.0);
  }

  @Test
  public void groupByNameData() {
    aggrData(":sum,(,name,),:by", 7.0);
  }

  private void groupingData(String aggr) {
    DataExpr expr = parse("name,foo,:eq,:sum," + aggr);
    List<TagsValuePair> ms = data("foo", 1.0, 2.0, 3.0, 1.0);
    ms.addAll(data("bar", 42.0));

    Iterable<TagsValuePair> vs = expr.eval(ms);
    int count = 0;
    for (TagsValuePair v : vs) {
      ++count;
      Assert.assertEquals(2, v.tags().size());
      Assert.assertEquals("foo", v.tags().get("name"));
      double tv = Double.parseDouble(v.tags().get("v"));
      Assert.assertEquals((tv < 2.0) ? 2.0 : tv, v.value(), 1e-12);
    }
    Assert.assertEquals(3, count);
  }

  @Test
  public void groupByValueData() {
    groupingData("(,v,),:by");
  }

  @Test
  public void groupByUnknownData() {
    DataExpr expr = parse("name,foo,:eq,:sum,(,a,v,),:by");
    List<TagsValuePair> ms = data("foo", 1.0, 2.0, 3.0, 1.0);
    ms.addAll(data("bar", 42.0));

    Iterable<TagsValuePair> vs = expr.eval(ms);
    Assert.assertFalse(vs.iterator().hasNext());
  }

  @Test
  public void rollupKeepData() {
    groupingData("(,v,name,),:rollup-keep");
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
  public void allData() {
    DataExpr expr = parse("name,foo,:eq,:all");
    List<TagsValuePair> ms = data("foo", 1.0, 2.0, 3.0, 1.0);
    ms.addAll(data("bar", 42.0));

    Iterable<TagsValuePair> vs = expr.eval(ms);
    Assert.assertEquals(4, StreamSupport.stream(vs.spliterator(), false).count());
  }

  @Test
  public void notData() {
    DataExpr expr = parse("name,foo,:eq,:not,:all");
    List<TagsValuePair> ms = data("foo", 1.0, 2.0, 3.0, 1.0);
    ms.addAll(data("bar", 42.0));

    Iterable<TagsValuePair> vs = expr.eval(ms);
    Assert.assertEquals(1, StreamSupport.stream(vs.spliterator(), false).count());
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
