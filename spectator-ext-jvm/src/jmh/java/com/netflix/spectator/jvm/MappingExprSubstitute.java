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
 * Benchmark                                  Mode  Cnt          Score         Error   Units
 * customReplaceMatch                        thrpt    5   32004441.157 ± 1124280.222   ops/s
 * customReplaceNoMatch                      thrpt    5  265951976.539 ± 3763154.472   ops/s
 * stringReplaceMatch                        thrpt    5     793429.235 ±   11693.098   ops/s
 * stringReplaceNoMatch                      thrpt    5     813017.866 ±   17047.396   ops/s
 *
 *
 * Benchmark                                  Mode  Cnt          Score         Error   Units
 * customReplaceMatch           gc.alloc.rate.norm    5        160.012 ±       0.001    B/op
 * customReplaceNoMatch         gc.alloc.rate.norm    5          0.001 ±       0.001    B/op
 * stringReplaceMatch           gc.alloc.rate.norm    5      17616.448 ±       0.029    B/op
 * stringReplaceNoMatch         gc.alloc.rate.norm    5      17616.442 ±       0.045    B/op
 *
 * ## Java 17
 *
 * Benchmark                                  Mode  Cnt          Score         Error   Units
 * customReplaceMatch                        thrpt    5   22809472.437 ± 1075058.765   ops/s
 * customReplaceNoMatch                      thrpt    5  529952552.582 ± 7374375.509   ops/s
 * stringReplaceMatch                        thrpt    5    2247841.555 ±   58645.893   ops/s
 * stringReplaceNoMatch                      thrpt    5    2156524.729 ±  498269.673   ops/s
 *
 * Benchmark                                  Mode  Cnt          Score         Error   Units
 * customReplaceMatch           gc.alloc.rate.norm    5        160.019 ±       0.001    B/op
 * customReplaceNoMatch         gc.alloc.rate.norm    5          0.001 ±       0.001    B/op
 * stringReplaceMatch           gc.alloc.rate.norm    5        888.197 ±       0.001    B/op
 * stringReplaceNoMatch         gc.alloc.rate.norm    5        888.201 ±       0.036    B/op
 * </pre>
 */
@State(Scope.Thread)
public class MappingExprSubstitute {

  private static final Map<String, String> VARS = new HashMap<>();

  static {
    VARS.put("keyspace", "test");
    VARS.put("scope", "foo");
    VARS.put("name", "ReadLatency");
    VARS.put("type", "ColumnFamily");
    VARS.put("LatencyUnit", "MICROSECONDS");
    VARS.put("RateUnit", "SECONDS");
    VARS.put("EventType", "calls");
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
    bh.consume(MappingExpr.substitute("{keyspace}", VARS));
  }

  @Benchmark
  public void customReplaceNoMatch(Blackhole bh) {
    bh.consume(MappingExpr.substitute("abcdefghi", VARS));
  }

  @Benchmark
  public void stringReplaceMatch(Blackhole bh) {
    bh.consume(substituteString("{keyspace}", VARS));
  }

  @Benchmark
  public void stringReplaceNoMatch(Blackhole bh) {
      bh.consume(substituteString("abcdefghi", VARS));
  }
}
