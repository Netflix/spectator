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
import java.util.SortedSet;

/**
 * Efficient alternative to using {@link java.util.regex.Pattern} for use cases that just
 * require basic matching. Calling the matches method should be mostly equivalent to calling
 * {@link java.util.regex.Matcher#find()}. It supports most common capabilities of the normal
 * java regular expressions, including character classes with negation, ranges, nested
 * classes and intersections, POSIX classes such as {@code \p{Alpha}}, alternatives,
 * repetitions, lookahead, named and non-capturing groups, and a leading {@code (?i)} flag.
 * Like java, malformed patterns throw an {@link IllegalArgumentException}, though it is not
 * a {@link java.util.regex.PatternSyntaxException}.
 *
 * <p>Differences from java:</p>
 *
 * <ul>
 *   <li>{@code $} only matches at the end of the string, the same as RE2. For java it can
 *       also match before a final line terminator, e.g. {@code a$} matches "a\n".</li>
 *   <li>{@code .} matches any char including line terminators, the same as the java
 *       {@code DOTALL} flag. For java, and RE2 for a newline, it only matches a line
 *       terminator if that flag is used, e.g. without it {@code a.b} does not match
 *       "a\nb".</li>
 *   <li>Matching ignores capturing groups and only checks if there is a match, so a
 *       reluctant quantifier, e.g. {@code a+?}, gives the same result as a greedy one.</li>
 *   <li>{@code .} and classes match a code point, so a surrogate pair such as an emoji is
 *       matched as a whole. A lone surrogate in the pattern, e.g. {@code \x{d83d}}, only
 *       matches a surrogate that is not part of a pair. Java does match a literal sequence
 *       that starts with a low surrogate at the second char of a pair if the pattern is not
 *       anchored, e.g. {@code \x{de00}a} on an emoji followed by 'a'.</li>
 *   <li>Classes are parsed the same as java 9 and later, where a negation applies to the
 *       whole class. For java 8, it does not apply to nested classes or intersections, e.g.
 *       {@code [^a[b]]} matches 'b'.</li>
 *   <li>When ignoring the case, only ASCII letters are folded, the same as the java flag
 *       without {@code UNICODE_CASE}. POSIX classes are folded the same as java 15 and
 *       later, e.g. {@code (?i)\p{Lower}} matches 'A', but not for earlier versions.</li>
 * </ul>
 *
 * <p>Unsupported features throw an {@link IllegalArgumentException} with a message that
 * starts with "unsupported:":</p>
 *
 * <ul>
 *   <li>Boundary matchers other than {@code ^} and {@code $}, e.g. {@code \b}</li>
 *   <li>Predefined horizontal and vertical whitespace classes</li>
 *   <li>Unicode classes and java.lang.Character classes, e.g. {@code \p{L}} or
 *       {@code \p{javaLowerCase}}</li>
 *   <li>Back references and lookbehind</li>
 *   <li>The escapes {@code \R}, {@code \X} and {@code \N{...}}, and control characters
 *       such as {@code \cA}</li>
 *   <li>Inline flags other than a leading {@code (?i)}, and atomic groups</li>
 *   <li>A possessive repetition of a portion that can match different lengths, e.g.
 *       {@code (ab|a)++}, or with a reluctant quantifier inside it</li>
 *   <li>A repetition or group of alternatives that can match different lengths followed by
 *       more of the pattern, if the rest of the pattern could depend on where it stops. Each
 *       repetition keeps the first match it finds rather than trying the others, so the
 *       result could differ from java. For example, {@code ^(ab|a)*c} is not supported. It
 *       is supported if each repetition can only end at one position, e.g. it consists of
 *       parts with a fixed length and repetitions of chars that cannot be followed by one of
 *       the same chars, such as {@code ^\d+(,\d+)*$}, {@code ^(\d{1,3}\.){3}\d{1,3}$} or
 *       {@code ^(\w+\.)*com$}.</li>
 *   <li>Too many ways to match chained repetitions that can match the same chars. Each
 *       repetition is tried at each position where the one before it could stop, so the
 *       time for a string that does not match grows with the length to the power of the
 *       number of repetitions. Two unbounded repetitions in a chain are supported, e.g.
 *       {@code ^\w+-?\w+$}, but not three, e.g. {@code ^\w+-?\w+-?\w+$}. Adjacent
 *       repetitions of the same chars are combined, e.g. {@code \w+\w+} is supported. A
 *       chain within a repeated group is tried again for each repetition, so only one
 *       unbounded repetition is supported in that chain, e.g. {@code ^(\w+-?\w+,)+} is not
 *       supported.</li>
 * </ul>
 *
 * <p><b>This class is an internal implementation detail only intended for use within spectator.
 * It is subject to change without notice.</b></p>
 */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface PatternMatcher {

  /**
   * Returns true if the passed in string matches the pattern.
   */
  boolean matches(String str);

  /**
   * Returns true if the passed in string matches the pattern after the prefix. This method
   * can be used to more efficiently check the value if the {@link #prefix()} was already
   * verified.
   */
  default boolean matchesAfterPrefix(String str) {
    return matches(str);
  }

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
   * Returns a set of trigrams for this pattern. A string will only match if it contains
   * all trigrams in the set. This can be useful for performing an initial filter based on
   * a trigram index. For complex expressions, expand OR clauses first (see
   * {@link #expandOrClauses(int)}) and then extract the trigrams for the individual patterns.
   */
  default SortedSet<String> trigrams() {
    return Collections.emptySortedSet();
  }

  /**
   * Returns a new matcher that matches the same pattern only ignoring the case of the input
   * string. Character classes also ignore the case. As with the Java {@code (?i)} flag, the
   * case of each element is folded before negation or intersection, so {@code [^a]} matches
   * neither 'a' nor 'A'. Like the Java flag without {@code UNICODE_CASE}, only ASCII letters
   * are folded, other characters must be equal, e.g. 'k' does not match the Kelvin sign.
   *
   * <p>The result is the same as compiling the pattern with a leading {@code (?i)}. Folding
   * the case can make more of the pattern able to match the same chars, so it can throw an
   * {@link IllegalArgumentException} for a pattern that is supported when the case is not
   * ignored, see the unsupported features for {@link PatternMatcher}.</p>
   *
   * <p>The string from {@code toString()} and {@code equals} use the pattern as optimized
   * for the case as written. If it has a class where only one case of a letter matches,
   * e.g. {@code [^a]}, then the optimizer may simplify it in a way that is only valid when
   * the case is not ignored. For example, {@code x[^a]*A?y} is simplified to
   * {@code x[^a]*y}, which no longer matches "xAy" once the case is ignored. Similarly,
   * {@code [^a&&A]} matches any char, but not 'a' or 'A' once the case is ignored, and it is
   * written as {@code [\s\S]}. So compiling the string and then ignoring the case can give a
   * different result than this method, and two matchers that are equal can differ once the
   * case is ignored. For {@link #toPatternExpr(int)}, {@link PatternExpr#ignoreCase()} on
   * the whole expression returns the expression for the pattern that ignores the case,
   * which can have a different structure. Ignoring the case for part of the expression uses
   * the same part of that expression, and throws if the structure is different. The clauses
   * from {@link #expandOrClauses(int)} are the same, ignoring the case for a clause uses the
   * clause at the same position for the pattern that ignores the case, and throws if the
   * clauses are different.</p>
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
   *
   * <p>Literal '%', '_', and '\' characters are escaped with a backslash. The query must
   * use backslash as the escape character, e.g. {@code LIKE ? ESCAPE '\'}, as that is not
   * the default for all databases.
   *
   * <p>A '.' is written as '_'. The regex matches a code point, so for values with
   * supplementary characters, such as emoji, it is only the same if '_' matches a code
   * point in the database, e.g. PostgreSQL, rather than a UTF-16 char.
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
