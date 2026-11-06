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

  /** Range of all non-ASCII code points, used in the regex for a class. */
  private static final String NON_ASCII_RANGE = "\\x{80}-\\x{10ffff}";

  private final AsciiSet set;
  private final boolean ignoreCase;

  /**
   * Set to use when the case is ignored. Java folds the case of each element of a class
   * before applying negation or intersection, so {@code (?i)[^a]} matches neither 'a' nor
   * 'A'. That cannot be derived from {@link #set} alone, so the parser computes it.
   */
  private final AsciiSet foldedSet;

  /**
   * True if the class matches all non-ASCII code points. Classes can only list ASCII
   * characters, so a class either matches all non-ASCII code points, e.g. {@code [^a]} or
   * {@code \S}, or none of them. Ignoring the case only folds ASCII letters, so it is the
   * same for both sets.
   */
  private final boolean nonAscii;

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
    this(set, foldedSet, ignoreCase, false);
  }

  /**
   * Create a new instance.
   *
   * @param set
   *     Set of ASCII characters that will match.
   * @param foldedSet
   *     Set of ASCII characters that will match if the case is ignored. It should include
   *     both cases for each letter or neither.
   * @param ignoreCase
   *     If true, then the match will ignore the case.
   * @param nonAscii
   *     If true, then all non-ASCII code points will match.
   */
  CharClassMatcher(AsciiSet set, AsciiSet foldedSet, boolean ignoreCase, boolean nonAscii) {
    this.set = set;
    this.foldedSet = foldedSet;
    this.ignoreCase = ignoreCase;
    this.nonAscii = nonAscii;
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

  /** Returns true if the class matches all non-ASCII code points. */
  boolean nonAscii() {
    return nonAscii;
  }

  /** Returns true if the class cannot match anything, even if the case is ignored. */
  boolean isEmpty() {
    return set.isEmpty() && foldedSet.isEmpty() && !nonAscii;
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
    return new CharClassMatcher(foldedSet, foldedSet, true, nonAscii);
  }

  /** Returns true if the match should ignore case. */
  boolean isIgnoreCase() {
    return ignoreCase;
  }


  /**
   * Returns true if the character is in the class. When ignoring case, the folded set
   * already includes the other case of each letter, so a single lookup is sufficient.
   * Both halves of a surrogate pair are non-ASCII, so checking each char of a pair gives
   * the same result as checking the code point.
   */
  boolean matches(char c) {
    if (c < 128) {
      return ignoreCase ? foldedSet.contains(c) : set.contains(c);
    }
    return nonAscii;
  }

  @Override
  public int matches(String str, int start, int length) {
    if (length <= 0) {
      return Constants.NO_MATCH;
    }
    final char c = str.charAt(start);
    if (!matches(c)) {
      return Constants.NO_MATCH;
    }
    // A surrogate pair is a single code point, so it is consumed as a whole. Only check
    // for a high surrogate, so ASCII chars do not need to read the next char.
    return Character.isHighSurrogate(c)
        ? PatternUtils.nextCodePoint(str, start, start + length)
        : start + 1;
  }

  @Override
  public int minLength() {
    return 1;
  }

  @Override
  public String toString() {
    if (set.isEmpty() && !nonAscii) {
      // An empty class is not a valid regex. The set can be empty while the folded set
      // is not, e.g. [a&&A].
      return FalseMatcher.INSTANCE.toString();
    }
    AsciiSet inverted = set.invert();
    if (nonAscii && inverted.isEmpty()) {
      return "[\\s\\S]";
    }
    // AsciiSet.toString only puts the dash at the start or in the middle of a range, so
    // it is a valid regex character class body once escaped. If the class matches the
    // non-ASCII code points, then the negated form is used, e.g. [^a], otherwise the
    // positive form, so that neither needs to list them.
    String p = PatternUtils.escape(set.toString());
    String n = PatternUtils.escape(inverted.toString());
    boolean negated = nonAscii;
    // When parsed, the case is folded before negation. If needed, use the other form so
    // that ignoring the case is the same after a round trip, e.g. [a&&[^A]] only has 'a'
    // and matches nothing when ignoring the case, but [a] would match 'a' and 'A'. The
    // other form needs the range of non-ASCII code points. That is avoided if possible as
    // some engines, e.g. RE2J, are very slow to compile a large range when ignoring case.
    boolean positiveKeepsFold = foldedSet.equals(withOtherCase(set));
    boolean negatedKeepsFold = foldedSet.equals(withOtherCase(inverted).invert());
    boolean keepsFold = negated ? negatedKeepsFold : positiveKeepsFold;
    boolean otherKeepsFold = negated ? positiveKeepsFold : negatedKeepsFold;
    if (!keepsFold && otherKeepsFold) {
      negated = !negated;
    }
    String range = (negated == nonAscii) ? "" : NON_ASCII_RANGE;
    return "[" + (negated ? "^" + n : p) + range + "]";
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
        && nonAscii == that.nonAscii
        && Objects.equals(set, that.set)
        && Objects.equals(foldedSet, that.foldedSet);
  }

  @Override
  public int hashCode() {
    int result = 1;
    result = 31 * result + set.hashCode();
    result = 31 * result + foldedSet.hashCode();
    result = 31 * result + Boolean.hashCode(ignoreCase);
    result = 31 * result + Boolean.hashCode(nonAscii);
    return result;
  }
}
