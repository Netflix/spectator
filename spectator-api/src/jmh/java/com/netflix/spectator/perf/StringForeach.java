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
package com.netflix.spectator.perf;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.infra.Blackhole;

import java.util.OptionalInt;
import java.util.UUID;

/**
 * Sanity check on iterating over a string using newer language features. Streams are
 * really slow and should be avoided for the time being.
 *
 * <pre>
 * Benchmark                           Mode  Cnt         Score         Error  Units
 * StringForeach.arrayForLoop         thrpt   10  69226595.071 ± 3958789.722  ops/s
 * StringForeach.arrayForeach         thrpt   10  69282507.672 ± 2090321.612  ops/s
 * StringForeach.strForLoop           thrpt   10  55900971.604 ± 2190481.894  ops/s
 * StringForeach.stream               thrpt   10   4337780.512 ±  102242.727  ops/s
 * </pre>
 */
@State(Scope.Thread)
public class StringForeach {

  private final String str = UUID.randomUUID().toString();
  private final char[] arr = str.toCharArray();

  @Threads(1)
  @Benchmark
  public void arrayForLoop(Blackhole bh) {
    int v = 0;
    int n = arr.length;
    for (int i = 0; i < n; ++i) {
      v += arr[i];
    }
    bh.consume(v);
  }

  @Threads(1)
  @Benchmark
  public void arrayForeach(Blackhole bh) {
    int v = 0;
    for (char c : arr) {
      v += c;
    }
    bh.consume(v);
  }

  @Threads(1)
  @Benchmark
  public void strForLoop(Blackhole bh) {
    int v = 0;
    int n = str.length();
    for (int i = 0; i < n; ++i) {
      v += str.charAt(i);
    }
    bh.consume(v);
  }

  @Threads(1)
  @Benchmark
  public void stream(Blackhole bh) {
    OptionalInt v = str.chars().reduce((acc, c) -> acc + c);
    bh.consume(v.isPresent() ? v.getAsInt() : 0);
  }

}
