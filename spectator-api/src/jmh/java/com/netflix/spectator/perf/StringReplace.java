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

import com.netflix.spectator.impl.AsciiSet;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.infra.Blackhole;

import java.util.BitSet;
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
 * Benchmark                               Mode  Cnt         Score         Error   Units
 *
 * bad_array                              thrpt    5  20960080.404 ± 2528326.088   ops/s
 * bad_asciiSet                           thrpt    5  28525355.999 ± 1110328.417   ops/s
 * bad_bitSet                             thrpt    5  25384526.493 ±  399471.760   ops/s
 * bad_checkFirst                         thrpt    5  28570631.420 ±  413296.441   ops/s
 * bad_naive                              thrpt    5    798526.522 ±   24004.695   ops/s
 * bad_stringBuilder                      thrpt    5  10592798.051 ±  500514.554   ops/s
 *
 * ok_array                               thrpt    5  19738313.781 ± 2000737.155   ops/s
 * ok_asciiSet                            thrpt    5  47503468.615 ± 2395740.563   ops/s
 * ok_bitSet                              thrpt    5  37388017.571 ± 2415574.832   ops/s
 * ok_checkFirst                          thrpt    5  47555675.336 ± 1323382.528   ops/s
 * ok_naive                               thrpt    5    641579.313 ±   10920.803   ops/s
 * ok_stringBuilder                       thrpt    5  13520065.966 ±  925254.484   ops/s
 * </pre>
 */
@State(Scope.Thread)
public class StringReplace {

  private static BitSet toBitSet(AsciiSet set) {
    BitSet bits = new BitSet();
    for (int i = 0; i < 128; ++i) {
      char c = (char) i;
      if (set.contains(c))
        bits.set(i);
    }
    return bits;
  }

  private final AsciiSet set = AsciiSet.fromPattern("-._A-Za-z0-9");
  private final BitSet members = toBitSet(set);
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

  boolean contains(char c) {
    return members.get(c);
  }

  private boolean containsAll(CharSequence str) {
    final int n = str.length();
    for (int i = 0; i < n; ++i) {
      if (!contains(str.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  private String bitSet(String input, char replacement) {
    if (containsAll(input)) {
      return input;
    } else {
      final int n = input.length();
      final char[] buf = input.toCharArray();
      for (int i = 0; i < n; ++i) {
        final char c = buf[i];
        if (!contains(c)) {
          buf[i] = replacement;
        }
      }
      return new String(buf);
    }
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
  public void ok_bitSet(Blackhole bh) {
    bh.consume(bitSet(ok, '_'));
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

  @Threads(1)
  @Benchmark
  public void bad_bitSet(Blackhole bh) {
    bh.consume(bitSet(bad, '_'));
  }

}
