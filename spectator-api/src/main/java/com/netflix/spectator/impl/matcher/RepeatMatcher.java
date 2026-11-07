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

import com.netflix.spectator.impl.Preconditions;

import java.io.Serializable;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Matcher for a bounded number of repetitions, {@code x{min,max}}, followed by the rest of
 * the pattern.
 *
 * <p>If greedy, then like {@link ZeroOrMoreMatcher}, it checks the rest of the pattern after
 * each repetition from max down to min, so {@code a{2,3}a} will match "aaa". If possessive, e.g.
 * {@code a{2,3}+}, then it consumes as many repetitions as possible and only checks the rest
 * of the pattern at that position, so {@code a{2,3}+a} will not match "aaa".
 */
final class RepeatMatcher implements GreedyMatcher, Serializable {

  private static final long serialVersionUID = 1L;

  private final Matcher repeated;
  private final int min;
  private final int max;
  private final int minLength;
  private final Matcher next;
  private final boolean possessive;

  /** Create a new instance for a greedy repetition. */
  RepeatMatcher(Matcher repeated, int min, int max) {
    this(repeated, min, max, TrueMatcher.INSTANCE, false);
  }

  /**
   * Create a new instance.
   *
   * @param repeated
   *     Matcher for the portion that is repeated.
   * @param min
   *     Minimum number of repetitions.
   * @param max
   *     Maximum number of repetitions.
   * @param next
   *     Matcher for the portion that follows the repetitions.
   * @param possessive
   *     If true, then as many repetitions as possible are consumed and the rest of the
   *     pattern is only checked at that position.
   */
  RepeatMatcher(Matcher repeated, int min, int max, Matcher next, boolean possessive) {
    this.repeated = Preconditions.checkNotNull(repeated, "repeated");
    this.min = min;
    this.max = max;
    this.next = Preconditions.checkNotNull(next, "next");
    this.possessive = possessive;
    // minLength is fixed for an immutable matcher; precompute it to avoid walking the
    // repeated sub-matcher on every matches() call. Use a long product and saturate so a
    // large repetition count cannot overflow to a negative value that would corrupt the
    // fast-reject/bounds arithmetic in the greedy matchers.
    long length = (long) min * repeated.minLength() + next.minLength();
    this.minLength = (int) Math.min(length, Integer.MAX_VALUE);
  }

  Matcher repeated() {
    return repeated;
  }

  int min() {
    return min;
  }

  int max() {
    return max;
  }

  /** Return the matcher for the portion that follows the repetitions. */
  Matcher next() {
    return next;
  }

  /** Returns true if the repetition is possessive. */
  boolean isPossessive() {
    return possessive;
  }

  /**
   * Instances serialized by an older version will not have the next matcher or the min
   * length. The min length is derived, so it is recomputed. A rewrite reuses matchers that
   * do not change, so it would not otherwise be recomputed.
   */
  private Object readResolve() {
    return next == null
        ? new RepeatMatcher(repeated, min, max)
        : new RepeatMatcher(repeated, min, max, next, possessive);
  }

  @Override
  public String containedString() {
    String str = min == 0 ? null : repeated.containedString();
    return str == null ? next.containedString() : str;
  }

  @Override
  public SortedSet<String> trigrams() {
    if (min == 0) {
      return next.trigrams();
    }
    SortedSet<String> ts = new TreeSet<>(repeated.trigrams());
    ts.addAll(next.trigrams());
    return ts;
  }

  @Override
  public int matches(String str, int start, int length) {
    final int end = start + length;
    if (isSingleCharClass(repeated)) {
      return matchesCharClass(repeated.as(), str, start, end);
    } else if (possessive || min == max || next instanceof TrueMatcher) {
      int pos = consume(str, start, end);
      return (pos < 0 || next instanceof TrueMatcher)
          ? pos
          : next.matches(str, pos, end - pos);
    }

    // Greedy with a range, check the rest of the pattern after each repetition
    return matchesLongestFirst(str, start, end);
  }

  /**
   * Returns true if the matcher is a class that only matches ASCII chars, so each
   * repetition is a single char.
   */
  private static boolean isSingleCharClass(Matcher matcher) {
    return matcher instanceof CharClassMatcher
        && matcher.<CharClassMatcher>as().nonAscii().isEmpty();
  }

  /**
   * Fast path for a repeated class such as {@code [0-9]{1,3}}. It is equivalent to the
   * general case, but each repetition is a single char, so the class can be checked
   * directly and the position for fewer repetitions is known. The call to
   * {@code repeated.matches} is megamorphic once many patterns have been compiled, so it
   * cannot be inlined.
   */
  private int matchesCharClass(CharClassMatcher cls, String str, int start, int end) {
    final int limit = (int) Math.min((long) start + max, end);
    int pos = start;
    while (pos < limit && cls.matches(str.charAt(pos))) {
      ++pos;
    }
    // Compare the count rather than start + min, that could overflow for a large min
    if (pos - start < min) {
      return Constants.NO_MATCH;
    } else if (next instanceof TrueMatcher) {
      return pos;
    } else if (possessive) {
      return next.matches(str, pos, end - pos);
    }
    // Greedy, try the most repetitions first. The count is at least min, so the sum
    // cannot overflow.
    final int stop = start + min;
    for (; pos >= stop; --pos) {
      int p = next.matches(str, pos, end - pos);
      if (p >= 0) {
        return p;
      }
    }
    return Constants.NO_MATCH;
  }

  /**
   * Like java, try the most repetitions first, then step back one repetition at a time. That
   * matters if the position is used by an enclosing matcher, e.g. for {@code (a{1,2}b?){2}}
   * on "aaa", the first repetition should match "aa". The same as {@link ZeroOrMoreMatcher},
   * the repeated portion does not backtrack.
   */
  private int matchesLongestFirst(String str, int start, int end) {
    int pos = start;
    int n = 0;
    // If every repetition matched the same number of chars, then the position for fewer
    // repetitions can be computed. Otherwise, e.g. for alternatives with different lengths
    // or a mix of surrogate pairs and other chars, the positions are found again if needed.
    int step = 0;
    boolean sameStep = true;
    while (n < max) {
      int p = repeated.matches(str, pos, end - pos);
      if (p == pos) {
        // Matched the empty string, so any remaining repetitions would also match at this
        // position and the minimum count is satisfiable.
        n = Math.max(n, min);
        break;
      } else if (p < pos) {
        break;
      }
      if (n == 0) {
        step = p - pos;
      } else if (p - pos != step) {
        sameStep = false;
      }
      pos = p;
      ++n;
    }
    if (n < min) {
      return Constants.NO_MATCH;
    }

    int p = next.matches(str, pos, end - pos);
    if (p >= 0 || n == min) {
      return p >= 0 ? p : Constants.NO_MATCH;
    }

    if (sameStep) {
      for (int k = n - 1; k >= min; --k) {
        pos -= step;
        p = next.matches(str, pos, end - pos);
        if (p >= 0) {
          return p;
        }
      }
      return Constants.NO_MATCH;
    }

    // Record the position after each count from min to n - 1. The repeated portion always
    // matches the same way at a given position, so this is the same as the first scan.
    int[] positions = new int[n - min];
    pos = start;
    for (int k = 0; k < n - 1; ++k) {
      if (k >= min) {
        positions[k - min] = pos;
      }
      pos = repeated.matches(str, pos, end - pos);
    }
    positions[n - 1 - min] = pos;
    for (int k = n - 1; k >= min; --k) {
      pos = positions[k - min];
      p = next.matches(str, pos, end - pos);
      if (p >= 0) {
        return p;
      }
    }
    return Constants.NO_MATCH;
  }

  /**
   * Consume as many repetitions as possible up to {@code max}. Returns the position after
   * the repetitions or {@link Constants#NO_MATCH} if there are fewer than {@code min}.
   */
  private int consume(String str, int start, int end) {
    int pos = start;
    int numMatches = 0;
    while (numMatches < max) {
      int p = repeated.matches(str, pos, end - pos);
      if (p > pos) {
        // Made forward progress, try to match additional repetitions.
        ++numMatches;
        pos = p;
      } else if (p == pos) {
        // The repeated portion matched the empty string. Any remaining repetitions would
        // also match empty without advancing, so the minimum count is satisfiable and there
        // is nothing more to consume. Return here rather than looping up to max, which may be
        // a very large count, with no forward progress. Mirrors the guard in ZeroOrMoreMatcher.
        return pos;
      } else {
        return (numMatches >= min) ? pos : Constants.NO_MATCH;
      }
    }
    return pos;
  }

  @Override
  public int minLength() {
    return minLength;
  }

  @Override
  public boolean isEndAnchored() {
    return next.isEndAnchored();
  }

  @Override
  public Matcher mergeNext(Matcher after) {
    if (after instanceof TrueMatcher) {
      return this;
    }
    Matcher m = (next instanceof TrueMatcher) ? after : SeqMatcher.create(next, after);
    return new RepeatMatcher(repeated, min, max, m, possessive);
  }

  @Override
  public Matcher rewrite(Function<Matcher, Matcher> f) {
    Matcher r = repeated.rewrite(f);
    Matcher n = next.rewrite(f);
    return f.apply(r == repeated && n == next
        ? this
        : new RepeatMatcher(r, min, max, n, possessive));
  }

  @Override
  public Matcher rewriteEnd(Function<Matcher, Matcher> f) {
    // The repeated portion is not in the final position unless it is the last repetition,
    // so only apply to this matcher and the next matcher.
    if (next instanceof TrueMatcher) {
      return f.apply(this);
    }
    Matcher n = next.rewriteEnd(f);
    return f.apply(n == next ? this : new RepeatMatcher(repeated, min, max, n, possessive));
  }

  @Override
  public String toString() {
    String count;
    if (min == max) {
      count = "{" + min + "}";
    } else if (max == Integer.MAX_VALUE && possessive) {
      count = "{" + min + ",}";
    } else {
      count = "{" + min + "," + max + "}";
    }
    return "(?:" + repeated + ")" + count + (possessive ? "+" : "")
        + (next instanceof TrueMatcher ? "" : next.toString());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    RepeatMatcher that = (RepeatMatcher) o;
    return min == that.min
        && max == that.max
        && minLength == that.minLength
        && possessive == that.possessive
        && Objects.equals(repeated, that.repeated)
        && Objects.equals(next, that.next);
  }

  @Override
  public int hashCode() {
    int result = 1;
    result = 31 * result + repeated.hashCode();
    result = 31 * result + min;
    result = 31 * result + max;
    result = 31 * result + minLength;
    result = 31 * result + next.hashCode();
    result = 31 * result + Boolean.hashCode(possessive);
    return result;
  }
}
