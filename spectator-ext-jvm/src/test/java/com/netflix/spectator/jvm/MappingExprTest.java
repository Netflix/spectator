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
package com.netflix.spectator.jvm;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;


public class MappingExprTest {

  @Test
  public void substituteEmpty() {
    Map<String, String> vars = new HashMap<>();
    String actual = MappingExpr.substitute("", vars);
    Assertions.assertEquals("", actual);
  }

  @Test
  public void substituteMissing() {
    Map<String, String> vars = new HashMap<>();
    String actual = MappingExpr.substitute("abc{def}", vars);
    Assertions.assertEquals("abc{def}", actual);
  }

  @Test
  public void substituteSingle() {
    Map<String, String> vars = new HashMap<>();
    vars.put("def", "123");
    String actual = MappingExpr.substitute("abc{def}", vars);
    Assertions.assertEquals("abc123", actual);
  }

  @Test
  public void substituteSingleEmptyVarName() {
    Map<String, String> vars = new HashMap<>();
    vars.put("", "123");
    String actual = MappingExpr.substitute("abc{}", vars);
    Assertions.assertEquals("abc123", actual);
  }

  @Test
  public void substituteSingleMissingClose() {
    Map<String, String> vars = new HashMap<>();
    vars.put("def", "123");
    String actual = MappingExpr.substitute("abc{def", vars);
    Assertions.assertEquals("abc{def", actual);
  }

  @Test
  public void substituteMultiple() {
    Map<String, String> vars = new HashMap<>();
    vars.put("def", "123");
    String actual = MappingExpr.substitute("abc{def}, {def}", vars);
    Assertions.assertEquals("abc123, 123", actual);
  }

  @Test
  public void substituteMultipleMissingClose() {
    Map<String, String> vars = new HashMap<>();
    vars.put("def", "123");
    String actual = MappingExpr.substitute("abc{def}, {def", vars);
    Assertions.assertEquals("abc123, {def", actual);
  }

  @Test
  public void substituteMultipleContainsOpenBrace() {
    Map<String, String> vars = new HashMap<>();
    vars.put("def, {def", "123");
    String actual = MappingExpr.substitute("abc{def, {def}", vars);
    Assertions.assertEquals("abc123", actual);
  }

  @Test
  public void substituteMultiVar() {
    Map<String, String> vars = new HashMap<>();
    vars.put("def", "123");
    vars.put("ghi", "456");
    String actual = MappingExpr.substitute("abc{def}, {ghi}, {def}", vars);
    Assertions.assertEquals("abc123, 456, 123", actual);
  }

  @Test
  public void substituteDecapitalize() {
    Map<String, String> vars = new HashMap<>();
    vars.put("name", "FooBarBaz");
    String actual = MappingExpr.substitute("abc.def.{name}", vars);
    Assertions.assertEquals("abc.def.fooBarBaz", actual);
  }

  @Test
  public void substituteRaw() {
    Map<String, String> vars = new HashMap<>();
    vars.put("name", "FooBarBaz");
    String actual = MappingExpr.substitute("abc.def.{raw:name}", vars);
    Assertions.assertEquals("abc.def.FooBarBaz", actual);
  }

  @Test
  public void substituteRawEmtpyVarName() {
    Map<String, String> vars = new HashMap<>();
    vars.put("", "FooBarBaz");
    String actual = MappingExpr.substitute("abc.def.{raw:}", vars);
    Assertions.assertEquals("abc.def.FooBarBaz", actual);
  }

  @Test
  public void evalMissing() {
    Map<String, Number> vars = new HashMap<>();
    Double v = MappingExpr.eval("{foo}", vars);
    Assertions.assertTrue(v.isNaN());
  }

  @Test
  public void evalSimple() {
    Map<String, Number> vars = new HashMap<>();
    vars.put("foo", 0.0);
    Double v = MappingExpr.eval("{foo}", vars);
    Assertions.assertEquals(0.0, v, 1e-12);
  }

  @Test
  public void evalConstant() {
    Map<String, Number> vars = new HashMap<>();
    Double v = MappingExpr.eval("42.0", vars);
    Assertions.assertEquals(42.0, v, 1e-12);
  }

  @Test
  public void evalAdd() {
    Map<String, Number> vars = new HashMap<>();
    vars.put("foo", 1.0);
    Double v = MappingExpr.eval("42.0,{foo},:add", vars);
    Assertions.assertEquals(43.0, v, 1e-12);
  }

  @Test
  public void evalSub() {
    Map<String, Number> vars = new HashMap<>();
    vars.put("foo", 1.0);
    Double v = MappingExpr.eval("42.0,{foo},:sub", vars);
    Assertions.assertEquals(41.0, v, 1e-12);
  }

  @Test
  public void evalMul() {
    Map<String, Number> vars = new HashMap<>();
    vars.put("foo", 2.0);
    Double v = MappingExpr.eval("42.0,{foo},:mul", vars);
    Assertions.assertEquals(84.0, v, 1e-12);
  }

  @Test
  public void evalDiv() {
    Map<String, Number> vars = new HashMap<>();
    vars.put("foo", 2.0);
    Double v = MappingExpr.eval("42.0,{foo},:div", vars);
    Assertions.assertEquals(21.0, v, 1e-12);
  }

  @Test
  public void evalIfChangedYes() {
    Map<String, Number> vars = new HashMap<>();
    vars.put("foo", 2.0);
    vars.put("previous:foo", 3.0);
    Double v = MappingExpr.eval("42.0,{foo},{previous:foo},:if-changed", vars);
    Assertions.assertEquals(42.0, v, 1e-12);
  }

  @Test
  public void evalIfChangedNo() {
    Map<String, Number> vars = new HashMap<>();
    vars.put("foo", 2.0);
    vars.put("previous:foo", 2.0);
    Double v = MappingExpr.eval("42.0,{foo},{previous:foo},:if-changed", vars);
    Assertions.assertEquals(0.0, v, 1e-12);
  }
}
