/*
 * Copyright 2014-2019 Netflix, Inc.
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

import com.netflix.spectator.impl.AsciiSet;

import java.io.Serializable;
import java.util.Objects;

/** Matcher that matches any character from a specified set. */
final class CharClassMatcher implements Matcher, Serializable {

  private static final long serialVersionUID = 1L;

  private final AsciiSet set;
  private final boolean ignoreCase;

  /**
   * Set to use when the case is ignored. Java folds the case of each element of a class
   * before applying negation or intersection, so {@code (?i)[^a]} matches neither 'a' nor
   * 'A'. That cannot be derived from {@link #set} alone, so the parser computes it.
   */
  private final AsciiSet foldedSet;

  /** Create a new instance. */
  CharClassMatcher(AsciiSet set) {
    this(set, false);
  }

  /** Create a new instance. */
  CharClassMatcher(AsciiSet set, boolean ignoreCase) {
    this(set, withOtherCase(set), ignoreCase);
  }

  /**
   * Create a new instance.
   *
   * @param set
   *     Set of characters that will match.
   * @param foldedSet
   *     Set of characters that will match if the case is ignored. It should include both
   *     cases for each letter or neither.
   * @param ignoreCase
   *     If true, then the match will ignore the case.
   */
  CharClassMatcher(AsciiSet set, AsciiSet foldedSet, boolean ignoreCase) {
    this.set = set;
    this.foldedSet = foldedSet;
    this.ignoreCase = ignoreCase;
  }

  /** Returns the set with both cases of any letter in the set. */
  static AsciiSet withOtherCase(AsciiSet set) {
    StringBuilder builder = new StringBuilder();
    for (char c = 'a'; c <= 'z'; ++c) {
      char upper = Character.toUpperCase(c);
      if (set.contains(c) || set.contains(upper)) {
        builder.append(c).append(upper);
      }
    }
    return builder.length() == 0 ? set : set.union(AsciiSet.fromPattern(builder.toString()));
  }

  /** Set of characters that will match. */
  AsciiSet set() {
    return set;
  }

  /** Set of characters that will match if the case is ignored. */
  AsciiSet foldedSet() {
    return foldedSet;
  }

  /** Instances serialized by an older version will not have the folded set. */
  private Object readResolve() {
    return foldedSet == null ? new CharClassMatcher(set, ignoreCase) : this;
  }

  /**
   * Returns true if the folded set is the set with the other case of each letter added.
   * That is the case unless the class used negation or intersection, e.g. {@code [^a]}.
   */
  boolean isSimpleFold() {
    return foldedSet.equals(withOtherCase(set));
  }

  /** Returns a matcher for this class that ignores the case. */
  CharClassMatcher withIgnoreCase() {
    return new CharClassMatcher(foldedSet, foldedSet, true);
  }

  /** Returns true if the match should ignore case. */
  boolean isIgnoreCase() {
    return ignoreCase;
  }


  /**
   * Returns true if the character is in the class. When ignoring case, the folded set
   * already includes the other case of each letter, so a single lookup is sufficient.
   */
  boolean matches(char c) {
    return ignoreCase ? foldedSet.contains(c) : set.contains(c);
  }

  @Override
  public int matches(String str, int start, int length) {
    return length > 0 && matches(str.charAt(start)) ? start + 1 : Constants.NO_MATCH;
  }

  @Override
  public int minLength() {
    return 1;
  }

  @Override
  public String toString() {
    if (set.isEmpty()) {
      // An empty class is not a valid regex. The set can be empty while the folded set
      // is not, e.g. [a&&A].
      return FalseMatcher.INSTANCE.toString();
    }
    // AsciiSet.toString only puts the dash at the start or in the middle of a range, so
    // it is a valid regex character class body once escaped.
    AsciiSet inverted = set.invert();
    String p = PatternUtils.escape(set.toString());
    String n = PatternUtils.escape(inverted.toString());
    // When parsed, the case is folded before negation. Prefer a form that keeps the
    // folded set so that ignoring the case is the same after a round trip, e.g. if
    // written as [^a], then [\x00-`b-\x7f] would not match 'a' when ignoring the case.
    boolean positiveKeepsFold = foldedSet.equals(withOtherCase(set));
    boolean negatedKeepsFold = !n.isEmpty() && foldedSet.equals(withOtherCase(inverted).invert());
    boolean negated = (positiveKeepsFold == negatedKeepsFold)
        ? !n.isEmpty() && n.length() <= p.length()
        : negatedKeepsFold;
    return "[" + (negated ? "^" + n : p) + "]";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    CharClassMatcher that = (CharClassMatcher) o;
    return ignoreCase == that.ignoreCase
        && Objects.equals(set, that.set)
        && Objects.equals(foldedSet, that.foldedSet);
  }

  @Override
  public int hashCode() {
    int result = 1;
    result = 31 * result + set.hashCode();
    result = 31 * result + foldedSet.hashCode();
    result = 31 * result + Boolean.hashCode(ignoreCase);
    return result;
  }
}
