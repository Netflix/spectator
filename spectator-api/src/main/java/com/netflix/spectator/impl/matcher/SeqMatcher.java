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

import com.netflix.spectator.impl.PatternMatcher;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
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
  public String prefix() {
    return matchers[0].prefix();
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
    List<Matcher> ms = new ArrayList<>();
    for (Matcher m : matchers) {
      ms.add(m.rewrite(f));
    }
    return f.apply(SeqMatcher.create(ms));
  }

  @Override
  public Matcher rewriteEnd(Function<Matcher, Matcher> f) {
    List<Matcher> ms = new ArrayList<>();
    for (int i = 0; i < matchers.length - 1; ++i) {
      ms.add(matchers[i]);
    }
    ms.add(matchers[matchers.length - 1].rewriteEnd(f));
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
