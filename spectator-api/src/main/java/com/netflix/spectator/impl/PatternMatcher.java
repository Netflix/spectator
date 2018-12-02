/*
 * Copyright 2014-2018 Netflix, Inc.
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
   * Returns a new matcher that matches the same pattern only ignoring the case of the input
   * string. Note, character classes will be matched as is and must explicitly include both
   * cases if that is the desired matching criteria.
   */
  default PatternMatcher ignoreCase() {
    return this;
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
