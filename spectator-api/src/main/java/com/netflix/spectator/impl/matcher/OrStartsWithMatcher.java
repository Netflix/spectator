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
 * Matches a start-anchored alternation of literal prefixes, e.g. {@code ^(abc|def|ghi)}. The
 * equivalent {@code SeqMatcher(StartMatcher, OrMatcher(CharSeq, ...))} matches the same strings by
 * dispatching an interface call per branch; this matcher collapses it to a single monomorphic loop
 * of {@code regionMatches} checks, equivalent to "does the value start with any of these prefixes".
 *
 * <p>Only {@link #matches(String, int, int)} and {@link #minLength()} use the flattened form; all
 * other operations forward to the original nested {@code delegate} so behavior is identical.</p>
 */
final class OrStartsWithMatcher implements Matcher, Serializable {

  private static final long serialVersionUID = 1L;

  private final String[] prefixes;
  private final boolean ignoreCase;
  private final Matcher delegate;
  private final int minLength;

  /**
   * Create a new instance.
   *
   * @param prefixes
   *     Literal prefixes; the value matches if it starts with any one of them.
   * @param ignoreCase
   *     True if the prefixes should be matched ignoring case.
   * @param delegate
   *     Equivalent nested matcher used for all non-matching operations.
   */
  OrStartsWithMatcher(String[] prefixes, boolean ignoreCase, Matcher delegate) {
    this.prefixes = prefixes;
    this.ignoreCase = ignoreCase;
    this.delegate = delegate;
    int min = Integer.MAX_VALUE;
    for (String p : prefixes) {
      min = Math.min(min, p.length());
    }
    this.minLength = min;
  }

  @Override
  public int matches(String str, int start, int length) {
    // The alternation is start-anchored to absolute position 0 (via StartMatcher). When invoked as
    // an embedded sub-matcher at a non-zero offset, defer to the delegate to preserve semantics
    // (StartMatcher fails for start != 0). The flat path handles the common root case.
    if (start != 0) {
      return delegate.matches(str, start, length);
    }
    for (String p : prefixes) {
      if (str.regionMatches(ignoreCase, 0, p, 0, p.length())) {
        return p.length();
      }
    }
    return Constants.NO_MATCH;
  }

  @Override
  public int minLength() {
    return minLength;
  }

  // All remaining behavior is identical to the unoptimized matcher; forward to the delegate.

  @Override
  public boolean matchesAfterPrefix(String str) {
    // prefix() forwards to the delegate, whose first element is StartMatcher (no literal prefix),
    // so prefix() is null and a caller has nothing to have pre-verified -- matchesAfterPrefix is
    // just matches(). Use the fast flat loop here; deferring to the delegate would fall back to the
    // slow nested Seq[Start, Or] matching that this matcher exists to avoid.
    return matches(str);
  }

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
    OrStartsWithMatcher that = (OrStartsWithMatcher) o;
    return ignoreCase == that.ignoreCase
        && minLength == that.minLength
        && Arrays.equals(prefixes, that.prefixes);
  }

  @Override
  public int hashCode() {
    int result = Arrays.hashCode(prefixes);
    result = 31 * result + Boolean.hashCode(ignoreCase);
    result = 31 * result + minLength;
    return result;
  }
}
