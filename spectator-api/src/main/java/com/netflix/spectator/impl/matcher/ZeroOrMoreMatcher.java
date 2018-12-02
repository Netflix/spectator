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

import com.netflix.spectator.impl.Preconditions;

import java.util.Objects;
import java.util.function.Function;

/**
 * Matcher that looks for a pattern zero or more times followed by another pattern.
 */
final class ZeroOrMoreMatcher implements GreedyMatcher {

  private final Matcher repeated;
  private final Matcher next;

  /** Create a new instance. */
  ZeroOrMoreMatcher(Matcher repeated, Matcher next) {
    this.repeated = repeated;
    this.next = Preconditions.checkNotNull(next, "next");
  }

  /** Return the matcher for the repeated portion. */
  Matcher repeated() {
    return repeated;
  }

  /** Return the matcher for the portion that follows the sub-string. */
  Matcher next() {
    return next;
  }

  @Override
  public int matches(String str, int start, int length) {
    final int end = start + length;
    if (repeated instanceof AnyMatcher) {
      final int stop = end - next.minLength();
      for (int pos = start; pos >= 0 && pos <= stop; ++pos) {
        int p = next.matches(str, pos, end - pos);
        if (p >= 0) {
          return p;
        }
      }
      return Constants.NO_MATCH;
    } else if (next != TrueMatcher.INSTANCE) {
      final int stop = end - next.minLength();
      int pos = start;
      while (pos >= 0 && pos <= stop) {
        int p = next.matches(str, pos, end - pos);
        if (p >= 0) {
          return p;
        }
        pos = repeated.matches(str, pos, end - pos);
        if (pos == start) {
          return Constants.NO_MATCH;
        }
      }
      return Constants.NO_MATCH;
    } else {
      int matchPos = Constants.NO_MATCH;
      int pos = start;
      while (pos > matchPos) {
        matchPos = pos;
        pos = repeated.matches(str, pos, end - pos);
      }
      return matchPos;
    }
  }

  @Override
  public int minLength() {
    return next.minLength();
  }

  @Override
  public boolean isEndAnchored() {
    return next.isEndAnchored();
  }

  @Override
  public boolean alwaysMatches() {
    return repeated instanceof AnyMatcher
        && (next instanceof TrueMatcher || next instanceof EndMatcher);
  }

  @Override
  public Matcher mergeNext(Matcher after) {
    if (after instanceof TrueMatcher) {
      return this;
    }
    Matcher m = (next instanceof TrueMatcher) ? after : SeqMatcher.create(next, after);
    return new ZeroOrMoreMatcher(repeated, m);
  }

  @Override
  public Matcher rewrite(Function<Matcher, Matcher> f) {
    return f.apply(new ZeroOrMoreMatcher(repeated.rewrite(f), next.rewrite(f)));
  }

  @Override
  public Matcher rewriteEnd(Function<Matcher, Matcher> f) {
    return f.apply(new ZeroOrMoreMatcher(repeated, next.rewriteEnd(f)));
  }

  @Override
  public String toString() {
    return "(" + repeated + ")*" + (next instanceof TrueMatcher ? "" : next.toString());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ZeroOrMoreMatcher that = (ZeroOrMoreMatcher) o;
    return Objects.equals(repeated, that.repeated) && Objects.equals(next, that.next);
  }

  @Override
  public int hashCode() {
    return Objects.hash(repeated, next);
  }
}
