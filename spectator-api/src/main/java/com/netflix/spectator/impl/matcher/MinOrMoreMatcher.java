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
 * Matcher for an unbounded repetition with a min count, {@code x+} or {@code x{n,}},
 * followed by another pattern. It matches the same as {@code x{n}} followed by {@code x*},
 * but the repeated portion is only referenced once.
 *
 * <p>Use {@link #create(Matcher, int, Matcher)}. If the repeated portion is small, then it
 * uses {@code x{n}x*} so the optimizer rules for {@link ZeroOrMoreMatcher} apply, e.g. for
 * {@code [a-z]+}. Otherwise, a nested repetition such as {@code ((a+b)+c)+} would double the
 * size of the matcher for each level. The repeated portion can get smaller when optimized,
 * e.g. a literal is parsed as a sequence of chars, so the optimizer uses {@code x{n}x*} if
 * it is small by then.
 */
final class MinOrMoreMatcher implements GreedyMatcher, Serializable {

  private static final long serialVersionUID = 1L;

  /**
   * Max number of matchers in the repeated portion for it to be used twice as {@code x{n}x*}.
   * Each level of nesting doubles the size, so it stops doubling once the size is more than
   * this. Most repeated portions are small, e.g. a class or a short literal.
   */
  static final int MAX_EXPANDED_SIZE = 16;

  /**
   * Create a matcher for {@code repeated{min,}} followed by {@code next}.
   *
   * @param repeated
   *     Matcher for the portion that is repeated.
   * @param min
   *     Minimum number of repetitions. If 0, then it is the same as {@code repeated*}.
   * @param next
   *     Matcher for the portion that follows the repetitions.
   */
  static Matcher create(Matcher repeated, int min, Matcher next) {
    if (min == 0) {
      return new ZeroOrMoreMatcher(repeated, next);
    } else if (Optimizer.size(repeated) > MAX_EXPANDED_SIZE && CharSet.of(repeated) == null) {
      // A single set of chars, e.g. (?:a|b|...), cannot have a nested repetition, so it is
      // always copied so the rules for repeated chars apply
      return new MinOrMoreMatcher(repeated, min, next);
    }
    Matcher prefix = (min == 1) ? repeated : new RepeatMatcher(repeated, min, min);
    return SeqMatcher.create(prefix, new ZeroOrMoreMatcher(repeated, next));
  }

  private final Matcher repeated;
  private final int min;
  private final Matcher next;
  private final int minLength;

  /** Create a new instance, see {@link #create(Matcher, int, Matcher)}. */
  MinOrMoreMatcher(Matcher repeated, int min, Matcher next) {
    Preconditions.checkArg(min >= 1, "min must be at least 1");
    this.repeated = Preconditions.checkNotNull(repeated, "repeated");
    this.min = min;
    this.next = Preconditions.checkNotNull(next, "next");
    // Saturate so a large repetition count cannot overflow to a negative value
    long length = (long) min * repeated.minLength() + next.minLength();
    this.minLength = (int) Math.min(length, Integer.MAX_VALUE);
  }

  /** Return the matcher for the repeated portion. */
  Matcher repeated() {
    return repeated;
  }

  /** Return the minimum number of repetitions. */
  int min() {
    return min;
  }

  /** Return the matcher for the portion that follows the repetitions. */
  Matcher next() {
    return next;
  }

  @Override
  public String containedString() {
    String str = repeated.containedString();
    return str == null ? next.containedString() : str;
  }

  @Override
  public SortedSet<String> trigrams() {
    SortedSet<String> ts = new TreeSet<>(repeated.trigrams());
    ts.addAll(next.trigrams());
    return ts;
  }

  @Override
  public int matches(String str, int start, int length) {
    final int end = start + length;

    // The min repetitions, each keeps its first match. Same as x{n} followed by x*.
    int pos = start;
    for (int i = 0; i < min; ++i) {
      int p = repeated.matches(str, pos, end - pos);
      if (p < pos) {
        return Constants.NO_MATCH;
      } else if (p == pos) {
        // Matched the empty string, so the remaining repetitions would also match here
        break;
      }
      pos = p;
    }

    // Additional repetitions, same as ZeroOrMoreMatcher
    final int loopStart = pos;
    if (next != TrueMatcher.INSTANCE) {
      final int stop = end - next.minLength();
      while (pos >= loopStart && pos <= stop) {
        int p = next.matches(str, pos, end - pos);
        if (p >= loopStart) {
          return p;
        }
        // The repeated portion can match the empty string, ensure the position moves
        // forward to avoid an endless loop
        int tmp = repeated.matches(str, pos, end - pos);
        if (tmp == pos) {
          return Constants.NO_MATCH;
        }
        pos = tmp;
      }
      return Constants.NO_MATCH;
    } else {
      int matchPos = Constants.NO_MATCH;
      while (pos > matchPos) {
        matchPos = pos;
        pos = repeated.matches(str, pos, end - pos);
      }
      return matchPos;
    }
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
    return new MinOrMoreMatcher(repeated, min, m);
  }

  @Override
  public Matcher rewrite(Function<Matcher, Matcher> f) {
    Matcher r = repeated.rewrite(f);
    Matcher n = next.rewrite(f);
    return f.apply(r == repeated && n == next ? this : new MinOrMoreMatcher(r, min, n));
  }

  @Override
  public Matcher rewriteEnd(Function<Matcher, Matcher> f) {
    Matcher n = next.rewriteEnd(f);
    return f.apply(n == next ? this : new MinOrMoreMatcher(repeated, min, n));
  }

  @Override
  public String toString() {
    String count = (min == 1) ? "+" : "{" + min + ",}";
    return "(" + repeated + ")" + count + (next instanceof TrueMatcher ? "" : next.toString());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    MinOrMoreMatcher that = (MinOrMoreMatcher) o;
    return min == that.min
        && minLength == that.minLength
        && Objects.equals(repeated, that.repeated)
        && Objects.equals(next, that.next);
  }

  @Override
  public int hashCode() {
    int result = 1;
    result = 31 * result + repeated.hashCode();
    result = 31 * result + min;
    result = 31 * result + next.hashCode();
    result = 31 * result + minLength;
    return result;
  }
}
