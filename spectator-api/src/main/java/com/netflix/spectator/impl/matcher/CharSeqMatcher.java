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
package com.netflix.spectator.impl.matcher;

import java.io.Serializable;
import java.util.Objects;
import java.util.SortedSet;

/** Matcher that checks for a sequence of characters. */
final class CharSeqMatcher implements Matcher, Serializable {

  private static final long serialVersionUID = 1L;

  private final String pattern;
  private final boolean ignoreCase;

  /** Create a new instance. */
  CharSeqMatcher(char pattern) {
    this(String.valueOf(pattern));
  }

  /** Create a new instance. */
  CharSeqMatcher(String pattern) {
    this(pattern, false);
  }

  /** Create a new instance. */
  CharSeqMatcher(String pattern, boolean ignoreCase) {
    this.pattern = pattern;
    this.ignoreCase = ignoreCase;
  }

  /** Sub-string to look for within the string being checked. */
  String pattern() {
    return pattern;
  }

  @Override
  public String containedString() {
    return pattern;
  }

  @Override
  public SortedSet<String> trigrams() {
    return PatternUtils.computeTrigrams(pattern);
  }

  @Override
  public int matches(String str, int start, int length) {
    final int plength = pattern.length();
    boolean matched = ignoreCase
        ? str.regionMatches(true, start, pattern, 0, plength)
        : str.startsWith(pattern, start);
    //System.out.println(matched + "::-" + str.substring(start));
    return matched ? start + plength : Constants.NO_MATCH;
  }

  @Override
  public int minLength() {
    return pattern.length();
  }

  @Override
  public String toString() {
    return PatternUtils.escape(pattern);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    CharSeqMatcher that = (CharSeqMatcher) o;
    return ignoreCase == that.ignoreCase && Objects.equals(pattern, that.pattern);
  }

  @Override
  public int hashCode() {
    int result = 1;
    result = 31 * result + pattern.hashCode();
    result = 31 * result + Boolean.hashCode(ignoreCase);
    return result;
  }
}
