/*
 * Copyright 2014-2022 Netflix, Inc.
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
package com.netflix.spectator.impl;

import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/**
 * Represents an expression of simpler patterns combined with AND, OR, and NOT clauses.
 * This can be used for rewriting complex regular expressions to simpler patterns for
 * data stores that either support a more limited set of pattern or have better optimizations
 * in place for simpler operations like starts with or contains.
 */
public interface PatternExpr {

  /**
   * Returns true if the expression matches the value. This is a helper mostly used for testing
   * to ensure the matching logic is consistent with the original regular expression.
   */
  boolean matches(String value);

  /**
   * Returns a copy of the expression that will ignore the case when matching.
   */
  PatternExpr ignoreCase();

  /**
   * Convert this expression into a query string. A common example would be to implement
   * an encoder that would convert it into part of a WHERE clause for a SQL DB.
   */
  default String toQueryString(Encoder encoder) {
    StringBuilder builder = new StringBuilder();
    buildQueryString(encoder, builder);
    return builder.toString();
  }

  /**
   * Convert this expression into a query string. A common example would be to implement
   * an encoder that would convert it into part of a WHERE clause for a SQL DB.
   */
  default void buildQueryString(Encoder encoder, StringBuilder builder) {
    if (this instanceof Regex) {
      Regex re = (Regex) this;
      builder.append(encoder.regex(re.matcher()));
    } else if (this instanceof And) {
      List<PatternExpr> exprs = ((And) this).exprs();
      int size = exprs.size();
      if (size == 1) {
        exprs.get(0).buildQueryString(encoder, builder);
      } else if (size > 1) {
        builder.append(encoder.startAnd());
        exprs.get(0).buildQueryString(encoder, builder);
        for (int i = 1; i < size; ++i) {
          builder.append(encoder.separatorAnd());
          exprs.get(i).buildQueryString(encoder, builder);
        }
        builder.append(encoder.endAnd());
      }
    } else if (this instanceof Or) {
      List<PatternExpr> exprs = ((Or) this).exprs();
      int size = exprs.size();
      if (size == 1) {
        exprs.get(0).buildQueryString(encoder, builder);
      } else if (size > 1) {
        builder.append(encoder.startOr());
        exprs.get(0).buildQueryString(encoder, builder);
        for (int i = 1; i < size; ++i) {
          builder.append(encoder.separatorOr());
          exprs.get(i).buildQueryString(encoder, builder);
        }
        builder.append(encoder.endOr());
      }
    } else if (this instanceof Not) {
      builder.append(encoder.startNot());
      ((Not) this).expr().buildQueryString(encoder, builder);
      builder.append(encoder.endNot());
    }
  }

  /**
   * Encoder to map a pattern expression to an expression for some other language.
   */
  interface Encoder {

    /** Encode a simple pattern match. */
    String regex(PatternMatcher matcher);

    /** Encode the start for a chain of clauses combined with AND. */
    String startAnd();

    /** Encode the separator for a chain of clauses combined with AND. */
    String separatorAnd();

    /** Encode the end for a chain of clauses combined with AND. */
    String endAnd();

    /** Encode the start for a chain of clauses combined with OR. */
    String startOr();

    /** Encode the separator for a chain of clauses combined with OR. */
    String separatorOr();

    /** Encode the end for a chain of clauses combined with OR. */
    String endOr();

    /** Encode the start for a NOT clause. */
    String startNot();

    /** Encode the end for a NOT clause. */
    String endNot();
  }

  /** A simple expression that performs a single pattern match. */
  static PatternExpr simple(PatternMatcher matcher) {
    return new Regex(matcher);
  }

  /** An expression that performs a logical AND of the listed sub-expressions. */
  static PatternExpr and(List<PatternExpr> exprs) {
    if (exprs == null)
      return null;
    int size = exprs.size();
    Preconditions.checkArg(size > 0, "exprs list cannot be empty");
    return size == 1 ? exprs.get(0) : new And(exprs);
  }

  /** An expression that performs a logical OR of the listed sub-expressions. */
  static PatternExpr or(List<PatternExpr> exprs) {
    if (exprs == null)
      return null;
    int size = exprs.size();
    Preconditions.checkArg(size > 0, "exprs list cannot be empty");
    return size == 1 ? exprs.get(0) : new Or(exprs);
  }

  /** An expression that inverts the match of the sub-expression. */
  static PatternExpr not(PatternExpr expr) {
    return new Not(expr);
  }

  final class Regex implements PatternExpr {

    private final PatternMatcher matcher;

    Regex(PatternMatcher matcher) {
      this.matcher = Preconditions.checkNotNull(matcher, "matcher");
    }

    public PatternMatcher matcher() {
      return matcher;
    }

    @Override public boolean matches(String str) {
      return matcher.matches(str);
    }

    @Override public PatternExpr ignoreCase() {
      return new Regex(matcher.ignoreCase());
    }

    @Override public String toString() {
      return "'" + matcher + "'";
    }

    @Override public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Regex)) return false;
      Regex regex = (Regex) o;
      return matcher.equals(regex.matcher);
    }

    @Override public int hashCode() {
      return Objects.hash(matcher);
    }
  }

  final class And implements PatternExpr {

    private final List<PatternExpr> exprs;

    And(List<PatternExpr> exprs) {
      this.exprs = Preconditions.checkNotNull(exprs, "exprs");
    }

    public List<PatternExpr> exprs() {
      return exprs;
    }

    @Override public boolean matches(String str) {
      for (PatternExpr expr : exprs) {
        if (!expr.matches(str)) {
          return false;
        }
      }
      return true;
    }

    @Override public PatternExpr ignoreCase() {
      return new And(exprs.stream().map(PatternExpr::ignoreCase).collect(Collectors.toList()));
    }

    @Override public String toString() {
      StringJoiner joiner = new StringJoiner(" AND ", "(", ")");
      exprs.forEach(expr -> joiner.add(expr.toString()));
      return joiner.toString();
    }

    @Override public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof And)) return false;
      And and = (And) o;
      return exprs.equals(and.exprs);
    }

    @Override public int hashCode() {
      return Objects.hash(exprs);
    }
  }

  final class Or implements PatternExpr {

    private final List<PatternExpr> exprs;

    Or(List<PatternExpr> exprs) {
      this.exprs = Preconditions.checkNotNull(exprs, "exprs");
    }

    public List<PatternExpr> exprs() {
      return exprs;
    }

    @Override public boolean matches(String str) {
      for (PatternExpr expr : exprs) {
        if (expr.matches(str)) {
          return true;
        }
      }
      return false;
    }

    @Override public PatternExpr ignoreCase() {
      return new Or(exprs.stream().map(PatternExpr::ignoreCase).collect(Collectors.toList()));
    }

    @Override public String toString() {
      StringJoiner joiner = new StringJoiner(" OR ", "(", ")");
      exprs.forEach(expr -> joiner.add(expr.toString()));
      return joiner.toString();
    }

    @Override public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Or)) return false;
      Or or = (Or) o;
      return exprs.equals(or.exprs);
    }

    @Override public int hashCode() {
      return Objects.hash(exprs);
    }
  }

  final class Not implements PatternExpr {

    private final PatternExpr expr;

    Not(PatternExpr expr) {
      this.expr = Preconditions.checkNotNull(expr, "expr");
    }

    public PatternExpr expr() {
      return expr;
    }

    @Override public boolean matches(String str) {
      return !expr.matches(str);
    }

    @Override public PatternExpr ignoreCase() {
      return new Not(expr.ignoreCase());
    }

    @Override public String toString() {
      return "NOT " + expr;
    }

    @Override public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Not)) return false;
      Not not = (Not) o;
      return expr.equals(not.expr);
    }

    @Override public int hashCode() {
      return Objects.hash(expr);
    }
  }
}
