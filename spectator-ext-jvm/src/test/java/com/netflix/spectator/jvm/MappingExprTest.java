/*
 * Copyright 2014-2018 Netflix, Inc.
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
package com.netflix.spectator.jvm;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.HashMap;
import java.util.Map;


@RunWith(JUnit4.class)
public class MappingExprTest {

  @Test
  public void substituteEmpty() {
    Map<String, String> vars = new HashMap<>();
    String actual = MappingExpr.substitute("", vars);
    Assert.assertEquals("", actual);
  }

  @Test
  public void substituteMissing() {
    Map<String, String> vars = new HashMap<>();
    String actual = MappingExpr.substitute("abc{def}", vars);
    Assert.assertEquals("abc{def}", actual);
  }

  @Test
  public void substituteSingle() {
    Map<String, String> vars = new HashMap<>();
    vars.put("def", "123");
    String actual = MappingExpr.substitute("abc{def}", vars);
    Assert.assertEquals("abc123", actual);
  }

  @Test
  public void substituteMultiple() {
    Map<String, String> vars = new HashMap<>();
    vars.put("def", "123");
    String actual = MappingExpr.substitute("abc{def}, {def}", vars);
    Assert.assertEquals("abc123, 123", actual);
  }

  @Test
  public void substituteMultiVar() {
    Map<String, String> vars = new HashMap<>();
    vars.put("def", "123");
    vars.put("ghi", "456");
    String actual = MappingExpr.substitute("abc{def}, {ghi}, {def}", vars);
    Assert.assertEquals("abc123, 456, 123", actual);
  }

  @Test
  public void substituteDecapitalize() {
    Map<String, String> vars = new HashMap<>();
    vars.put("name", "FooBarBaz");
    String actual = MappingExpr.substitute("abc.def.{name}", vars);
    Assert.assertEquals("abc.def.fooBarBaz", actual);
  }

  @Test
  public void substituteRaw() {
    Map<String, String> vars = new HashMap<>();
    vars.put("name", "FooBarBaz");
    String actual = MappingExpr.substitute("abc.def.{raw:name}", vars);
    Assert.assertEquals("abc.def.FooBarBaz", actual);
  }

  @Test
  public void evalMissing() {
    Map<String, Number> vars = new HashMap<>();
    Double v = MappingExpr.eval("{foo}", vars);
    Assert.assertTrue(v.isNaN());
  }

  @Test
  public void evalSimple() {
    Map<String, Number> vars = new HashMap<>();
    vars.put("foo", 0.0);
    Double v = MappingExpr.eval("{foo}", vars);
    Assert.assertEquals(0.0, v, 1e-12);
  }

  @Test
  public void evalConstant() {
    Map<String, Number> vars = new HashMap<>();
    Double v = MappingExpr.eval("42.0", vars);
    Assert.assertEquals(42.0, v, 1e-12);
  }

  @Test
  public void evalAdd() {
    Map<String, Number> vars = new HashMap<>();
    vars.put("foo", 1.0);
    Double v = MappingExpr.eval("42.0,{foo},:add", vars);
    Assert.assertEquals(43.0, v, 1e-12);
  }

  @Test
  public void evalSub() {
    Map<String, Number> vars = new HashMap<>();
    vars.put("foo", 1.0);
    Double v = MappingExpr.eval("42.0,{foo},:sub", vars);
    Assert.assertEquals(41.0, v, 1e-12);
  }

  @Test
  public void evalMul() {
    Map<String, Number> vars = new HashMap<>();
    vars.put("foo", 2.0);
    Double v = MappingExpr.eval("42.0,{foo},:mul", vars);
    Assert.assertEquals(84.0, v, 1e-12);
  }

  @Test
  public void evalDiv() {
    Map<String, Number> vars = new HashMap<>();
    vars.put("foo", 2.0);
    Double v = MappingExpr.eval("42.0,{foo},:div", vars);
    Assert.assertEquals(21.0, v, 1e-12);
  }

  @Test
  public void evalIfChangedYes() {
    Map<String, Number> vars = new HashMap<>();
    vars.put("foo", 2.0);
    vars.put("previous:foo", 3.0);
    Double v = MappingExpr.eval("42.0,{foo},{previous:foo},:if-changed", vars);
    Assert.assertEquals(42.0, v, 1e-12);
  }

  @Test
  public void evalIfChangedNo() {
    Map<String, Number> vars = new HashMap<>();
    vars.put("foo", 2.0);
    vars.put("previous:foo", 2.0);
    Double v = MappingExpr.eval("42.0,{foo},{previous:foo},:if-changed", vars);
    Assert.assertEquals(0.0, v, 1e-12);
  }
}
