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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.function.Supplier;

/**
 * Matcher for a sub-expression of {@link PatternExpr} from a {@link CaseSensitiveMatcher}.
 * The sub-expressions are built from the pattern as optimized for the case as written, so
 * ignoring the case for each of them could give a different result than ignoring the case
 * for the pattern. Instead, ignoring the case for the whole expression returns the expression
 * for the pattern that ignores the case, and {@link #ignoreCase()} for a sub-expression
 * returns the matcher for the same position in that expression, or throws if the structure
 * is different. The expression is only computed if the case is ignored, and then shared.
 */
final class ExprLeafMatcher implements PatternMatcher, Serializable {

  private static final long serialVersionUID = 1L;

  private final PatternMatcher matcher;
  private final Folded folded;
  private final int[] path;

  /**
   * Create a new instance.
   *
   * @param matcher
   *     Matcher used for checking strings.
   * @param folded
   *     Expression for the pattern that ignores the case.
   * @param path
   *     Index of the sub-expression and the number of sub-expressions at each level of the
   *     expression, so the position is only used if the structure is the same.
   */
  ExprLeafMatcher(PatternMatcher matcher, Folded folded, int[] path) {
    this.matcher = matcher;
    this.folded = folded;
    this.path = path;
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
    // The leaf cannot be folded by itself, even if it has no class where the folded set is
    // different, because the optimizer may have already replaced such a class, e.g.
    // [a&&[^A]] => a, but with the case ignored it never matches.
    PatternMatcher m = folded.find(path);
    if (m == null) {
      throw folded.error();
    }
    return m;
  }

  @Override
  public List<PatternMatcher> expandOrClauses(int max) {
    return matcher.expandOrClauses(max);
  }

  @Override
  public PatternExpr toPatternExpr(int max) {
    // The leaf is a simple pattern, so the expression is usually just the leaf. Use this
    // matcher for it so that ignoring the case uses the folded expression rather than
    // ignoring the case for the optimized leaf.
    PatternExpr expr = matcher.toPatternExpr(max);
    if (expr instanceof PatternExpr.Regex
        && ((PatternExpr.Regex) expr).matcher().equals(matcher)) {
      return PatternExpr.simple(this);
    }
    return expr;
  }

  @Override
  public String toSqlPattern() {
    return matcher.toSqlPattern();
  }

  @Override
  public String toString() {
    return matcher.toString();
  }

  /**
   * Wrap the leaves of the expression as optimized for the case as written so that
   * ignoring the case uses the expression for the pattern that ignores the case.
   *
   * @param expr
   *     Expression for the pattern as optimized for the case as written.
   * @param folded
   *     Expression for the pattern that ignores the case.
   */
  static PatternExpr wrap(PatternExpr expr, Folded folded) {
    return PatternExpr.withIgnoreCase(wrap(expr, folded, new int[0]), folded);
  }

  private static PatternExpr wrap(PatternExpr expr, Folded folded, int[] path) {
    if (expr instanceof PatternExpr.Regex) {
      PatternMatcher m = ((PatternExpr.Regex) expr).matcher();
      return PatternExpr.simple(new ExprLeafMatcher(m, folded, path));
    } else if (expr instanceof PatternExpr.Not) {
      return PatternExpr.not(wrap(((PatternExpr.Not) expr).expr(), folded, child(path, 0, 1)));
    } else if (expr instanceof PatternExpr.And) {
      return PatternExpr.and(wrap(((PatternExpr.And) expr).exprs(), folded, path));
    } else {
      return PatternExpr.or(wrap(((PatternExpr.Or) expr).exprs(), folded, path));
    }
  }

  private static List<PatternExpr> wrap(List<PatternExpr> exprs, Folded folded, int[] path) {
    List<PatternExpr> results = new ArrayList<>(exprs.size());
    for (int i = 0; i < exprs.size(); ++i) {
      results.add(wrap(exprs.get(i), folded, child(path, i, exprs.size())));
    }
    return results;
  }

  private static int[] child(int[] path, int i, int size) {
    int[] p = Arrays.copyOf(path, path.length + 2);
    p[path.length] = i;
    p[path.length + 1] = size;
    return p;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ExprLeafMatcher that = (ExprLeafMatcher) o;
    return matcher.equals(that.matcher)
        && folded.equals(that.folded)
        && Arrays.equals(path, that.path);
  }

  @Override
  public int hashCode() {
    return 31 * Objects.hash(matcher, folded) + Arrays.hashCode(path);
  }

  /**
   * Expression for the pattern that ignores the case. It is computed the first time it is
   * needed. If ignoring the case for the pattern throws, then that error is used for the
   * sub-expressions.
   */
  static final class Folded implements Supplier<PatternExpr>, Serializable {

    private static final long serialVersionUID = 1L;

    private final CaseSensitiveMatcher source;
    private final int max;

    private transient volatile PatternExpr expr;
    private transient volatile IllegalArgumentException error;
    private transient int hash;

    Folded(CaseSensitiveMatcher source, int max) {
      this.source = source;
      this.max = max;
    }

    /** Ensure the expression has been computed, returns null if it is not available. */
    private PatternExpr expr() {
      if (expr == null && error == null) {
        PatternExpr e = null;
        try {
          e = source.ignoreCase().toPatternExpr(max);
        } catch (IllegalArgumentException ex) {
          error = ex;
        }
        expr = e;
        if (e == null && error == null) {
          error = new IllegalArgumentException("unsupported: toPatternExpr(" + max
              + ") is null when the case is ignored\n" + source);
        }
      }
      return expr;
    }

    /** Returns the expression, or throws if ignoring the case for the pattern throws. */
    @Override
    public PatternExpr get() {
      PatternExpr e = expr();
      if (e == null) {
        throw error();
      }
      return e;
    }

    /**
     * Returns the matcher at the path, or null if the expression is not available or the
     * structure is different.
     */
    PatternMatcher find(int[] path) {
      PatternExpr e = expr();
      for (int i = 0; e != null && i < path.length; i += 2) {
        e = child(e, path[i], path[i + 1]);
      }
      return e instanceof PatternExpr.Regex ? ((PatternExpr.Regex) e).matcher() : null;
    }

    /** Returns the sub-expression at the index if there are the expected number. */
    private static PatternExpr child(PatternExpr e, int i, int size) {
      List<PatternExpr> exprs;
      if (e instanceof PatternExpr.Not) {
        return size == 1 ? ((PatternExpr.Not) e).expr() : null;
      } else if (e instanceof PatternExpr.And) {
        exprs = ((PatternExpr.And) e).exprs();
      } else if (e instanceof PatternExpr.Or) {
        exprs = ((PatternExpr.Or) e).exprs();
      } else {
        return null;
      }
      return exprs.size() == size ? exprs.get(i) : null;
    }

    /**
     * Error to throw if there is no matcher for a path. If there is an expression, then it
     * has a different structure, so only the whole expression can ignore the case.
     */
    IllegalArgumentException error() {
      // A new exception each time rather than throwing the shared instance from several
      // places and threads, the cause is the error from ignoring the case for the pattern
      IllegalArgumentException e = error;
      return e != null
          ? new IllegalArgumentException(e.getMessage(), e)
          : new IllegalArgumentException("unsupported: ignoring the case for part of the "
              + "expression from toPatternExpr, ignore the case for the whole expression "
              + "instead\n" + source);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Folded that = (Folded) o;
      return max == that.max && source.equals(that.source);
    }

    @Override
    public int hashCode() {
      // Cached as each leaf of the expression uses it and the hash code of the source is
      // computed from the whole pattern
      int h = hash;
      if (h == 0) {
        h = 31 * source.hashCode() + max;
        hash = h;
      }
      return h;
    }
  }
}
