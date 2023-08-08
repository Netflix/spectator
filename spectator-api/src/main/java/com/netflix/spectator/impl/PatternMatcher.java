/*
 * Copyright 2014-2023 Netflix, Inc.
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

import com.netflix.spectator.impl.matcher.PatternUtils;

import java.util.Collections;
import java.util.List;

/**
 * Efficient alternative to using {@link java.util.regex.Pattern} for use cases that just
 * require basic matching. Calling the matches method should be mostly equivalent to calling
 * {@link java.util.regex.Matcher#find()}. It supports most common capabilities of the normal
 * java regular expressions. Unsupported features are:
 *
 * <ul>
 *   <li>Boundary matchers other than {@code ^} and {@code $}</li>
 *   <li>Predefined horizontal and veritical whitespace classes</li>
 *   <li>java.lang.Character classes</li>
 *   <li>Unicode classes</li>
 *   <li>Back references</li>
 *   <li>Special constructs other than lookahead</li>
 * </ul>
 *
 * <p><b>This class is an internal implementation detail only intended for use within spectator.
 * It is subject to change without notice.</b></p>
 */
public interface PatternMatcher {

  /**
   * Returns true if the passed in string matches the pattern.
   */
  boolean matches(String str);

  /**
   * Returns a fixed string prefix for the pattern if one is available. This can be used
   * with indexed data to help select a subset of values that are possible matches. If the
   * pattern does not have a fixed string prefix, then null will be returned.
   */
  default String prefix() {
    return null;
  }

  /**
   * Returns a fixed string that is contained within matching results for the pattern if one
   * is available. This can be used with indexed data to help select a subset of values that
   * are possible matches. If the pattern does not have a fixed sub-string, then null will be
   * returned.
   */
  default String containedString() {
    return null;
  }

  /**
   * The minimum possible length of a matching string. This can be used as a quick check
   * to see if there is any way a given string could match.
   */
  default int minLength() {
    return 0;
  }

  /**
   * Returns true if the pattern is anchored to the start of the string.
   */
  default boolean isStartAnchored() {
    return false;
  }

  /**
   * Returns true if the pattern is anchored to the end of the string.
   */
  default boolean isEndAnchored() {
    return false;
  }

  /**
   * Returns true if this matcher will match any string. This can be used as a quick check
   * to avoid checking for matches.
   */
  default boolean alwaysMatches() {
    return false;
  }

  /**
   * Returns true if this matcher will not match any string. This can be used as a quick check
   * to avoid checking for matches.
   */
  default boolean neverMatches() {
    return false;
  }

  /**
   * Returns true if this matcher is equivalent to performing a starts with check on the
   * prefix. This can be useful when mapping to storage that may have optimized prefix
   * matching operators.
   */
  default boolean isPrefixMatcher() {
    return false;
  }

  /**
   * Returns true if this matcher is equivalent to checking if a string contains a string.
   * This can be useful when mapping to storage that may have optimized contains matching
   * operators.
   */
  default boolean isContainsMatcher() {
    return false;
  }

  /**
   * Returns a new matcher that matches the same pattern only ignoring the case of the input
   * string. Note, character classes will be matched as is and must explicitly include both
   * cases if that is the desired matching criteria.
   */
  default PatternMatcher ignoreCase() {
    return this;
  }

  /**
   * Split OR clauses in the pattern to separate matchers. Logically the original pattern
   * will match if at least one of the patterns in the expanded list matches.
   *
   * @param max
   *     Maximum size of the expanded list. This can be used to stop early for some expressions
   *     that may expand to a really large set.
   * @return
   *     List of expanded patterns or null if this pattern cannot be expanded due to exceeding
   *     the maximum limit.
   */
  default List<PatternMatcher> expandOrClauses(int max) {
    return Collections.singletonList(this);
  }

  /**
   * Attempts to rewrite this pattern to a set of simple pattern matches that can be combined
   * with AND, OR, and NOT to have the same matching behavior as the original regex pattern.
   * This can be useful when working with data stores that have more restricted pattern matching
   * support such as RE2.
   *
   * @param max
   *     Maximum size of the expanded OR list which is needed as part of simplifying the
   *     overall expression. See {@link #expandOrClauses(int)} for more details.
   * @return
   *     Expression that represents a set of simple pattern matches, or null if it is not
   *     possible to simplify the expression.
   */
  default PatternExpr toPatternExpr(int max) {
    return null;
  }

  /**
   * Returns a pattern that can be used with a SQL LIKE clause or null if this expression
   * cannot be expressed as a SQL pattern. Can be used to more optimally map the pattern
   * to a SQL data store.
   */
  default String toSqlPattern() {
    return null;
  }

  /**
   * Compile a pattern string and return a matcher that can be used to check if string values
   * match the pattern. Pattern matchers are can be reused many times and are thread safe.
   */
  static PatternMatcher compile(String pattern) {
    return PatternUtils.compile(pattern);
  }

  /**
   * Helper function to check if a string value matches the provided pattern. Note, if matching
   * many values against the same pattern, then it is much more efficient to use
   * {@link #compile(String)} to get an instance of a matcher that can be reused.
   *
   * @param pattern
   *     Pattern to use for creating the matcher instance.
   * @param value
   *     Value to check against the pattern.
   * @return
   *     True if the pattern matches the value.
   */
  static boolean matches(String pattern, String value) {
    return compile(pattern).matches(value);
  }
}
