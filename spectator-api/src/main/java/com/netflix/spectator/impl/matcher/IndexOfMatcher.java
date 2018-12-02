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
package com.netflix.spectator.impl.matcher;

import java.util.Objects;
import java.util.function.Function;

/**
 * Matcher that looks for a given substring within the string being matched.
 */
final class IndexOfMatcher implements GreedyMatcher {

  private final String pattern;
  private final Matcher next;
  private final boolean ignoreCase;

  /** Create a new instance. */
  IndexOfMatcher(String pattern, Matcher next) {
    this(pattern, next, false);
  }

  /** Create a new instance. */
  IndexOfMatcher(String pattern, Matcher next, boolean ignoreCase) {
    this.pattern = pattern;
    this.next = next;
    this.ignoreCase = ignoreCase;
  }

  /** Sub-string to look for within the string being checked. */
  String pattern() {
    return pattern;
  }

  /** Return the matcher for the portion that follows the sub-string. */
  Matcher next() {
    return next;
  }

  private int indexOfIgnoreCase(String str, int offset) {
    final int length = pattern.length();
    final int end = (str.length() - length) + 1;
    for (int i = offset; i < end; ++i) {
      if (str.regionMatches(true, i, pattern, 0, length)) {
        return i;
      }
    }
    return -1;
  }

  private int indexOf(String str, int offset) {
    return ignoreCase ? indexOfIgnoreCase(str, offset) : str.indexOf(pattern, offset);
  }

  private boolean endsWithIgnoreCase(String str) {
    final int length = pattern.length();
    final int offset = str.length() - length;
    return str.regionMatches(true, offset, pattern, 0, length);
  }

  private boolean endsWith(String str, int offset) {
    final int remaining = str.length() - offset;
    final int length = pattern.length();
    if (remaining < length) {
      return false;
    }
    return ignoreCase ? endsWithIgnoreCase(str) : str.endsWith(pattern);
  }

  @Override
  public int matches(String str, int start, int length) {
    if (next == TrueMatcher.INSTANCE) {
      int pos = indexOf(str, start);
      return pos >= 0 ? pos + pattern.length() : Constants.NO_MATCH;
    } else if (next == EndMatcher.INSTANCE) {
      return endsWith(str, start) ? length : Constants.NO_MATCH;
    } else {
      final int end = start + length;
      final int stop = end - next.minLength();
      int pos = start;
      while (pos >= 0 && pos <= stop) {
        pos = indexOf(str, pos);
        if (pos >= 0) {
          int s = pos + pattern.length();
          int p = next.matches(str, s, end - s);
          if (p >= 0) {
            return p;
          }
          ++pos;
        }
      }
      return Constants.NO_MATCH;
    }
  }

  @Override
  public int minLength() {
    return pattern.length() + next.minLength();
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
    return new IndexOfMatcher(pattern, m);
  }

  @Override
  public Matcher rewrite(Function<Matcher, Matcher> f) {
    return f.apply(new IndexOfMatcher(pattern, next.rewrite(f)));
  }

  @Override
  public Matcher rewriteEnd(Function<Matcher, Matcher> f) {
    return f.apply(new IndexOfMatcher(pattern, next.rewriteEnd(f)));
  }

  @Override
  public String toString() {
    return ".*" + PatternUtils.escape(pattern) + next;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    IndexOfMatcher that = (IndexOfMatcher) o;
    return ignoreCase == that.ignoreCase
        && Objects.equals(pattern, that.pattern)
        && Objects.equals(next, that.next);
  }

  @Override
  public int hashCode() {
    return Objects.hash(pattern, next, ignoreCase);
  }
}
