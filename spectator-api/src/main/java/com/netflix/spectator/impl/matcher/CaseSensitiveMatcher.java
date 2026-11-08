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
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;

/**
 * Matcher for a pattern with a class where the set once the case is ignored is not the same
 * as adding the other case of each letter, e.g. {@code [^a]} or {@code [^a-z]}. Some rewrites
 * of the optimizer are then only valid for the case as written. For example, {@code [^a]*A?}
 * is the same as {@code [^a]*}, but once the case is ignored, {@code [^a]} no longer matches
 * 'A'. The optimized matcher is used for everything other than ignoring the case. To ignore
 * the case, the pattern before it was optimized is folded first, the same as for the
 * {@code (?i)} flag.
 */
final class CaseSensitiveMatcher implements PatternMatcher, Serializable {

  private static final long serialVersionUID = 1L;

  private final Matcher matcher;
  private final Matcher original;

  /**
   * Create a new instance.
   *
   * @param matcher
   *     Optimized matcher for the pattern.
   * @param original
   *     Matcher for the complete pattern before it was optimized. It is used for
   *     {@link #ignoreCase()}.
   */
  CaseSensitiveMatcher(Matcher matcher, Matcher original) {
    this.matcher = matcher;
    this.original = original;
  }

  /** Matcher used for checking strings. */
  Matcher matcher() {
    return matcher;
  }

  @Override
  public boolean matches(String str) {
    return matcher.matches(str);
  }

  @Override
  public boolean matchesAfterPrefix(String str) {
    return matcher.matchesAfterPrefix(str);
  }

  @Override
  public String prefix() {
    return matcher.prefix();
  }

  @Override
  public String containedString() {
    return matcher.containedString();
  }

  @Override
  public int minLength() {
    return matcher.minLength();
  }

  @Override
  public boolean isStartAnchored() {
    return matcher.isStartAnchored();
  }

  @Override
  public boolean isEndAnchored() {
    return matcher.isEndAnchored();
  }

  @Override
  public boolean alwaysMatches() {
    return matcher.alwaysMatches();
  }

  @Override
  public boolean neverMatches() {
    return matcher.neverMatches();
  }

  @Override
  public boolean isPrefixMatcher() {
    return matcher.isPrefixMatcher();
  }

  @Override
  public boolean isContainsMatcher() {
    return matcher.isContainsMatcher();
  }

  @Override
  public SortedSet<String> trigrams() {
    return matcher.trigrams();
  }

  @Override
  public PatternMatcher ignoreCase() {
    Matcher m = Optimizer.optimize(original.rewrite(PatternUtils::ignoreCase));
    PatternUtils.checkRepetitions(m, "(?i)" + matcher);
    PatternUtils.checkChainedRepetitions(m, "(?i)" + matcher);
    return new IgnoreCaseMatcher(m);
  }

  /**
   * The clauses are built from the optimized matcher. Like {@link #toPatternExpr(int)},
   * ignoring the case for a clause uses the clause at the same position for the pattern
   * that ignores the case, see {@link ExprLeafMatcher}.
   */
  @SuppressWarnings("PMD.ReturnEmptyCollectionRatherThanNull")
  @Override
  public List<PatternMatcher> expandOrClauses(int max) {
    List<PatternMatcher> clauses = matcher.expandOrClauses(max);
    return clauses == null
        ? null
        : ExprLeafMatcher.wrap(clauses, ExprLeafMatcher.Folded.forClauses(this, max));
  }

  /**
   * The expression is built from the optimized matcher. Ignoring the case for each of the
   * sub-expressions could give a different result than ignoring the case for the pattern,
   * so ignoring the case uses the expression for the pattern that ignores the case, see
   * {@link ExprLeafMatcher}.
   */
  @Override
  public PatternExpr toPatternExpr(int max) {
    PatternExpr expr = matcher.toPatternExpr(max);
    if (expr == null) {
      return null;
    }
    return ExprLeafMatcher.wrap(expr, ExprLeafMatcher.Folded.forExpr(this, max));
  }

  @Override
  public String toSqlPattern() {
    return matcher.toSqlPattern();
  }

  @Override
  public String toString() {
    return matcher.toString();
  }

  // Only the optimized matcher is compared, the same as for a pattern that is not wrapped.
  // Patterns that only differ before they are optimized, e.g. [^a]*A? and [^a]*, are equal
  // when the case is not ignored.

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    CaseSensitiveMatcher that = (CaseSensitiveMatcher) o;
    return Objects.equals(matcher, that.matcher);
  }

  @Override
  public int hashCode() {
    return matcher.hashCode();
  }
}
