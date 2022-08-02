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
package com.netflix.spectator.impl.matcher;

import com.netflix.spectator.impl.PatternExpr;
import com.netflix.spectator.impl.PatternMatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Internal interface for matcher implementations. Provides some additional methods
 * that are not exposed on the public {@link PatternMatcher} interface.
 */
interface Matcher extends PatternMatcher {

  /**
   * Check if a string matches the pattern.
   *
   * @param str
   *     Input string to check.
   * @param start
   *     Starting position in the string to use when checking for a match.
   * @param length
   *     Maximum length after the starting position to consider when checking for a
   *     match.
   * @return
   *     If there is a match, then the returned value will be the position in the
   *     string just after the match. Otherwise {@link Constants#NO_MATCH} will be
   *     returned.
   */
  int matches(String str, int start, int length);

  @Override
  default boolean matches(String str) {
    return matches(str, 0, str.length()) >= 0;
  }

  @Override
  default PatternMatcher ignoreCase() {
    Matcher m = rewrite(PatternUtils::ignoreCase);
    return new IgnoreCaseMatcher(m);
  }

  @Override
  default List<PatternMatcher> expandOrClauses(int max) {
    List<Matcher> ms = PatternUtils.expandOrClauses(this, max);
    if (ms == null)
      return null;
    List<PatternMatcher> results = new ArrayList<>(ms.size());
    for (Matcher m : ms) {
      results.add(Optimizer.optimize(m));
    }
    return results;
  }

  @Override
  default PatternExpr toPatternExpr(int max) {
    return PatternUtils.toPatternExpr(this, max);
  }

  @Override
  default String toSqlPattern() {
    return PatternUtils.toSqlPattern(this);
  }

  /** Cast the matcher to type {@code T}. */
  @SuppressWarnings("unchecked")
  default <T> T as() {
    return (T) this;
  }

  /**
   * Return a new matcher by recursively applying the rewrite function to all sub-matchers.
   */
  default Matcher rewrite(Function<Matcher, Matcher> f) {
    return f.apply(this);
  }

  /**
   * Return a new matcher by recursively applying the rewrite function to all sub-matchers in
   * the final position of the sequence.
   */
  default Matcher rewriteEnd(Function<Matcher, Matcher> f) {
    return f.apply(this);
  }
}
