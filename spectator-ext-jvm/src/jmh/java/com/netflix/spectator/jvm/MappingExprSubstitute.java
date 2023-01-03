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

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import java.beans.Introspector;
import java.util.*;

/**
 * <pre>
 * ## Java 8
 *
 * Benchmark                                  Mode  Cnt         Score              Error   Units
 * customReplaceMatch                        thrpt    5  18983022.881 ±       212610.589   ops/s
 * customReplaceNoMatch                      thrpt    5  19068305.638 ±        54315.365   ops/s
 * stringReplaceMatch                        thrpt    5   4606323.928 ±        36878.183   ops/s
 * stringReplaceNoMatch                      thrpt    5   4636115.030 ±        22758.712   ops/s
 *
 * Benchmark                                  Mode  Cnt         Score              Error   Units
 * customReplaceMatch           gc.alloc.rate.norm    5       141.733 ±           88.578    B/op
 * customReplaceNoMatch         gc.alloc.rate.norm    5       152.020 ±            0.002    B/op
 * stringReplaceMatch           gc.alloc.rate.norm    5      2584.079 ±            0.006    B/op
 * stringReplaceNoMatch         gc.alloc.rate.norm    5      2584.079 ±            0.006    B/op
 *
 * ## Java 17
 *
 * Benchmark                                  Mode  Cnt         Score        Error   Units
 * customReplaceMatch                        thrpt    5  26738250.771 ± 203389.685   ops/s
 * customReplaceNoMatch                      thrpt    5  27456054.651 ± 171103.076   ops/s
 * stringReplaceMatch                        thrpt    5  18817330.662 ±  91736.795   ops/s
 * stringReplaceNoMatch                      thrpt    5  18810950.123 ±  64060.629   ops/s
 *
 * Benchmark                                  Mode  Cnt         Score        Error   Units
 * customReplaceMatch           gc.alloc.rate.norm    5       120.017 ±      0.001    B/op
 * customReplaceNoMatch         gc.alloc.rate.norm    5       120.016 ±      0.001    B/op
 * stringReplaceMatch           gc.alloc.rate.norm    5       120.024 ±      0.001    B/op
 * stringReplaceNoMatch         gc.alloc.rate.norm    5       120.023 ±      0.001    B/op
 *
 * </pre>
 */
@State(Scope.Thread)
public class MappingExprSubstitute {

  private static final Map<String, String> VARS = Collections.singletonMap("{variable}", "value");

  static String substituteCustom(String pattern, Map<String, String> vars) {
    String value = pattern;
    for (Map.Entry<String, String> entry : vars.entrySet()) {
      String raw = entry.getValue();
      String v = Introspector.decapitalize(raw);
      value = MappingExpr.replace(value, "{raw:" + entry.getKey() + "}", raw);
      value = MappingExpr.replace(value, "{" + entry.getKey() + "}", v);
    }
    return value;
  }

  static String substituteString(String pattern, Map<String, String> vars) {
    String value = pattern;
    for (Map.Entry<String, String> entry : vars.entrySet()) {
      String raw = entry.getValue();
      String v = Introspector.decapitalize(raw);
      value = value.replace("{raw:" + entry.getKey() + "}", raw);
      value = value.replace("{" + entry.getKey() + "}", v);
    }
    return value;
  }

  @Benchmark
  public void customReplaceMatch(Blackhole bh) {
    bh.consume(substituteCustom("{variable}", VARS));
  }

  @Benchmark
  public void customReplaceNoMatch(Blackhole bh) {
    bh.consume(substituteCustom("abcdefghi", VARS));
  }

  @Benchmark
  public void stringReplaceMatch(Blackhole bh) {
    bh.consume(substituteString("{variable}", VARS));
  }

  @Benchmark
  public void stringReplaceNoMatch(Blackhole bh) {
      bh.consume(substituteString("abcdefghi", VARS));
  }
}
