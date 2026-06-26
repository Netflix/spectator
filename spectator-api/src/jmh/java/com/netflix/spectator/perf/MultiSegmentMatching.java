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
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Benchmark for multi-segment "contains" regex patterns of the form {@code .*A.*B.*C...}, which
 * compile to a chain of nested {@link com.netflix.spectator.impl.matcher.IndexOfMatcher} instances
 * (optionally fronted by a start anchor or terminated by an end anchor). These show up in
 * production LWC subscription expressions, frequently wrapped in {@code :not}, on high-cardinality
 * keys such as {@code nf.cluster} and {@code ipc.endpoint}:
 *
 * <pre>
 *   nf.cluster,.*-.*-md-preview-.*,:re,:not
 *   nf.cluster,.*-.*-chap-canary,:re,:not
 *   ipc.endpoint,.*Available.*SnapshotVersions.*,:re,:not
 * </pre>
 *
 * <p>The matchers are compiled the same way {@code Query.Regex} does ({@code "^" + value}) and
 * invoked via {@link PatternMatcher#matches(String)}, which is what the inverted-query path in
 * {@code QueryIndex} ultimately calls per distinct value.</p>
 *
 * <p>The {@code depth} parameter is the number of {@code .*}-separated single-dash gap segments
 * before a fixed terminal segment. The leading gaps have low selectivity (a single {@code "-"}):
 * the recursive matcher finds a dash at nearly every position and, on each one, recurses into the
 * next segment and backtracks on failure, so cost grows with both the number of dashes in the
 * value and the pattern depth. {@code shape} covers the three foldable forms (plain contains,
 * end-anchored {@code $}, start-anchored {@code ^}). The non-matching value is the dominant
 * production case (most values do not contain the terminal) and the worst case for backtracking.</p>
 */
@State(Scope.Thread)
public class MultiSegmentMatching {

  /** Number of {@code .*}-separated single-dash gap segments before the terminal segment. */
  @Param({"1", "2", "3", "4", "5"})
  public int depth;

  /** Foldable pattern shape: plain contains, end-anchored, or start-anchored. */
  @Param({"contains", "end", "start"})
  public String shape;

  /** Whether the value matches the pattern. The non-matching case is the backtracking worst case. */
  @Param({"noMatch", "match"})
  public String value;

  private PatternMatcher matcher;
  private String input;

  @Setup
  public void setup() {
    StringBuilder gaps = new StringBuilder();
    for (int i = 0; i < depth; i++) {
      gaps.append(".*-");
    }
    // Build the user pattern; Query.Regex anchors it with a leading "^".
    final String regex;
    switch (shape) {
      case "end":
        regex = gaps + ".*mdpreview$";
        break;
      case "start":
        regex = "pre" + gaps + ".*mdpreview.*";
        break;
      case "contains":
      default:
        regex = gaps + ".*mdpreview.*";
        break;
    }
    this.matcher = PatternMatcher.compile("^" + regex);

    // Both values start with "pre" and have many dashes; the matching value also contains the
    // terminal segment at the end so it is valid for all three shapes.
    this.input = "match".equals(value)
        ? "pre-a-b-c-d-e-f-mdpreview"
        : "pre-prod-useast1-main-canary-v123-shadow-42";
  }

  @Benchmark
  public void match(Blackhole bh) {
    bh.consume(matcher.matches(input));
  }
}
