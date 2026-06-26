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
package com.netflix.spectator.impl.matcher;

import com.netflix.spectator.impl.PatternExpr;
import com.netflix.spectator.impl.PatternMatcher;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.SortedSet;
import java.util.function.Function;

/**
 * Matches a sequence of literal segments separated by {@code .*}, with anchors permitted only at
 * the extremes, e.g. {@code .*A.*B.*}, {@code .*A.*B$}, or {@code ^A.*B.*C$}. The equivalent nested
 * {@link IndexOfMatcher} chain (optionally fronted by a {@link StartsWithMatcher} and/or terminated
 * by an {@link EndMatcher}) matches the same strings, but does so with a recursive, backtracking
 * scan that is quadratic when an early segment has low selectivity. This matcher collapses the
 * chain into a single greedy left-to-right pass with no backtracking.
 *
 * <p>The greedy pass is correct because each non-anchored segment is an unanchored substring
 * search: taking the earliest match of a segment maximizes the remaining window for the segments
 * that follow, so a failure can never be salvaged by retrying an earlier segment at a later
 * position. A leading {@code ^} pins the first segment with a {@code startsWith} check; a trailing
 * {@code $} pins the last segment with an {@code endsWith} check.</p>
 *
 * <p>Only {@link #matches(String, int, int)} and {@link #minLength()} use the flattened form. All
 * other operations (translation, rewriting, trigrams, etc.) are forwarded to the original nested
 * {@code delegate} so behavior is identical to the unoptimized matcher.</p>
 */
final class IndexOfSeqMatcher implements Matcher, Serializable {

  private static final long serialVersionUID = 1L;

  private final String[] segments;
  private final boolean startAnchored;
  private final boolean endAnchored;
  private final boolean ignoreCase;
  private final Matcher delegate;
  private final int minLength;

  /**
   * Create a new instance.
   *
   * @param segments
   *     Ordered literal segments. When {@code startAnchored}, the first segment must appear at the
   *     start of the value; when {@code endAnchored}, the last segment must appear at the end.
   *     Every other segment is found by an ordered substring search.
   * @param startAnchored
   *     True if the first segment is anchored to the start of the value.
   * @param endAnchored
   *     True if the last segment is anchored to the end of the value.
   * @param ignoreCase
   *     True if the segments should be matched ignoring case.
   * @param delegate
   *     Equivalent nested matcher used for all non-matching operations.
   */
  IndexOfSeqMatcher(
      String[] segments,
      boolean startAnchored,
      boolean endAnchored,
      boolean ignoreCase,
      Matcher delegate) {
    this.segments = segments;
    this.startAnchored = startAnchored;
    this.endAnchored = endAnchored;
    this.ignoreCase = ignoreCase;
    this.delegate = delegate;
    int sum = 0;
    for (String s : segments) {
      sum += s.length();
    }
    this.minLength = sum;
  }

  /** Ordered literal segments. */
  String[] segments() {
    return segments;
  }

  /** True if the first segment is anchored to the start of the value. */
  boolean startAnchored() {
    return startAnchored;
  }

  /** True if the last segment is anchored to the end of the value. */
  boolean endAnchored() {
    return endAnchored;
  }

  /** True if the segments are matched ignoring case. */
  boolean isIgnoreCase() {
    return ignoreCase;
  }

  private boolean regionMatches(String str, int offset, String seg) {
    return str.regionMatches(ignoreCase, offset, seg, 0, seg.length());
  }

  private int indexOf(String str, String seg, int offset) {
    if (!ignoreCase) {
      return str.indexOf(seg, offset);
    }
    final int segLength = seg.length();
    final int end = (str.length() - segLength) + 1;
    for (int i = offset; i < end; ++i) {
      if (str.regionMatches(true, i, seg, 0, segLength)) {
        return i;
      }
    }
    return -1;
  }

  @Override
  public int matches(String str, int start, int length) {
    // The start-anchored prefix is anchored to absolute position 0 (like StartsWithMatcher), not
    // to `start`. When this matcher is embedded as a sub-matcher invoked at a non-zero offset
    // (e.g. a branch of an OrMatcher reached after an earlier match), defer to the delegate to
    // preserve exact semantics. The flat path below handles the common root case (start == 0).
    if (startAnchored && start != 0) {
      return delegate.matches(str, start, length);
    }
    if (length < minLength) {
      return Constants.NO_MATCH;
    }
    final int end = start + length;
    final int n = segments.length;
    int pos = start;
    for (int i = 0; i < n; ++i) {
      final String seg = segments[i];
      if (i == 0 && startAnchored) {
        if (!regionMatches(str, start, seg)) {
          return Constants.NO_MATCH;
        }
        pos = start + seg.length();
      } else if (i == n - 1 && endAnchored) {
        final int endPos = end - seg.length();
        if (endPos < pos || !regionMatches(str, endPos, seg)) {
          return Constants.NO_MATCH;
        }
        pos = end;
      } else {
        final int p = indexOf(str, seg, pos);
        if (p < 0) {
          return Constants.NO_MATCH;
        }
        pos = p + seg.length();
      }
    }
    return pos;
  }

  @Override
  public int minLength() {
    return minLength;
  }

  @Override
  public boolean matchesAfterPrefix(String str) {
    // For a start-anchored fold, prefix() returns segments[0], so a caller (e.g. the QueryIndex
    // prefix tree) may have already verified it; mirror the delegate, which skips re-checking the
    // prefix rather than failing the value. For the unanchored case there is no fixed prefix, so
    // matchesAfterPrefix is just matches() and the flat path is both correct and the fast path --
    // it must NOT defer to the delegate, which would fall back to the slow backtracking chain.
    return startAnchored ? delegate.matchesAfterPrefix(str) : matches(str);
  }

  // All remaining behavior is identical to the unoptimized matcher; forward to the delegate.

  @Override
  public boolean isStartAnchored() {
    return delegate.isStartAnchored();
  }

  @Override
  public boolean isEndAnchored() {
    return delegate.isEndAnchored();
  }

  @Override
  public boolean isPrefixMatcher() {
    return delegate.isPrefixMatcher();
  }

  @Override
  public boolean isContainsMatcher() {
    return delegate.isContainsMatcher();
  }

  @Override
  public boolean alwaysMatches() {
    return delegate.alwaysMatches();
  }

  @Override
  public boolean neverMatches() {
    return delegate.neverMatches();
  }

  @Override
  public String prefix() {
    return delegate.prefix();
  }

  @Override
  public String containedString() {
    return delegate.containedString();
  }

  @Override
  public SortedSet<String> trigrams() {
    return delegate.trigrams();
  }

  @Override
  public Matcher rewrite(Function<Matcher, Matcher> f) {
    return delegate.rewrite(f);
  }

  @Override
  public Matcher rewriteEnd(Function<Matcher, Matcher> f) {
    return delegate.rewriteEnd(f);
  }

  @Override
  public List<PatternMatcher> expandOrClauses(int max) {
    return delegate.expandOrClauses(max);
  }

  @Override
  public PatternExpr toPatternExpr(int max) {
    return delegate.toPatternExpr(max);
  }

  @Override
  public String toSqlPattern() {
    return delegate.toSqlPattern();
  }

  @Override
  public String toString() {
    return delegate.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    IndexOfSeqMatcher that = (IndexOfSeqMatcher) o;
    return startAnchored == that.startAnchored
        && endAnchored == that.endAnchored
        && ignoreCase == that.ignoreCase
        && minLength == that.minLength
        && Arrays.equals(segments, that.segments);
  }

  @Override
  public int hashCode() {
    int result = Arrays.hashCode(segments);
    result = 31 * result + Boolean.hashCode(startAnchored);
    result = 31 * result + Boolean.hashCode(endAnchored);
    result = 31 * result + Boolean.hashCode(ignoreCase);
    result = 31 * result + minLength;
    return result;
  }
}
