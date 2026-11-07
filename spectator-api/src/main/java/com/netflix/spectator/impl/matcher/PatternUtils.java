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

import com.netflix.spectator.impl.AsciiSet;
import com.netflix.spectator.impl.PatternExpr;
import com.netflix.spectator.impl.PatternMatcher;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Helper functions for working with patterns.
 *
 * <p><b>This class is an internal implementation detail only intended for use within spectator.
 * It is subject to change without notice.</b></p>
 */
public final class PatternUtils {

  /**
   * Maximum number of characters for a negative lookahead that will be inlined. Each
   * character adds a level of nesting to the rewritten pattern, so long lookaheads could
   * overflow the stack when processing the matcher or exceed the nesting limits of other
   * regex engines.
   */
  private static final int MAX_INLINE_LOOKAHEAD_LENGTH = 100;

  private PatternUtils() {
  }

  /**
   * Returns true if ignoring the case changes which characters match the given character.
   * Only ASCII letters are folded, see {@link #regionMatchesIgnoreCase(String, int, String)},
   * so for any other character only the character itself will match.
   */
  static boolean caseMatters(char c) {
    final int lower = c | 0x20;
    return lower >= 'a' && lower <= 'z';
  }

  /** Returns true if ignoring the case could change which strings match the given string. */
  static boolean caseMatters(String s) {
    final int n = s.length();
    for (int i = 0; i < n; ++i) {
      if (caseMatters(s.charAt(i))) {
        return true;
      }
    }
    return false;
  }

  /**
   * Find the first position at or after the offset where the pattern occurs in the string
   * ignoring the case of ASCII letters, or -1 if not found. The result is the same as checking
   * {@link #regionMatchesIgnoreCase(String, int, String)} for each position, but it avoids
   * most of the per-position checks:
   *
   * <ul>
   *   <li>If case does not matter for any character in the pattern, then it will use
   *   {@link String#indexOf(String, int)}.</li>
   *   <li>Otherwise, the full comparison is only done for positions where the first
   *   character matches, either as is or as the other case of an ASCII letter.</li>
   * </ul>
   */
  static int indexOfIgnoreCase(String str, String pattern, int offset) {
    final int length = pattern.length();
    final int end = str.length() - length;
    if (length == 0) {
      return offset <= end ? offset : -1;
    } else if (!caseMatters(pattern)) {
      return str.indexOf(pattern, offset);
    }

    final char first = pattern.charAt(0);
    final char other = toOtherCase(first);
    for (int i = Math.max(offset, 0); i <= end; ++i) {
      final char c = str.charAt(i);
      if ((c == first || c == other) && regionMatchesIgnoreCase(str, i, pattern)) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Compile a pattern string and return a matcher that can be used to check if string values
   * match the pattern. Pattern matchers are can be reused many times and are thread safe.
   */
  public static PatternMatcher compile(String pattern) {
    boolean ignoreCase = pattern.startsWith("(?i)");
    int offset = ignoreCase ? 4 : 0;
    Matcher m = Parser.parse(pattern, offset);
    if (ignoreCase) {
      // Rewrite before optimizing so the pattern only needs to be optimized once. The
      // optimizer rules keep the ignore case flag and see the folded classes, so the
      // result is the same as compile(p).ignoreCase(), which optimizes again after the
      // rewrite.
      m = m.rewrite(PatternUtils::ignoreCase);
    }
    // Match anywhere in the string, same as the pattern ^.*(p).*$. The matcher is wrapped
    // rather than the pattern string so the wrapper cannot mix with the syntax of the
    // pattern, e.g. for a)|(b or a trailing backslash. For an empty pattern, the optimizer
    // reduces the wrapper to a matcher that always matches.
    Matcher end = new ZeroOrMoreMatcher(AnyMatcher.INSTANCE, EndMatcher.INSTANCE);
    Matcher any = new ZeroOrMoreMatcher(AnyMatcher.INSTANCE, SeqMatcher.create(m, end));
    Matcher full = SeqMatcher.create(StartMatcher.INSTANCE, any);
    m = Optimizer.optimize(full);
    checkRepetitions(m, pattern);
    checkChainedRepetitions(m, pattern);
    if (ignoreCase) {
      return new IgnoreCaseMatcher(m);
    }
    // Some rewrites of the optimizer are only valid for the case as written if there is a
    // class where the folded set is different, e.g. [^a]*A? => [^a]*, but once the case is
    // ignored [^a] no longer matches 'A'. Keep the pattern before it was optimized so that
    // ignoring the case can fold it first, the same as the (?i) flag.
    return hasCaseSpecificClass(full) ? new CaseSensitiveMatcher(m, full) : m;
  }

  /**
   * Returns true if the matcher has a case sensitive class where the folded set is not the
   * set with the other case of each letter added, e.g. {@code [^a]}.
   */
  private static boolean hasCaseSpecificClass(Matcher matcher) {
    boolean[] found = new boolean[1];
    matcher.rewrite(m -> {
      if (m instanceof CharClassMatcher) {
        CharClassMatcher c = m.as();
        found[0] |= !c.isIgnoreCase() && !c.isSimpleFold();
      }
      return m;
    });
    return found[0];
  }

  /**
   * Check that the matcher will give the same answer as java. To keep the time to check a
   * string linear rather than exponential, the matcher does not backtrack into a part that
   * has already matched, e.g. an earlier repetition or a group of alternatives followed by
   * another group. If that part can match different lengths, the first match may not let
   * the rest of the pattern match, e.g. {@code (a|ab)+c} on "ababc". Those patterns are not
   * supported:
   *
   * <ul>
   *   <li>A repeated or optional part must always match the same length, e.g.
   *   {@code (\d\.)+}, or only have parts with a fixed length and repetitions of single
   *   chars that cannot be followed by a char they match, e.g. {@code (\d{1,3}\.){3}} or
   *   {@code \d+(,\d+)*$}. Otherwise, it is only supported if nothing follows it and at
   *   most one repetition is required, e.g. a trailing {@code (a|ab)*}, but not
   *   {@code (a|ab){2}}. A group with an empty last alternative, e.g. {@code (a|)}, is the
   *   same as an optional part, {@code a?}.</li>
   *   <li>A group of alternatives followed by another group must have alternatives with
   *   the same length, e.g. {@code (a|b)(c|cd)}. The optimizer can usually rewrite the
   *   pattern so this doesn't apply, e.g. by merging the second group into each
   *   alternative of the first.</li>
   * </ul>
   *
   * @param matcher
   *     Optimized matcher for the complete pattern.
   * @param pattern
   *     Pattern to include in the error message.
   */
  static void checkRepetitions(Matcher matcher, String pattern) {
    if (!sameAsJava(matcher, false)) {
      throw new IllegalArgumentException("unsupported: repetition or group of alternatives "
          + "that can match different lengths followed by more of the pattern\n" + pattern);
    }
  }

  /**
   * Returns true if the matcher will give the same answer as java, see
   * {@link #checkRepetitions(Matcher, String)}.
   *
   * @param matcher
   *     Matcher to check.
   * @param endUsed
   *     True if the position after the matcher is used, e.g. it is followed by more of the
   *     pattern or is the repeated portion of a repetition.
   */
  private static boolean sameAsJava(Matcher matcher, boolean endUsed) {
    if (matcher instanceof SeqMatcher) {
      List<Matcher> ms = matcher.<SeqMatcher>as().matchers();
      int last = ms.size() - 1;
      for (int i = 0; i <= last; ++i) {
        if (!sameAsJava(ms.get(i), endUsed || i < last)) {
          return false;
        }
      }
      return true;
    } else if (matcher instanceof OrMatcher) {
      // The first alternative that matches is used
      if (endUsed && !hasFixedEnd(matcher)) {
        return false;
      }
      for (Matcher m : matcher.<OrMatcher>as().matchers()) {
        if (!sameAsJava(m, endUsed)) {
          return false;
        }
      }
      return true;
    } else if (matcher instanceof ZeroOrMoreMatcher) {
      ZeroOrMoreMatcher m = matcher.as();
      if (!endUsed && m.next() instanceof TrueMatcher) {
        // Zero repetitions would match, so it always matches
        return true;
      }
      Matcher r = withoutEmptyLast(m.repeated());
      return hasKnownEnd(r, m.next(), endUsed)
          && sameAsJava(r, true)
          && sameAsJava(m.next(), endUsed);
    } else if (matcher instanceof ZeroOrOneMatcher) {
      // Checks the next portion after the repeated portion and without it, but does not
      // backtrack into the repeated portion
      ZeroOrOneMatcher m = matcher.as();
      if (!endUsed && m.next() instanceof TrueMatcher) {
        return true;
      }
      Matcher r = withoutEmptyLast(m.repeated());
      return hasKnownEnd(r, m.next(), endUsed)
          && sameAsJava(r, true)
          && sameAsJava(m.next(), endUsed);
    } else if (matcher instanceof RepeatMatcher) {
      RepeatMatcher m = matcher.as();
      if (!endUsed && m.min() <= 1 && m.next() instanceof TrueMatcher) {
        // Only the first repetition needs to match
        return m.min() == 0 || sameAsJava(m.repeated(), false);
      }
      // A possessive repetition is checked by the parser. The repetitions are only stepped
      // back to the min, so a repetition that matched a non-empty alternative is never
      // retried with the empty one, e.g. (a|){2}ab on "ab". Only with a min of 0 is that
      // the same as an optional part, see Optimizer.repeatEmptyLastAlternative.
      Matcher r = m.min() == 0 ? withoutEmptyLast(m.repeated()) : m.repeated();
      return (m.isPossessive() || hasKnownEnd(r, m.next(), endUsed))
          && sameAsJava(r, true)
          && sameAsJava(m.next(), endUsed);
    } else if (matcher instanceof IndexOfMatcher) {
      return sameAsJava(matcher.<IndexOfMatcher>as().next(), endUsed);
    } else if (matcher instanceof PositiveLookaheadMatcher) {
      // Only checks if there is a match, the position after is not used
      return sameAsJava(matcher.<PositiveLookaheadMatcher>as().matcher(), false);
    } else if (matcher instanceof NegativeLookaheadMatcher) {
      return sameAsJava(matcher.<NegativeLookaheadMatcher>as().matcher(), false);
    }
    return true;
  }

  /**
   * Returns true if the matcher fails at all positions after a position where it fails. For
   * example, {@code .*a} checks all positions after the start, so if it fails, it would also
   * fail starting from a later position. Then a portion before it that could end at several
   * positions only needs to try the first one.
   */
  static boolean failsAtLaterPositions(Matcher matcher) {
    if (matcher instanceof ZeroOrMoreMatcher) {
      return matcher.<ZeroOrMoreMatcher>as().repeated() instanceof AnyMatcher;
    } else if (matcher instanceof IndexOfSeqMatcher) {
      return !matcher.<IndexOfSeqMatcher>as().startAnchored();
    } else {
      // An index of matcher checks each match of the sub-string after the start, or only
      // the first if that is enough
      return matcher instanceof IndexOfMatcher;
    }
  }

  /**
   * Length of string used to estimate the number of positions an unbounded repetition could
   * stop at, see {@link #checkChainedRepetitions}.
   */
  private static final double TYPICAL_LENGTH = 256.0;

  /**
   * Max estimated number of ways to match the repetitions in a chain. It allows two unbounded
   * repetitions, with a few optional or bounded repetitions mixed in, but not three.
   */
  private static final double MAX_CHAIN_COST = 16.0 * TYPICAL_LENGTH * TYPICAL_LENGTH;

  /**
   * Check that there are not too many ways to match chained repetitions. A chain is a
   * sequence of repetitions where each one can match the same chars as the one before it,
   * with only chars that both can match, or optional portions, between them, e.g.
   * {@code \\w+-?\\w+}. The second repetition is tried at each position where the first could
   * stop, so for a string that does not match, the time grows with the product of the number
   * of positions where each repetition in the chain could stop. That is estimated as the
   * number of possible counts for a bounded repetition, e.g. 3 for {@code \\d{1,3}} or 2 for
   * {@code a?}, and {@link #TYPICAL_LENGTH} for an unbounded repetition. Each attempt also
   * consumes the chars for the last repetition, so that is multiplied by the number of chars
   * it could consume, even if it can only stop at one position. If the cost is more than
   * {@link #MAX_CHAIN_COST}, then the pattern is not supported. That allows at most two
   * unbounded repetitions in a chain.
   *
   * <p>A repetition of a single set of chars that cannot be followed by one of those chars,
   * e.g. {@code \\w+-}, can only stop at one position, so the chain continues with the same
   * number of ways. If the rest of the pattern always matches, e.g. a trailing {@code \\w*},
   * then the first attempt to reach it is a match and it is not counted.
   *
   * <p>Adjacent repetitions where one set of chars contains the other are simplified by the
   * optimizer, e.g. {@code \\w+\\w+} is a single repetition. A pattern that does not start
   * with {@code ^} can match at any position, so it is the same as a pattern starting with
   * {@code ^.*}.
   *
   * @param matcher
   *     Optimized matcher for the complete pattern.
   * @param pattern
   *     Pattern to include in the error message.
   */
  static void checkChainedRepetitions(Matcher matcher, String pattern) {
    double[] max = new double[1];
    chains(matcher, Collections.emptyMap(), max, true);
    if (max[0] > MAX_CHAIN_COST) {
      throw new IllegalArgumentException("unsupported: too many ways to match chained "
          + "repetitions that can match the same chars\n" + pattern);
    }
  }

  /**
   * Returns the chains that can continue after the matcher.
   *
   * @param matcher
   *     Matcher to check.
   * @param chains
   *     Chains that can continue at the start of the matcher. The key is the set of chars for
   *     the last repetition and the value is the estimated number of ways to match the chain.
   * @param max
   *     Updated with the max estimated number of ways to match a chain.
   */
  private static Map<CharSet, Double> chains(
      Matcher matcher, Map<CharSet, Double> chains, double[] max, boolean last) {
    // The elements of a sequence and the portion after a repetition are processed in a loop
    // rather than recursively, otherwise a long pattern could overflow the stack. Each entry
    // also tracks if the rest of the pattern after it always matches.
    Deque<Matcher> pending = new ArrayDeque<>();
    Deque<Boolean> pendingLast = new ArrayDeque<>();
    pending.push(matcher);
    pendingLast.push(last);
    Map<CharSet, Double> result = chains;
    while (!pending.isEmpty()) {
      Matcher m = pending.pop();
      boolean isLast = pendingLast.pop();
      if (m instanceof SeqMatcher) {
        List<Matcher> ms = m.<SeqMatcher>as().matchers();
        boolean restLast = isLast;
        for (int i = ms.size() - 1; i >= 0; --i) {
          pending.push(ms.get(i));
          pendingLast.push(restLast);
          restLast = restLast && matchesAtAnyPosition(ms.get(i));
        }
      } else if (m instanceof ZeroOrOneMatcher) {
        ZeroOrOneMatcher zm = m.as();
        result = loopChains(zm.repeated(), 0, 1, zm.next(), isLast, result, max);
        pending.push(zm.next());
        pendingLast.push(isLast);
      } else if (m instanceof ZeroOrMoreMatcher) {
        ZeroOrMoreMatcher zm = m.as();
        result = loopChains(zm.repeated(), 0, -1, zm.next(), isLast, result, max);
        pending.push(zm.next());
        pendingLast.push(isLast);
      } else if (m instanceof RepeatMatcher) {
        RepeatMatcher rm = m.as();
        if (rm.isPossessive()) {
          // Only one way to match, but it still consumes the chars for each attempt
          double work = last(rm.next(), isLast)
              ? 1.0
              : Math.min((double) rm.max() - rm.min() + 1.0, TYPICAL_LENGTH);
          result = repetitionChains(rm.repeated(), rm.min(), 1.0, work, result, max);
        } else {
          result = loopChains(rm.repeated(), rm.min(), rm.max(), rm.next(), isLast, result, max);
        }
        pending.push(rm.next());
        pendingLast.push(isLast);
      } else if (m instanceof IndexOfMatcher) {
        // Same as .* followed by the substring, but it only tries the first match of the
        // substring if that is enough, and a substring at the end is checked directly
        IndexOfMatcher im = m.as();
        Matcher next = im.next();
        boolean done = last(next, isLast) || next instanceof EndMatcher;
        boolean tries = !done && !failsAtLaterPositions(next);
        double ways = tries ? TYPICAL_LENGTH : 1.0;
        double work = done ? 1.0 : TYPICAL_LENGTH;
        result = repetitionChains(AnyMatcher.INSTANCE, 0, ways, work, result, max);
        result = filterChains(result, im.pattern(), im.isIgnoreCase());
        pending.push(next);
        pendingLast.push(isLast);
      } else {
        result = elementChains(m, result, max, isLast);
      }
    }
    return result;
  }

  /** Returns true if the portion and everything after it always matches. */
  private static boolean last(Matcher next, boolean isLast) {
    return isLast && matchesAtAnyPosition(next);
  }

  /**
   * Returns the chains that can continue after a repetition that is not possessive.
   *
   * @param repeated
   *     Repeated portion.
   * @param min
   *     Min number of repetitions.
   * @param max
   *     Max number of repetitions, or -1 if unbounded.
   * @param next
   *     Portion after the repetition.
   * @param isLast
   *     True if the rest of the pattern after the next portion always matches.
   * @param chains
   *     Chains that can continue at the start of the repetition.
   * @param maxCost
   *     Updated with the max estimated cost of matching a chain.
   */
  private static Map<CharSet, Double> loopChains(
      Matcher repeated,
      int min,
      int max,
      Matcher next,
      boolean isLast,
      Map<CharSet, Double> chains,
      double[] maxCost) {
    double range = max < 0 ? TYPICAL_LENGTH : Math.min((double) max - min + 1.0, TYPICAL_LENGTH);
    if (last(next, isLast)) {
      // If the rest always matches, then the first attempt to reach it is a match, so the
      // repetition is not tried again
      return repetitionChains(repeated, min, 1.0, 1.0, chains, maxCost);
    }
    // A repetition of a single set of chars can only stop at one position if the next portion
    // cannot start with one of those chars. Each attempt still consumes the chars.
    CharSet set = CharSet.of(repeated);
    boolean oneEnd = set != null && !firstChars(next, CharSet.ANY).overlaps(set);
    double ways = oneEnd ? 1.0 : range;
    return repetitionChains(repeated, min, ways, range, chains, maxCost);
  }

  /** Returns the chains that can continue after a matcher that is not a repetition. */
  private static Map<CharSet, Double> elementChains(
      Matcher matcher, Map<CharSet, Double> chains, double[] max, boolean isLast) {
    CharSet single = CharSet.of(matcher);
    if (single != null) {
      return filterChains(chains, single);
    } else if (matcher instanceof CharSeqMatcher) {
      CharSeqMatcher m = matcher.as();
      return filterChains(chains, m.pattern(), m.isIgnoreCase());
    } else if (matcher instanceof StartsWithMatcher) {
      StartsWithMatcher m = matcher.as();
      return filterChains(chains, m.pattern(), m.isIgnoreCase());
    } else if (matcher instanceof OrMatcher) {
      Map<CharSet, Double> result = new HashMap<>();
      for (Matcher m : matcher.<OrMatcher>as().matchers()) {
        mergeChains(result, chains(m, chains, max, isLast));
      }
      return result;
    } else if (matcher instanceof IndexOfSeqMatcher) {
      // Substrings separated by .*, each substring is only found once
      return Collections.emptyMap();
    } else if (matcher instanceof OrStartsWithMatcher) {
      return chains(matcher.<OrStartsWithMatcher>as().delegate(), chains, max, isLast);
    } else if (matcher instanceof PositiveLookaheadMatcher) {
      // Checked at the position, but does not consume any chars
      chains(matcher.<PositiveLookaheadMatcher>as().matcher(), chains, max, false);
      return chains;
    } else if (matcher instanceof NegativeLookaheadMatcher) {
      chains(matcher.<NegativeLookaheadMatcher>as().matcher(), chains, max, false);
      return chains;
    } else if (matcher instanceof TrueMatcher
        || matcher instanceof StartMatcher
        || matcher instanceof EndMatcher) {
      return chains;
    }
    return Collections.emptyMap();
  }

  /**
   * Returns true if the matcher always matches at the first position it is tried, e.g. a
   * trailing {@code -?}. A repetition followed by it does not try any other positions.
   */
  private static boolean matchesAtAnyPosition(Matcher matcher) {
    if (matcher instanceof TrueMatcher) {
      return true;
    } else if (matcher instanceof ZeroOrOneMatcher) {
      return matchesAtAnyPosition(matcher.<ZeroOrOneMatcher>as().next());
    } else if (matcher instanceof ZeroOrMoreMatcher) {
      return matchesAtAnyPosition(matcher.<ZeroOrMoreMatcher>as().next());
    } else if (matcher instanceof RepeatMatcher) {
      RepeatMatcher m = matcher.as();
      return m.min() == 0 && matchesAtAnyPosition(m.next());
    }
    return false;
  }

  /**
   * Returns the chains that can continue after a repetition, but before the portion that
   * follows it.
   *
   * @param repeated
   *     Repeated portion.
   * @param min
   *     Min number of repetitions, if 0 the chains can also continue after skipping it.
   * @param ways
   *     Estimated number of positions where the repetition could stop.
   * @param work
   *     Estimated cost of each attempt to match the repetition, at least the number of
   *     chars it could consume.
   * @param chains
   *     Chains that can continue at the start of the repetition.
   * @param max
   *     Updated with the max estimated cost of matching a chain.
   */
  private static Map<CharSet, Double> repetitionChains(
      Matcher repeated,
      int min,
      double ways,
      double work,
      Map<CharSet, Double> chains,
      double[] max) {
    CharSet set = CharSet.of(repeated);
    boolean single = set != null;
    Map<CharSet, Double> inner = Collections.emptyMap();
    if (!single) {
      // Chains continue into the repeated portion, then use all chars it could consume. The
      // repetitions consume different chars, so the ways to match within the repeated
      // portion are not multiplied by the number of repetitions.
      inner = chains(repeated, chains, max, false);
      set = CharSet.consumed(repeated);
    }
    double prev = 1.0;
    for (Map.Entry<CharSet, Double> entry : chains.entrySet()) {
      if (entry.getKey().overlaps(set)) {
        prev = Math.max(prev, entry.getValue());
      }
    }
    double cost = prev * ways;
    for (double value : inner.values()) {
      cost = Math.max(cost, value);
    }
    max[0] = Math.max(max[0], Math.max(cost, prev * work));
    Map<CharSet, Double> result = new HashMap<>(inner);
    result.put(set, cost);
    if (min == 0) {
      mergeChains(result, chains);
    } else if (single) {
      // The required chars are the same as fixed chars, so the chains that can match them
      // continue, e.g. for \w*a{1,2}b\w*, the first \w* could also consume the a and b.
      // For a repeated portion with more than one char, the inner chains continue.
      mergeChains(result, filterChains(chains, set));
    }
    return result;
  }

  /** Returns the chains that can continue after the string. */
  private static Map<CharSet, Double> filterChains(
      Map<CharSet, Double> chains, String str, boolean ignoreCase) {
    Map<CharSet, Double> result = chains;
    for (int i = 0; i < str.length() && !result.isEmpty(); i = str.offsetByCodePoints(i, 1)) {
      result = filterChains(result, CharSet.of(str.codePointAt(i), ignoreCase));
    }
    return result;
  }

  /** Returns the chains that can continue after a char from the set. */
  private static Map<CharSet, Double> filterChains(Map<CharSet, Double> chains, CharSet set) {
    Map<CharSet, Double> result = new HashMap<>();
    for (Map.Entry<CharSet, Double> entry : chains.entrySet()) {
      if (entry.getKey().overlaps(set)) {
        result.put(entry.getKey(), entry.getValue());
      }
    }
    return result;
  }

  /** Add the chains to the result, keeping the max cost for each set. */
  private static void mergeChains(Map<CharSet, Double> result, Map<CharSet, Double> chains) {
    for (Map.Entry<CharSet, Double> entry : chains.entrySet()) {
      result.merge(entry.getKey(), entry.getValue(), Math::max);
    }
  }

  /**
   * Returns true if only one of the ways the repeated portion can match from a given
   * position could let the rest of the pattern match. That is the case if it always ends at
   * the same position, or if it only has parts with a fixed length and repetitions of single
   * chars, where the char after each repetition cannot be one it matches. For example, with
   * {@code (\d{1,3}\.){3}}, the digits can only be followed by a '.', so stopping before the
   * last digit cannot help.
   *
   * @param repeated
   *     Repeated portion.
   * @param next
   *     Portion after the repetition.
   * @param endUsed
   *     True if the position after the next portion is used by an enclosing matcher.
   */
  private static boolean hasKnownEnd(Matcher repeated, Matcher next, boolean endUsed) {
    if (hasFixedEnd(repeated)) {
      return true;
    }
    // Chars that can follow one repetition, the start of another or of the next portion
    CharSet after = firstChars(next, endUsed ? CharSet.ANY : CharSet.NONE);
    CharSet follow = firstChars(repeated, after).union(after);
    return hasKnownEnd(repeated, follow);
  }

  /**
   * Returns true if the matcher has parts with a fixed length and repetitions of single
   * chars, where each repetition cannot be followed by a char it matches.
   *
   * @param matcher
   *     Matcher to check.
   * @param follow
   *     Set of chars that could follow the matcher.
   */
  private static boolean hasKnownEnd(Matcher matcher, CharSet follow) {
    if (hasFixedEnd(matcher)) {
      return true;
    } else if (matcher instanceof SeqMatcher) {
      List<Matcher> ms = matcher.<SeqMatcher>as().matchers();
      CharSet f = follow;
      for (int i = ms.size() - 1; i >= 0; --i) {
        Matcher m = ms.get(i);
        if (!hasKnownEnd(m, f)) {
          return false;
        }
        f = firstChars(m, f);
      }
      return true;
    }

    Matcher repeated;
    Matcher next;
    if (matcher instanceof ZeroOrMoreMatcher) {
      repeated = matcher.<ZeroOrMoreMatcher>as().repeated();
      next = matcher.<ZeroOrMoreMatcher>as().next();
      // It stops at the first position where the next portion matches, so that is only the
      // last position of the run of chars if the next portion cannot match before that.
      // With nothing after it, it consumes as many as possible.
      CharSet set = CharSet.of(repeated);
      CharSet nextFirst = next instanceof TrueMatcher ? follow : firstChars(next, CharSet.ANY);
      return set != null && !nextFirst.overlaps(set) && hasKnownEnd(next, follow);
    } else if (matcher instanceof ZeroOrOneMatcher) {
      repeated = matcher.<ZeroOrOneMatcher>as().repeated();
      next = matcher.<ZeroOrOneMatcher>as().next();
    } else if (matcher instanceof RepeatMatcher && !matcher.<RepeatMatcher>as().isPossessive()) {
      repeated = matcher.<RepeatMatcher>as().repeated();
      next = matcher.<RepeatMatcher>as().next();
    } else {
      return false;
    }
    // Tries the most repetitions first
    CharSet set = CharSet.of(repeated);
    return set != null
        && !firstChars(next, follow).overlaps(set)
        && hasKnownEnd(next, follow);
  }

  /**
   * Returns a set that contains the first char that could be matched by the matcher.
   *
   * @param matcher
   *     Matcher to check.
   * @param follow
   *     Set of chars that could follow the matcher, used if it can match the empty string.
   */
  private static CharSet firstChars(Matcher matcher, CharSet follow) {
    CharSet single = CharSet.of(matcher);
    if (single != null) {
      return single;
    } else if (matcher instanceof CharSeqMatcher) {
      CharSeqMatcher m = matcher.as();
      return firstChar(m.pattern(), m.isIgnoreCase(), follow);
    } else if (matcher instanceof StartsWithMatcher) {
      StartsWithMatcher m = matcher.as();
      return firstChar(m.pattern(), m.isIgnoreCase(), follow);
    } else if (matcher instanceof EndMatcher) {
      return CharSet.NONE;
    } else if (matcher instanceof TrueMatcher || matcher instanceof StartMatcher) {
      return follow;
    } else if (matcher instanceof PositiveLookaheadMatcher) {
      // Does not consume any chars, but the char at the position must be one the lookahead
      // can match. The follow set can be empty if the rest of the pattern is not needed,
      // e.g. for a trailing (?=\d), so it is not enough on its own.
      Matcher m = matcher.<PositiveLookaheadMatcher>as().matcher();
      return firstChars(m, CharSet.ANY).union(follow);
    } else if (matcher instanceof NegativeLookaheadMatcher) {
      // Can pass with any char at the position, even if the follow set is empty
      return CharSet.ANY;
    } else if (matcher instanceof SeqMatcher) {
      // Stop at the first element that cannot match empty, otherwise checking each repetition
      // in a long pattern would walk the rest of the pattern
      CharSet set = CharSet.NONE;
      for (Matcher m : matcher.<SeqMatcher>as().matchers()) {
        if (m instanceof EndMatcher) {
          return set;
        }
        set = set.union(firstChars(m, CharSet.NONE));
        if (m.minLength() > 0) {
          return set;
        }
      }
      return set.union(follow);
    } else if (matcher instanceof OrMatcher) {
      CharSet set = CharSet.NONE;
      for (Matcher m : matcher.<OrMatcher>as().matchers()) {
        set = set.union(firstChars(m, follow));
      }
      return set;
    } else if (matcher instanceof ZeroOrMoreMatcher) {
      ZeroOrMoreMatcher m = matcher.as();
      CharSet after = firstChars(m.next(), follow);
      return firstChars(m.repeated(), after).union(after);
    } else if (matcher instanceof ZeroOrOneMatcher) {
      ZeroOrOneMatcher m = matcher.as();
      CharSet after = firstChars(m.next(), follow);
      return firstChars(m.repeated(), after).union(after);
    } else if (matcher instanceof RepeatMatcher) {
      RepeatMatcher m = matcher.as();
      CharSet after = firstChars(m.next(), follow);
      CharSet first = firstChars(m.repeated(), after);
      return m.min() > 0 ? first : first.union(after);
    }
    return CharSet.ANY;
  }

  private static CharSet firstChar(String str, boolean ignoreCase, CharSet follow) {
    return str.isEmpty() ? follow : CharSet.of(str.codePointAt(0), ignoreCase);
  }

  /**
   * Returns true if every match from a given position ends at the same position, either
   * because it always matches the same length or because it must match to the end of the
   * string, e.g. {@code (a$|bc$)}.
   */
  static boolean hasFixedEnd(Matcher matcher) {
    return isFixedLength(matcher) || matcher.isEndAnchored();
  }

  /**
   * Returns the repeated portion without an empty last alternative, e.g. {@code (a|)+} is
   * the same as {@code a*}. The other alternatives are tried first, and a repetition stops
   * once the repeated portion matches the empty string, since any remaining repetitions
   * would match empty at the same position. That is not the case if the empty alternative
   * is first, e.g. {@code (|a)+} would stop before trying {@code a}.
   */
  private static Matcher withoutEmptyLast(Matcher repeated) {
    if (repeated instanceof OrMatcher) {
      List<Matcher> alts = repeated.<OrMatcher>as().matchers();
      int last = alts.size() - 1;
      if (last > 0 && alts.get(last) instanceof TrueMatcher) {
        return OrMatcher.create(alts.subList(0, last));
      }
    }
    return repeated;
  }

  private static String context(String str, int pos) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < pos; ++i) {
      builder.append(' ');
    }
    builder.append('^');
    return str + "\n" + builder.toString();
  }

  /**
   * Create an IllegalArgumentException with a message including context based
   * on the position.
   */
  static IllegalArgumentException error(String message, String str, int pos) {
    return new IllegalArgumentException(message + "\n" + context(str, pos));
  }

  /**
   * Create an IllegalArgumentException for a regex feature that is not supported by this
   * matcher, with a message including context based on the position. This is an illegal
   * argument rather than an unsupported operation: the pattern is the problem, not the
   * compile operation. The message is prefixed with {@code unsupported:} to distinguish it
   * from a malformed pattern.
   */
  static IllegalArgumentException unsupported(String message, String str, int pos) {
    return new IllegalArgumentException("unsupported: " + message + "\n" + context(str, pos));
  }

  /**
   * Replace quotations, {@code \\Q...\\E}, with the escaped text. Like java, this is done
   * before parsing, so the quoted text can be used anywhere a literal could be used, e.g.
   * {@code [a-\\Qz\\E]} is {@code [a-z]}, and an empty quotation joins the elements on
   * either side, e.g. {@code a*\\Q\\E?} is {@code a*?}. An unclosed quotation extends to
   * the end of the pattern. Other escapes are decoded by the parser.
   */
  static String removeQuotations(String str) {
    return removeQuotations(str, null);
  }

  /**
   * Replace quotations, see {@link #removeQuotations(String)}. If {@code positions} is not
   * null, then the position in the input of each char in the result is added to it, followed
   * by the length of the input for the end of the result. It is used to report errors
   * relative to the pattern as written.
   */
  static String removeQuotations(String str, IntArrayBuilder positions) {
    // The positions are always filled in, even if there are no quotations
    if (positions == null && !str.contains("\\Q")) {
      return str;
    }
    StringBuilder builder = new StringBuilder(str.length());
    int i = 0;
    while (i < str.length()) {
      char c = str.charAt(i);
      if (c != '\\' || i + 1 >= str.length()) {
        builder.append(c);
        addPositions(positions, builder, i);
        ++i;
      } else if (str.charAt(i + 1) != 'Q') {
        // Other escape, e.g. \\\\Q is an escaped backslash followed by 'Q'
        builder.append(c);
        addPositions(positions, builder, i);
        builder.append(str.charAt(i + 1));
        addPositions(positions, builder, i + 1);
        i += 2;
      } else {
        int end = str.indexOf("\\E", i + 2);
        if (end < 0) {
          end = str.length();
        }
        for (int j = i + 2; j < end; ++j) {
          appendQuoted(builder, str.charAt(j), j == i + 2);
          addPositions(positions, builder, j);
        }
        i = Math.min(end + 2, str.length());
      }
    }
    if (positions != null) {
      positions.add(str.length());
    }
    return builder.toString();
  }

  /** Add the position for each char appended to the builder that does not have one yet. */
  private static void addPositions(IntArrayBuilder positions, StringBuilder builder, int pos) {
    if (positions != null) {
      while (positions.size() < builder.length()) {
        positions.add(pos);
      }
    }
  }

  /** Simple growable array of ints. */
  static final class IntArrayBuilder {
    private int[] values = new int[16];
    private int size;

    void add(int v) {
      if (size == values.length) {
        values = Arrays.copyOf(values, size * 2);
      }
      values[size++] = v;
    }

    int size() {
      return size;
    }

    int[] toArray() {
      return Arrays.copyOf(values, size);
    }
  }

  /**
   * Append a quoted character so it is a literal for the parser. ASCII letters and digits
   * are kept as is, since escaping them would change the meaning, e.g. {@code \\d}. Like
   * java, a digit at the start of the quotation is written as a hex escape so it cannot be
   * joined with an escape or repetition before the quotation, e.g. {@code \\07\\Q7\\E} is
   * {@code \\07} followed by '7' rather than {@code \\077}. Surrogates are kept as is so
   * that a pair stays together.
   */
  private static void appendQuoted(StringBuilder builder, char c, boolean first) {
    if (Constants.DIGIT.contains(c)) {
      if (first) {
        builder.append("\\x3");
      }
    } else if (!Constants.ALPHA.contains(c) && !Character.isSurrogate(c)) {
      builder.append('\\');
    }
    builder.append(c);
  }

  /**
   * Escape a string so it will be interpreted as a literal character sequence when processed
   * as a regular expression.
   */
  @SuppressWarnings("PMD.NcssCount")
  public static String escape(String str) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < str.length(); ++i) {
      char c = str.charAt(i);
      switch (c) {
        case '\t': builder.append("\\t"); break;
        case '\n': builder.append("\\n"); break;
        case '\r': builder.append("\\r"); break;
        case '\f': builder.append("\\f"); break;
        case '\\': builder.append("\\\\"); break;
        case '^':  builder.append("\\^"); break;
        case '$':  builder.append("\\$"); break;
        case '.':  builder.append("\\."); break;
        case '|':  builder.append("\\|"); break;
        case '?':  builder.append("\\?"); break;
        case '*':  builder.append("\\*"); break;
        case '+':  builder.append("\\+"); break;
        case '[':  builder.append("\\["); break;
        case ']':  builder.append("\\]"); break;
        case '(':  builder.append("\\("); break;
        case ')':  builder.append("\\)"); break;
        case '{':  builder.append("\\{"); break;
        case '}':  builder.append("\\}"); break;
        default:
          if (c <= ' ' || (c > '~' && c <= 0xFF)) {
            // Use the hex escape, RE2 doesn't support the unicode escape. Characters
            // above 0xFF are not special, so they are kept as is.
            builder.append(String.format("\\x%02x", (int) c));
          } else {
            builder.append(c);
          }
          break;
      }
    }
    return builder.toString();
  }

  /** Convert to a matchers that ignores the case. */
  static Matcher ignoreCase(Matcher matcher) {
    if (matcher instanceof CharClassMatcher) {
      // A class for a single character, e.g. [a], is converted to a sequence first. Once
      // folded it would have both cases, [Aa], and the optimizer could not merge it with
      // adjacent literals. This matters for (?i) patterns that are rewritten before they
      // are optimized.
      Matcher seq = Optimizer.convertSingleCharClassToSeq(matcher);
      if (seq instanceof CharSeqMatcher) {
        return new CharSeqMatcher(seq.<CharSeqMatcher>as().pattern(), true);
      }
      // The folded set can be empty even if the set is not, e.g. [a&&[^A]]. The empty class
      // is kept rather than replaced with false, so it still has a min length of 1 for rules
      // such as content after the end anchor. The optimizer converts it to false.
      return matcher.<CharClassMatcher>as().withIgnoreCase();
    } else if (matcher instanceof CharSeqMatcher) {
      CharSeqMatcher m = matcher.as();
      return new CharSeqMatcher(m.pattern(), true);
    } else if (matcher instanceof IndexOfMatcher) {
      IndexOfMatcher m = matcher.as();
      return new IndexOfMatcher(m.pattern(), m.next(), true);
    } else if (matcher instanceof StartsWithMatcher) {
      StartsWithMatcher m = matcher.as();
      return new StartsWithMatcher(m.pattern(), true);
    } else {
      return matcher;
    }
  }

  /**
   * Expand OR clauses in the provided matcher up to the max limit. If expanding would exceed
   * the max, then null will be returned.
   */
  @SuppressWarnings("PMD.ReturnEmptyCollectionRatherThanNull")
  static List<Matcher> expandOrClauses(Matcher matcher, int max) {
    if (matcher instanceof OrStartsWithMatcher) {
      // Folded alternation, expand the original form so the branches are visible
      return expandOrClauses(matcher.<OrStartsWithMatcher>as().delegate(), max);
    } else if (matcher instanceof IndexOfMatcher) {
      IndexOfMatcher m = matcher.as();
      return map(
          expandOrClauses(m.next(), max),
          n -> new IndexOfMatcher(m.pattern(), n, m.isIgnoreCase()),
          max);
    } else if (matcher instanceof ZeroOrMoreMatcher) {
      ZeroOrMoreMatcher m = matcher.as();
      return map(expandOrClauses(m.next(), max), n -> new ZeroOrMoreMatcher(m.repeated(), n), max);
    } else if (matcher instanceof RepeatMatcher) {
      RepeatMatcher m = matcher.as();
      return map(
          expandOrClauses(m.next(), max),
          n -> new RepeatMatcher(m.repeated(), m.min(), m.max(), n, m.isPossessive()),
          max);
    } else if (matcher instanceof ZeroOrOneMatcher) {
      ZeroOrOneMatcher m = matcher.as();
      List<Matcher> rs = expandOrClauses(m.repeated(), max);
      List<Matcher> ns = expandOrClauses(m.next(), max);
      if (rs == null || ns == null || ns.size() * (rs.size() + 1) > max) {
        return null;
      } else if (rs.size() == 1 && ns.size() == 1) {
        return Collections.singletonList(matcher);
      } else {
        List<Matcher> results = new ArrayList<>(ns);
        for (Matcher r : rs) {
          for (Matcher n : ns) {
            results.add(new ZeroOrOneMatcher(r, n));
          }
        }
        return results;
      }
    } else if (matcher instanceof PositiveLookaheadMatcher) {
      PositiveLookaheadMatcher m = matcher.as();
      return map(expandOrClauses(m.matcher(), max), PositiveLookaheadMatcher::new, max);
    } else if (matcher instanceof SeqMatcher) {
      return expandSeq(matcher.as(), max);
    } else if (matcher instanceof OrMatcher) {
      return expandOr(matcher.as(), max);
    } else {
      return Collections.singletonList(matcher);
    }
  }

  @SuppressWarnings("PMD.ReturnEmptyCollectionRatherThanNull")
  private static List<Matcher> map(List<Matcher> ms, Function<Matcher, Matcher> f, int max) {
    if (ms == null || ms.size() > max) {
      return null;
    }
    List<Matcher> results = new ArrayList<>(ms.size());
    for (Matcher m : ms) {
      results.add(f.apply(m));
    }
    return results;
  }

  @SuppressWarnings("PMD.ReturnEmptyCollectionRatherThanNull")
  private static List<Matcher> expandSeq(SeqMatcher seqMatcher, int max) {
    List<List<Matcher>> results = new ArrayList<>();
    for (Matcher matcher : seqMatcher.matchers()) {
      if (results.isEmpty()) {
        List<Matcher> rs = expandOrClauses(matcher, max);
        if (rs == null)
          return null;
        for (Matcher m : rs) {
          List<Matcher> tmp = new ArrayList<>();
          tmp.add(m);
          results.add(tmp);
        }
      } else {
        List<Matcher> rs = expandOrClauses(matcher, max);
        if (rs == null || results.size() * rs.size() > max)
          return null;
        List<List<Matcher>> tmp = new ArrayList<>(results.size() * rs.size());
        for (List<Matcher> ms : results) {
          for (Matcher r : rs) {
            List<Matcher> seq = new ArrayList<>(ms);
            seq.add(r);
            tmp.add(seq);
          }
        }
        results = tmp;
      }
    }

    List<Matcher> tmp = new ArrayList<>(results.size());
    for (List<Matcher> ms : results) {
      tmp.add(SeqMatcher.create(ms));
    }
    return tmp;
  }

  @SuppressWarnings("PMD.ReturnEmptyCollectionRatherThanNull")
  private static List<Matcher> expandOr(OrMatcher matcher, int max) {
    List<Matcher> ms = matcher.matchers();
    if (ms.size() > max) {
      return null;
    }
    List<Matcher> results = new ArrayList<>();
    for (Matcher m : ms) {
      List<Matcher> tmp = expandOrClauses(m, max);
      if (tmp == null || results.size() + tmp.size() > max) {
        return null;
      }
      results.addAll(tmp);
    }
    return results;
  }

  static PatternExpr toPatternExpr(Matcher matcher, int max) {
    List<Matcher> matchers = expandOrClauses(matcher, max);
    if (matchers == null) {
      return null;
    } else {
      List<PatternExpr> exprs = new ArrayList<>(matchers.size());
      for (Matcher m : matchers) {
        PatternExpr expr = expandLookahead(inlineNegativeLookahead(m, false, true));
        if (expr == null) {
          // If any branch cannot be simplified, then neither can the overall expression
          return null;
        }
        exprs.add(expr);
      }
      return PatternExpr.or(exprs);
    }
  }

  /**
   * Splitting out a lookahead into a separate clause is only valid if the lookahead is at a
   * fixed position. For example, with {@code foo-(?!bar)} the clause {@code NOT '.*foo-bar'}
   * would reject {@code foo-bar foo-baz} even though the second {@code foo-} is not followed
   * by {@code bar}. If nothing needs to match after the lookahead and it is for a fixed
   * sequence of characters, then it can instead be rewritten as a pattern that matches
   * anything other than that sequence. For example, {@code (?!ab)} is the same as
   * {@code ($|[^a]|a($|[^b]))} if nothing follows.
   *
   * @param matcher
   *     Matcher to rewrite.
   * @param fixed
   *     True if the start position of the matcher is fixed, i.e. it is start anchored and
   *     preceded only by fixed length matchers.
   * @param restAny
   *     True if whatever follows the matcher will match any suffix.
   */
  private static Matcher inlineNegativeLookahead(Matcher matcher, boolean fixed, boolean restAny) {
    if (matcher instanceof IndexOfMatcher) {
      IndexOfMatcher m = matcher.as();
      Matcher next = inlineNegativeLookahead(m.next(), false, restAny);
      return next == m.next() ? m : new IndexOfMatcher(m.pattern(), next, m.isIgnoreCase());
    } else if (matcher instanceof ZeroOrMoreMatcher) {
      ZeroOrMoreMatcher m = matcher.as();
      Matcher next = inlineNegativeLookahead(m.next(), false, restAny);
      return next == m.next() ? m : new ZeroOrMoreMatcher(m.repeated(), next);
    } else if (matcher instanceof ZeroOrOneMatcher) {
      ZeroOrOneMatcher m = matcher.as();
      Matcher next = inlineNegativeLookahead(m.next(), false, restAny);
      return next == m.next() ? m : new ZeroOrOneMatcher(m.repeated(), next);
    } else if (matcher instanceof RepeatMatcher) {
      RepeatMatcher m = matcher.as();
      Matcher next = inlineNegativeLookahead(m.next(), false, restAny);
      return next == m.next()
          ? m
          : new RepeatMatcher(m.repeated(), m.min(), m.max(), next, m.isPossessive());
    } else if (matcher instanceof SeqMatcher) {
      Matcher[] ms = matcher.<SeqMatcher>as().matchers().toArray(new Matcher[0]);
      boolean changed = false;
      boolean f = fixed;
      for (int i = 0; i < ms.length; ++i) {
        Matcher m = ms[i];
        ms[i] = inlineNegativeLookahead(m, f, restAny && matchesAnySuffix(ms, i + 1));
        changed |= ms[i] != m;
        f = isFixedAfter(f, m);
      }
      return changed ? SeqMatcher.create(ms) : matcher;
    } else if (matcher instanceof NegativeLookaheadMatcher && !fixed && restAny) {
      Matcher m = negateCharSeq(matcher.<NegativeLookaheadMatcher>as().matcher());
      return m == null ? matcher : m;
    } else {
      return matcher;
    }
  }

  /**
   * Create a matcher for anything other than the sequence of characters matched by the
   * lookahead, or null if the lookahead is not a sequence of single character matchers or
   * is longer than {@link #MAX_INLINE_LOOKAHEAD_LENGTH}. It will also be null if a class in
   * the lookahead matches every code point, as the inverted class would be empty. The end of
   * the string is also allowed as the lookahead would not match.
   */
  private static Matcher negateCharSeq(Matcher matcher) {
    List<CharClassMatcher> classes = new ArrayList<>();
    if (!charClasses(matcher, classes)
        || classes.isEmpty()
        || classes.size() > MAX_INLINE_LOOKAHEAD_LENGTH) {
      return null;
    }
    Matcher tail = null;
    for (int i = classes.size() - 1; i >= 0; --i) {
      CharClassMatcher cls = classes.get(i);
      List<Matcher> alternatives = new ArrayList<>(3);
      alternatives.add(EndMatcher.INSTANCE);
      if (cls != null) {
        // The folded set is inverted as well so that if the case is ignored later, [^b]
        // will match neither 'b' nor 'B'.
        CharClassMatcher m = new CharClassMatcher(
            cls.set().invert(), cls.foldedSet().invert(), false, cls.nonAscii().invert());
        if (m.isEmpty()) {
          return null;
        }
        alternatives.add(m);
      }
      if (tail != null) {
        Matcher m = (cls == null) ? AnyMatcher.INSTANCE : cls;
        alternatives.add(SeqMatcher.create(m, tail));
      }
      tail = OrMatcher.create(alternatives);
    }
    return tail;
  }

  /**
   * Add a class for the characters at each position of the matcher. A null entry is used
   * for any character. Returns false if the matcher is not a sequence of single character
   * matchers. For ignore case, the sets will include both cases so that the inverted set
   * will be correct. The classes will not ignore case, but keep the folded set so the
   * case can be ignored later.
   */
  private static boolean charClasses(Matcher matcher, List<CharClassMatcher> classes) {
    if (matcher instanceof CharSeqMatcher) {
      CharSeqMatcher m = matcher.as();
      String pattern = m.pattern();
      for (int i = 0; i < pattern.length(); ++i) {
        char c = pattern.charAt(i);
        if (c >= 128) {
          return false;
        }
        AsciiSet set = AsciiSet.fromPattern(Character.toString(c));
        classes.add(new CharClassMatcher(
            m.isIgnoreCase() ? CharClassMatcher.withOtherCase(set) : set));
      }
      return true;
    } else if (matcher instanceof CharClassMatcher) {
      CharClassMatcher m = matcher.as();
      classes.add(m.isIgnoreCase()
          ? new CharClassMatcher(m.foldedSet(), m.foldedSet(), false, m.nonAscii())
          : m);
      return true;
    } else if (matcher instanceof AnyMatcher) {
      classes.add(null);
      return true;
    } else if (matcher instanceof SeqMatcher) {
      for (Matcher m : matcher.<SeqMatcher>as().matchers()) {
        if (!charClasses(m, classes)) {
          return false;
        }
      }
      return true;
    } else {
      return false;
    }
  }

  /** Returns true if the matchers starting at the offset will match any suffix. */
  private static boolean matchesAnySuffix(Matcher[] ms, int offset) {
    for (int i = offset; i < ms.length; ++i) {
      if (!matchesAnySuffix(ms[i])) {
        return false;
      }
    }
    return true;
  }

  private static boolean matchesAnySuffix(Matcher matcher) {
    if (matcher instanceof SeqMatcher) {
      return matchesAnySuffix(matcher.<SeqMatcher>as().matchers().toArray(new Matcher[0]), 0);
    } else {
      return matcher.alwaysMatches();
    }
  }

  /**
   * Returns true if the position after the matcher is fixed.
   *
   * @param fixed
   *     True if the position before the matcher is fixed.
   * @param matcher
   *     Matcher to check.
   */
  private static boolean isFixedAfter(boolean fixed, Matcher matcher) {
    if (matcher instanceof StartMatcher || matcher instanceof StartsWithMatcher) {
      return true;
    } else {
      return fixed && isFixedLength(matcher);
    }
  }

  /** Returns true if the matcher always matches the same number of code points. */
  static boolean isFixedLength(Matcher matcher) {
    return fixedLength(matcher) >= 0;
  }

  /**
   * Returns the number of code points the matcher always matches, or -1 if it can match
   * different lengths.
   */
  private static long fixedLength(Matcher matcher) {
    if (matcher instanceof CharSeqMatcher) {
      String pattern = matcher.<CharSeqMatcher>as().pattern();
      return pattern.codePointCount(0, pattern.length());
    } else if (matcher instanceof StartsWithMatcher) {
      String pattern = matcher.<StartsWithMatcher>as().pattern();
      return pattern.codePointCount(0, pattern.length());
    } else if (matcher instanceof CharClassMatcher || matcher instanceof AnyMatcher) {
      return 1;
    } else if (matcher instanceof TrueMatcher
        || matcher instanceof StartMatcher
        || matcher instanceof EndMatcher
        || matcher instanceof PositiveLookaheadMatcher
        || matcher instanceof NegativeLookaheadMatcher) {
      return 0;
    } else if (matcher instanceof RepeatMatcher) {
      RepeatMatcher m = matcher.as();
      long r = fixedLength(m.repeated());
      long n = fixedLength(m.next());
      return m.min() == m.max() && r >= 0 && n >= 0 ? saturatedAdd(r * m.min(), n) : -1;
    } else if (matcher instanceof SeqMatcher) {
      long length = 0;
      for (Matcher m : matcher.<SeqMatcher>as().matchers()) {
        long n = fixedLength(m);
        if (n < 0) {
          return -1;
        }
        length = saturatedAdd(length, n);
      }
      return length;
    } else if (matcher instanceof OrMatcher) {
      // Fixed if all of the alternatives have the same length
      long length = -1;
      for (Matcher m : matcher.<OrMatcher>as().matchers()) {
        long n = fixedLength(m);
        if (n < 0 || (length >= 0 && n != length)) {
          return -1;
        }
        length = n;
      }
      return length;
    } else {
      return -1;
    }
  }

  private static long saturatedAdd(long a, long b) {
    return Math.min(a + b, Integer.MAX_VALUE);
  }

  private static PatternExpr expandLookahead(Matcher matcher) {
    List<PatternExpr> exprs = new ArrayList<>();
    // 0 - positive matcher to append
    // 1 - negative matcher to append
    // 2 - remaining matcher to keep processing
    Matcher[] results = new Matcher[3];

    // Keep processing until no lookaheads remain
    removeNextLookahead(matcher, results);
    while (results[2] != null) {
      if (results[0] != null)
        exprs.add(PatternExpr.simple(results[0]));
      if (results[1] != null)
        exprs.add(PatternExpr.not(PatternExpr.simple(results[1])));
      removeNextLookahead(results[2], results);
    }

    // If the results array is all null, then something was incompatible, return null
    // to indicate this regex cannot be simplified
    if (results[0] == null && results[1] == null) {
      return null;
    }

    // Add final expression
    if (results[0] != null)
      exprs.add(PatternExpr.simple(results[0]));
    if (results[1] != null)
      exprs.add(PatternExpr.not(PatternExpr.simple(results[1])));
    return PatternExpr.and(exprs);
  }

  private static void removeNextLookahead(Matcher matcher, Matcher[] results) {
    clearAndRewriteNextLookahead(matcher, results, false, true);

    // The results are complete patterns, so they can be optimized. This should not be done
    // for the partial results when recursing, because some optimizations are only valid at
    // the start of the pattern.
    for (int i = 0; i < results.length; ++i) {
      if (results[i] != null) {
        results[i] = Optimizer.optimize(results[i]);
      }
    }
  }

  private static void clearAndRewriteNextLookahead(
      Matcher matcher, Matcher[] results, boolean fixed, boolean restAny) {
    Arrays.fill(results, null);
    rewriteNextLookahead(matcher, results, fixed, restAny);
  }

  @SuppressWarnings("PMD")
  private static void rewriteNextLookahead(
      Matcher matcher, Matcher[] results, boolean fixed, boolean restAny) {
    if (matcher instanceof IndexOfMatcher) {
      IndexOfMatcher m = matcher.as();
      rewriteNextLookahead(m.next(), results, false, restAny);
      for (int i = 0; i < results.length; ++i) {
        if (results[i] != null) {
          results[i] = new IndexOfMatcher(m.pattern(), results[i], m.isIgnoreCase());
        }
      }
    } else if (matcher instanceof SeqMatcher) {
      SeqMatcher m = matcher.as();
      Matcher[] ms = m.matchers().toArray(new Matcher[0]);
      boolean f = fixed;
      for (int i = 0; i < ms.length; ++i) {
        results[0] = null;
        rewriteNextLookahead(ms[i], results, f, restAny && matchesAnySuffix(ms, i + 1));
        f = isFixedAfter(f, ms[i]);
        if (results[2] != null) {
          // Truncated sequence with lookahead match at the end
          List<Matcher> matchers = new ArrayList<>(i + 1);
          matchers.addAll(Arrays.asList(ms).subList(0, i));
          if (results[0] != null) {
            matchers.add(results[0]);
            results[0] = SeqMatcher.create(matchers);
          } else {
            matchers.add(results[1]);
            results[1] = SeqMatcher.create(matchers);
          }

          // Matcher with lookahead removed
          ms[i] = results[2];
          results[2] = SeqMatcher.create(ms);
          break;
        } else if (results[0] == null) {
          // Indicates this entry of the sequence cannot be simplified
          return;
        } else {
          results[0] = m;
        }
      }
    } else if (matcher instanceof ZeroOrMoreMatcher) {
      ZeroOrMoreMatcher m = matcher.as();
      if (containsUnsupported(m.repeated())) {
        return;
      }
      clearAndRewriteNextLookahead(m.next(), results, false, restAny);
      for (int i = 0; i < results.length; ++i) {
        if (results[i] != null) {
          results[i] = new ZeroOrMoreMatcher(m.repeated(), results[i]);
        }
      }
    } else if (matcher instanceof ZeroOrOneMatcher) {
      ZeroOrOneMatcher m = matcher.as();
      if (containsUnsupported(m.repeated())) {
        return;
      }
      clearAndRewriteNextLookahead(m.next(), results, false, restAny);
      for (int i = 0; i < results.length; ++i) {
        if (results[i] != null) {
          results[i] = new ZeroOrOneMatcher(m.repeated(), results[i]);
        }
      }
    } else if (matcher instanceof RepeatMatcher) {
      RepeatMatcher m = matcher.as();
      if (m.isPossessive() || m.max() > 1000 || containsUnsupported(m.repeated())) {
        return;
      }
      clearAndRewriteNextLookahead(m.next(), results, false, restAny);
      for (int i = 0; i < results.length; ++i) {
        if (results[i] != null) {
          results[i] = new RepeatMatcher(m.repeated(), m.min(), m.max(), results[i], false);
        }
      }
    } else if (matcher instanceof PositiveLookaheadMatcher) {
      // X(?=Y)Z is split into XY AND XZ. If the position is not fixed, this is only valid
      // when Z will match anything, e.g. foo-(?=bar).
      PositiveLookaheadMatcher m = matcher.as();
      if (containsUnsupported(m.matcher()) || !(fixed || restAny)) {
        return;
      }
      results[0] = m.matcher();
      results[2] = TrueMatcher.INSTANCE;
    } else if (matcher instanceof NegativeLookaheadMatcher) {
      // X(?!Y)Z is split into XZ AND NOT XY. This is only valid if the position is fixed,
      // see inlineNegativeLookahead for how some other cases are handled.
      NegativeLookaheadMatcher m = matcher.as();
      if (containsUnsupported(m.matcher()) || !fixed) {
        return;
      }
      results[1] = m.matcher();
      results[2] = TrueMatcher.INSTANCE;
    } else if (!containsUnsupported(matcher)) {
      results[0] = matcher;
    }
  }

  /**
   * Returns true if the matcher contains a lookahead or a repetition with a large upper bound
   * anywhere in the tree. Some engines like RE2 have limitations on the number of repetitions,
   * treat those as failures to match to the expression.
   */
  private static boolean containsUnsupported(Matcher matcher) {
    if (matcher instanceof NegativeLookaheadMatcher) {
      return true;
    } else if (matcher instanceof PositiveLookaheadMatcher) {
      return true;
    } else if (matcher instanceof IndexOfMatcher) {
      IndexOfMatcher m = matcher.as();
      return containsUnsupported(m.next());
    } else if (matcher instanceof OrMatcher) {
      for (Matcher m : matcher.<OrMatcher>as().matchers()) {
        if (containsUnsupported(m)) {
          return true;
        }
      }
      return false;
    } else if (matcher instanceof RepeatMatcher) {
      // RE2 does not support possessive repetitions
      RepeatMatcher m = matcher.as();
      return m.isPossessive()
          || m.max() > 1000
          || containsUnsupported(m.repeated())
          || containsUnsupported(m.next());
    } else if (matcher instanceof SeqMatcher) {
      for (Matcher m : matcher.<SeqMatcher>as().matchers()) {
        if (containsUnsupported(m)) {
          return true;
        }
      }
      return false;
    } else if (matcher instanceof ZeroOrMoreMatcher) {
      ZeroOrMoreMatcher m = matcher.as();
      return containsUnsupported(m.repeated()) || containsUnsupported(m.next());
    } else if (matcher instanceof ZeroOrOneMatcher) {
      ZeroOrOneMatcher m = matcher.as();
      return containsUnsupported(m.repeated()) || containsUnsupported(m.next());
    } else {
      return false;
    }
  }

  /** Convert a matcher to a SQL pattern or return null if not possible. */
  static String toSqlPattern(Matcher matcher) {
    SqlPatternBuilder builder = new SqlPatternBuilder();
    if (toSqlPattern(builder, matcher) && builder.isValid()) {
      if (!builder.endsWithWildcard() && !builder.isEndAnchored()) {
        builder.appendWildcard('%');
      }
      return builder.toString();
    } else {
      return null;
    }
  }

  /**
   * Builder for SQL LIKE patterns that tracks whether the pattern currently ends with
   * a '%' wildcard. Literals are escaped using backslash as the escape character. LIKE
   * patterns are implicitly anchored, so the builder also tracks whether anything has
   * been appended after an end anchor, which cannot be represented.
   */
  @SuppressWarnings("PMD.AvoidStringBufferField")
  private static final class SqlPatternBuilder {

    private final StringBuilder builder = new StringBuilder();
    private boolean endsWithWildcard = false;
    private boolean endAnchored = false;
    private boolean valid = true;

    /** Append a wildcard, either '%' or '_'. */
    void appendWildcard(char c) {
      checkNotEnded();
      builder.append(c);
      endsWithWildcard = c == '%';
    }

    /** Append an escaped literal string. */
    void appendLiteral(String str) {
      for (int i = 0; i < str.length(); ++i) {
        char c = str.charAt(i);
        switch (c) {
          case '_':  builder.append("\\_");  break;
          case '%':  builder.append("\\%");  break;
          case '\\': builder.append("\\\\"); break;
          default:   builder.append(c);      break;
        }
      }
      if (!str.isEmpty()) {
        checkNotEnded();
        endsWithWildcard = false;
      }
    }

    private void checkNotEnded() {
      if (endAnchored) {
        valid = false;
      }
    }

    /** Mark the end anchor, nothing else can be appended after this point. */
    void markEnd() {
      endAnchored = true;
    }

    boolean isEmpty() {
      return builder.length() == 0;
    }

    boolean endsWithWildcard() {
      return endsWithWildcard;
    }

    /** Returns true if an end anchor has been seen. */
    boolean isEndAnchored() {
      return endAnchored;
    }

    /** Returns false if something was appended after the end anchor. */
    boolean isValid() {
      return valid;
    }

    @Override public String toString() {
      return builder.toString();
    }
  }

  private static boolean toSqlPattern(SqlPatternBuilder builder, Matcher matcher) {
    if (matcher instanceof TrueMatcher) {
      builder.appendWildcard('%');
      return true;
    } else if (matcher instanceof AnyMatcher) {
      builder.appendWildcard('_');
      return true;
    } else if (matcher instanceof StartMatcher) {
      // Start anchor is only supported at the beginning of the pattern
      return builder.isEmpty();
    } else if (matcher instanceof EndMatcher) {
      builder.markEnd();
      return true;
    } else if (matcher instanceof ZeroOrMoreMatcher) {
      ZeroOrMoreMatcher m = matcher.as();
      if (m.repeated() == AnyMatcher.INSTANCE) {
        builder.appendWildcard('%');
        return toSqlPattern(builder, m.next());
      }
    } else if (matcher instanceof SeqMatcher) {
      SeqMatcher sm = matcher.as();
      for (Matcher m : sm.matchers()) {
        if (!toSqlPattern(builder, m)) {
          return false;
        }
      }
      return true;
    } else if (matcher instanceof CharSeqMatcher) {
      // LIKE is case sensitive, so case insensitive literals cannot be represented
      CharSeqMatcher m = matcher.as();
      if (m.isIgnoreCase()) {
        return false;
      }
      builder.appendLiteral(m.pattern());
      return true;
    } else if (matcher instanceof IndexOfMatcher) {
      IndexOfMatcher m = matcher.as();
      if (m.isIgnoreCase()) {
        return false;
      }
      builder.appendWildcard('%');
      builder.appendLiteral(m.pattern());
      return toSqlPattern(builder, m.next());
    } else if (matcher instanceof StartsWithMatcher) {
      // Start anchor is only supported at the beginning of the pattern
      StartsWithMatcher m = matcher.as();
      if (!builder.isEmpty() || m.isIgnoreCase()) {
        return false;
      }
      builder.appendLiteral(m.pattern());
      return true;
    }

    return false;
  }

  /** Returns the first matcher from a sequence. */
  static Matcher head(Matcher matcher) {
    if (matcher instanceof SeqMatcher) {
      List<Matcher> ms = matcher.<SeqMatcher>as().matchers();
      return ms.get(0);
    } else {
      return matcher;
    }
  }

  /**
   * Returns all but the first matcher from a sequence or True if there is only a single
   * matcher in the sequence.
   */
  static Matcher tail(Matcher matcher) {
    if (matcher instanceof SeqMatcher) {
      List<Matcher> ms = matcher.<SeqMatcher>as().matchers();
      return SeqMatcher.create(ms.subList(1, ms.size()));
    } else {
      return TrueMatcher.INSTANCE;
    }
  }

  /**
   * Get the prefix matcher. This is similar to {@link #head(Matcher)} except that it can
   * reach into character sequences as well as higher level sequences.
   */
  static Matcher getPrefix(Matcher matcher) {
    if (matcher instanceof SeqMatcher) {
      List<Matcher> ms = matcher.<SeqMatcher>as().matchers();
      return ms.get(0);
    } else if (matcher instanceof ZeroOrMoreMatcher) {
      ZeroOrMoreMatcher zm = matcher.as();
      return new ZeroOrMoreMatcher(zm.repeated(), TrueMatcher.INSTANCE);
    } else if (matcher instanceof CharSeqMatcher) {
      CharSeqMatcher m = matcher.as();
      String pattern = m.pattern();
      int n = firstCodePointLength(pattern);
      return n == 0 ? null : new CharSeqMatcher(pattern.substring(0, n), m.isIgnoreCase());
    } else {
      return matcher;
    }
  }

  /**
   * Get the suffix matcher. This is similar to {@link #tail(Matcher)} except that it intended
   * to be used with {@link #getPrefix(Matcher)}
   */
  static Matcher getSuffix(Matcher matcher) {
    if (matcher instanceof SeqMatcher) {
      List<Matcher> ms = matcher.<SeqMatcher>as().matchers();
      return SeqMatcher.create(ms.subList(1, ms.size()));
    } else if (matcher instanceof ZeroOrMoreMatcher) {
      ZeroOrMoreMatcher zm = matcher.as();
      return zm.next();
    } else if (matcher instanceof CharSeqMatcher) {
      CharSeqMatcher m = matcher.as();
      String pattern = m.pattern();
      int n = firstCodePointLength(pattern);
      return pattern.length() <= n
          ? TrueMatcher.INSTANCE
          : new CharSeqMatcher(pattern.substring(n), m.isIgnoreCase());
    } else {
      return TrueMatcher.INSTANCE;
    }
  }

  /**
   * Length of the first code point, or 0 for an empty string. The prefix of a character
   * sequence is the first code point rather than the first char, so a surrogate pair is not
   * split, e.g. for an emoji. A lone surrogate would not be valid for other regex engines.
   */
  private static int firstCodePointLength(String str) {
    return str.isEmpty() ? 0 : str.offsetByCodePoints(0, 1);
  }

  /**
   * Returns true if the string has the pattern at the offset, ignoring the case of ASCII
   * letters. Like java.util.regex with CASE_INSENSITIVE, other characters must be equal,
   * e.g. 'k' does not match the Kelvin sign. This is also faster than
   * {@link String#regionMatches(boolean, int, String, int, int)}, which uses the Unicode
   * case tables for each char that differs.
   */
  static boolean regionMatchesIgnoreCase(String str, int offset, String pattern) {
    final int length = pattern.length();
    if (offset < 0 || offset > str.length() - length) {
      return false;
    }
    for (int i = 0; i < length; ++i) {
      if (!equalsIgnoreCase(str.charAt(offset + i), pattern.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns true if the chars are equal ignoring the case of ASCII letters. Setting the 0x20
   * bit maps an upper case ASCII letter to lower case, so if both are the same letter after
   * that, then they only differ by case.
   */
  private static boolean equalsIgnoreCase(char a, char b) {
    if (a == b) {
      return true;
    }
    final int c = a | 0x20;
    return c == (b | 0x20) && c >= 'a' && c <= 'z';
  }

  /** Returns the other case of an ASCII letter, or the char itself if it is not one. */
  static char toOtherCase(char c) {
    final int folded = c | 0x20;
    return folded >= 'a' && folded <= 'z' ? (char) (c ^ 0x20) : c;
  }

  /**
   * Compute the set of trigrams for an input string. Each trigram is three code points, so
   * a surrogate pair is never split and a trigram with supplementary characters, such as
   * emoji, will be longer than three chars. An index of the values should be built with
   * this method so that the trigrams are consistent.
   */
  public static SortedSet<String> computeTrigrams(String input) {
    final int length = input.length();
    if (length < 3) {
      return Collections.emptySortedSet();
    }
    // The indices a, b, and c are the starts of the three code points of the current
    // trigram. The length is at least 3, so b is always before the end.
    SortedSet<String> trigrams = new TreeSet<>();
    int a = 0;
    int b = nextCodePoint(input, a, length);
    int c = nextCodePoint(input, b, length);
    while (c < length) {
      int d = nextCodePoint(input, c, length);
      trigrams.add(input.substring(a, d));
      a = b;
      b = c;
      c = d;
    }
    return trigrams;
  }

  /**
   * Returns the index after the code point that starts at {@code i}. Like java, a surrogate
   * pair is a single code point, so it is consumed as a whole if it ends before {@code end}.
   * Any other char, including a lone surrogate, is a single code point.
   */
  static int nextCodePoint(String str, int i, int end) {
    return i + 1 < end
        && Character.isHighSurrogate(str.charAt(i))
        && Character.isLowSurrogate(str.charAt(i + 1))
        ? i + 2
        : i + 1;
  }
}
