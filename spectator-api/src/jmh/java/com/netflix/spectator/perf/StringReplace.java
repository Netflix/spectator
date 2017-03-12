/*
 * Copyright 2014-2017 Netflix, Inc.
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

import com.netflix.spectator.impl.AsciiSet;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.infra.Blackhole;

import java.util.UUID;

/**
 * Compares a number of approaches for quickly replacing invalid characters that are part
 * of a string. The goal is to be fast and preferably minimize the number of allocations.
 *
 * The {@link AsciiSet} class uses a combination of array approach, check first, and method
 * handle to avoid an additional allocation and array copy when creating the string (see
 * {@link StringCreate} benchmark for more details).
 *
 * <pre>
 * Benchmark                                Mode  Cnt         Score         Error  Units
 *
 * StringReplace.bad_array                 thrpt   10  16714173.859 ±  234209.339  ops/s
 * StringReplace.bad_asciiSet              thrpt   10  18107063.738 ±  360271.524  ops/s
 * StringReplace.bad_checkFirst            thrpt   10  14165041.869 ±  208433.352  ops/s
 * StringReplace.bad_naive                 thrpt   10    530188.254 ±    8342.491  ops/s
 * StringReplace.bad_stringBuilder         thrpt   10   5754313.189 ±  138045.465  ops/s
 *
 * StringReplace.ok_array                  thrpt   10  18899940.964 ±  293219.495  ops/s
 * StringReplace.ok_asciiSet               thrpt   10  30230490.618 ± 1096911.677  ops/s
 * StringReplace.ok_checkFirst             thrpt   10  30601837.288 ±  453161.602  ops/s
 * StringReplace.ok_naive                  thrpt   10    491380.343 ±    9738.662  ops/s
 * StringReplace.ok_stringBuilder          thrpt   10   6673496.163 ±  115877.036  ops/s
 * </pre>
 */
@State(Scope.Thread)
public class StringReplace {

  private final AsciiSet set = AsciiSet.fromPattern("-._A-Za-z0-9");
  private final String ok = UUID.randomUUID().toString();
  private final String bad = ok.replace('-', ' ');

  private void replace(StringBuilder buf, String input, char replacement) {
    final int n = input.length();
    for (int i = 0; i < n; ++i) {
      final char c = input.charAt(i);
      if (set.contains(c)) {
        buf.append(c);
      } else {
        buf.append(replacement);
      }
    }
  }

  private void replace(char[] buf, String input, char replacement) {
    final int n = input.length();
    for (int i = 0; i < n; ++i) {
      final char c = input.charAt(i);
      if (!set.contains(c)) {
        buf[i] = replacement;
      }
    }
  }

  private String naive(String input, char replacement) {
    return input.replaceAll("[-._A-Za-z0-9]", "" + replacement);
  }

  private String stringBuilder(String input, char replacement) {
    StringBuilder buf = new StringBuilder(input.length());
    replace(buf, input, replacement);
    return buf.toString();
  }

  private String array(String input, char replacement) {
    char[] buf = input.toCharArray();
    replace(buf, input, replacement);
    return new String(buf);
  }

  private String checkFirst(String input, char replacement) {
    return set.containsAll(input) ? input : array(input, replacement);
  }

  private String asciiSet(String input, char replacement) {
    return set.replaceNonMembers(input, replacement);
  }

  @Threads(1)
  @Benchmark
  public void ok_naive(Blackhole bh) {
    bh.consume(naive(ok, '_'));
  }

  @Threads(1)
  @Benchmark
  public void ok_stringBuilder(Blackhole bh) {
    bh.consume(stringBuilder(ok, '_'));
  }

  @Threads(1)
  @Benchmark
  public void ok_array(Blackhole bh) {
    bh.consume(array(ok, '_'));
  }

  @Threads(1)
  @Benchmark
  public void ok_checkFirst(Blackhole bh) {
    bh.consume(checkFirst(ok, '_'));
  }

  @Threads(1)
  @Benchmark
  public void ok_asciiSet(Blackhole bh) {
    bh.consume(asciiSet(ok, '_'));
  }

  @Threads(1)
  @Benchmark
  public void bad_naive(Blackhole bh) {
    bh.consume(naive(bad, '_'));
  }

  @Threads(1)
  @Benchmark
  public void bad_stringBuilder(Blackhole bh) {
    bh.consume(stringBuilder(bad, '_'));
  }

  @Threads(1)
  @Benchmark
  public void bad_array(Blackhole bh) {
    bh.consume(array(bad, '_'));
  }

  @Threads(1)
  @Benchmark
  public void bad_checkFirst(Blackhole bh) {
    bh.consume(checkFirst(bad, '_'));
  }

  @Threads(1)
  @Benchmark
  public void bad_asciiSet(Blackhole bh) {
    bh.consume(asciiSet(bad, '_'));
  }

}
