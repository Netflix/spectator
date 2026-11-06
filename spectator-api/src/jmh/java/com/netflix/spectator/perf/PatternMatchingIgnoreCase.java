/*
 * Copyright 2014-2026 Netflix, Inc.
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

import com.netflix.spectator.impl.PatternMatcher;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Locale;

/**
 * Case insensitive matching. Each pattern is checked against an input with the same case,
 * so most chars are equal, and against an input with the other case, so every letter needs
 * to be folded.
 */
@State(Scope.Thread)
public class PatternMatchingIgnoreCase {

  // Fixed value so results can be compared between runs, the patterns below all match it
  private final String example = "ab3f5c1e-9d45-4e2a-b123-0f7e6d5c4b3a";
  private final String lower = new String(example);
  private final String upper = example.toUpperCase(Locale.US);

  private final String substr = example.substring(example.length() - 17, example.length() - 4);

  private final PatternMatcher prefix = PatternMatcher.compile("^" + example).ignoreCase();
  private final PatternMatcher substring = PatternMatcher.compile(substr).ignoreCase();
  private final PatternMatcher startIndexOf = PatternMatcher.compile("^ab.*123").ignoreCase();
  private final PatternMatcher multiIndexOf = PatternMatcher.compile("ab.*45.*123").ignoreCase();
  private final PatternMatcher or = PatternMatcher.compile(
      "(abc|bcd|cde|def|ef0|f01|012|123|234|456|567|678|789|890|90a|0ab)").ignoreCase();
  // Only the last prefix matches, so all of them are checked
  private final PatternMatcher orPrefix = PatternMatcher.compile(
      "^(abc|bcd|cde|def|ef0|f01|012|123|234|456|567|678|789|890|90a|ab3)").ignoreCase();
  private final PatternMatcher seq = PatternMatcher.compile(
      "^" + example.substring(0, 8) + "-[0-9a-f]{4}-" + example.substring(14)).ignoreCase();

  @Benchmark
  public void prefixSameCase(Blackhole bh) {
    bh.consume(prefix.matches(lower));
  }

  @Benchmark
  public void prefixOtherCase(Blackhole bh) {
    bh.consume(prefix.matches(upper));
  }

  @Benchmark
  public void substrSameCase(Blackhole bh) {
    bh.consume(substring.matches(lower));
  }

  @Benchmark
  public void substrOtherCase(Blackhole bh) {
    bh.consume(substring.matches(upper));
  }

  @Benchmark
  public void startIndexOfSameCase(Blackhole bh) {
    bh.consume(startIndexOf.matches(lower));
  }

  @Benchmark
  public void startIndexOfOtherCase(Blackhole bh) {
    bh.consume(startIndexOf.matches(upper));
  }

  @Benchmark
  public void multiIndexOfSameCase(Blackhole bh) {
    bh.consume(multiIndexOf.matches(lower));
  }

  @Benchmark
  public void multiIndexOfOtherCase(Blackhole bh) {
    bh.consume(multiIndexOf.matches(upper));
  }

  @Benchmark
  public void orSameCase(Blackhole bh) {
    bh.consume(or.matches(lower));
  }

  @Benchmark
  public void orOtherCase(Blackhole bh) {
    bh.consume(or.matches(upper));
  }

  @Benchmark
  public void orPrefixSameCase(Blackhole bh) {
    bh.consume(orPrefix.matches(lower));
  }

  @Benchmark
  public void orPrefixOtherCase(Blackhole bh) {
    bh.consume(orPrefix.matches(upper));
  }

  @Benchmark
  public void seqSameCase(Blackhole bh) {
    bh.consume(seq.matches(lower));
  }

  @Benchmark
  public void seqOtherCase(Blackhole bh) {
    bh.consume(seq.matches(upper));
  }
}
