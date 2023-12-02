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

import com.netflix.spectator.impl.PatternMatcher;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Matcher that checks a sequence of sub-matchers.
 */
final class SeqMatcher implements Matcher, Serializable {

  private static final long serialVersionUID = 1L;

  /** Create a new instance. */
  static Matcher create(List<Matcher> matchers) {
    return create(matchers.toArray(new Matcher[] {}));
  }

  /** Create a new instance. */
  static Matcher create(Matcher... matchers) {
    switch (matchers.length) {
      case 0:  return TrueMatcher.INSTANCE;
      case 1:  return matchers[0];
      default: return new SeqMatcher(matchers);
    }
  }

  private final Matcher[] matchers;
  private final int minLength;

  /** Create a new instance. */
  private SeqMatcher(Matcher... matchers) {
    this.matchers = matchers;
    int min = 0;
    for (Matcher matcher : matchers) {
      min += matcher.minLength();
    }
    this.minLength = min;
  }

  /** Return the sub-matchers for this sequence. */
  List<Matcher> matchers() {
    return Arrays.asList(matchers);
  }

  @Override
  public int matches(String str, int start, int length) {
    final int end = start + length;
    int pos = start;
    for (int i = 0; i < matchers.length && pos >= 0; ++i) {
      pos = matchers[i].matches(str, pos, end - pos);
    }
    return pos;
  }

  @Override
  public boolean matchesAfterPrefix(String str) {
    if (matchers[0] instanceof StartsWithMatcher) {
      final int end = str.length();
      int pos = matchers[0].prefix().length();
      for (int i = 1; i < matchers.length && pos >= 0; ++i) {
        pos = matchers[i].matches(str, pos, end - pos);
      }
      return pos >= 0;
    } else {
      return matches(str);
    }
  }

  @Override
  public String prefix() {
    return matchers[0].prefix();
  }

  @Override
  public String containedString() {
    String str = null;
    for (Matcher m : matchers) {
      str = m.containedString();
      if (str != null) break;
    }
    return str;
  }

  @Override
  public SortedSet<String> trigrams() {
    SortedSet<String> ts = new TreeSet<>();
    for (Matcher m : matchers) {
      ts.addAll(m.trigrams());
    }
    return ts;
  }

  @Override
  public int minLength() {
    return minLength;
  }

  @Override
  public boolean isStartAnchored() {
    return matchers[0].isStartAnchored();
  }

  @Override
  public boolean isEndAnchored() {
    return matchers[matchers.length - 1].isEndAnchored();
  }

  @Override
  public Matcher rewrite(Function<Matcher, Matcher> f) {
    int n = matchers.length;
    Matcher[] ms = new Matcher[n];
    for (int i = 0; i < n; ++i) {
      ms[i] = matchers[i].rewrite(f);
    }
    return f.apply(SeqMatcher.create(ms));
  }

  @Override
  public Matcher rewriteEnd(Function<Matcher, Matcher> f) {
    int n = matchers.length;
    Matcher[] ms = new Matcher[n];
    System.arraycopy(matchers, 0, ms, 0, n - 1);
    ms[n - 1] = matchers[n - 1].rewriteEnd(f);
    return f.apply(SeqMatcher.create(ms));
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append('(');
    for (PatternMatcher m : matchers) {
      builder.append(m.toString());
    }
    builder.append(')');
    return builder.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SeqMatcher that = (SeqMatcher) o;
    return minLength == that.minLength && Arrays.equals(matchers, that.matchers);
  }

  @Override
  public int hashCode() {
    int result = minLength;
    result = 31 * result + Arrays.hashCode(matchers);
    return result;
  }
}
