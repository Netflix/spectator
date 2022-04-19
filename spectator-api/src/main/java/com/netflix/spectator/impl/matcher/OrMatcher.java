/*
 * Copyright 2014-2022 Netflix, Inc.
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
import java.util.function.Function;

/** Matcher that checks if any of the sub-matchers matchers the string. */
final class OrMatcher implements GreedyMatcher, Serializable {

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
      default: return new OrMatcher(matchers);
    }
  }

  private final Matcher[] matchers;
  private final int minLength;

  /** Create a new instance. */
  OrMatcher(Matcher... matchers) {
    this.matchers = matchers;
    int min = Integer.MAX_VALUE;
    for (PatternMatcher matcher : matchers) {
      min = Math.min(min, matcher.minLength());
    }
    this.minLength = min;
  }

  /** Return the sub-matchers. */
  List<Matcher> matchers() {
    return Arrays.asList(matchers);
  }

  @Override
  public int matches(String str, int start, int length) {
    for (Matcher matcher : matchers) {
      int pos = matcher.matches(str, start, length);
      if (pos >= 0) {
        return pos;
      }
    }
    return Constants.NO_MATCH;
  }

  @Override
  public int minLength() {
    return minLength;
  }

  @Override
  public boolean isStartAnchored() {
    boolean anchored = true;
    for (Matcher m : matchers) {
      anchored &= m.isStartAnchored();
    }
    return anchored;
  }

  @Override
  public boolean isEndAnchored() {
    boolean anchored = true;
    for (Matcher m : matchers) {
      anchored &= m.isEndAnchored();
    }
    return anchored;
  }

  @Override
  public Matcher mergeNext(Matcher after) {
    if (after instanceof TrueMatcher) {
      return this;
    }
    int n = matchers.length;
    Matcher[] ms = new Matcher[n];
    for (int i = 0; i < n; ++i) {
      ms[i] = SeqMatcher.create(matchers[i], after);
    }
    return OrMatcher.create(ms);
  }

  @Override
  public Matcher rewrite(Function<Matcher, Matcher> f) {
    int n = matchers.length;
    Matcher[] ms = new Matcher[n];
    for (int i = 0; i < n; ++i) {
      ms[i] = matchers[i].rewrite(f);
    }
    return f.apply(OrMatcher.create(ms));
  }

  @Override
  public Matcher rewriteEnd(Function<Matcher, Matcher> f) {
    List<Matcher> ms = new ArrayList<>();
    for (Matcher m : matchers) {
      ms.add(m.rewriteEnd(f));
    }
    return f.apply(OrMatcher.create(ms));
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append('(');
    for (int i = 0; i < matchers.length; ++i) {
      if (i > 0) {
        builder.append('|');
      }
      builder.append(matchers[i].toString());
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
    OrMatcher orMatcher = (OrMatcher) o;
    return minLength == orMatcher.minLength && Arrays.equals(matchers, orMatcher.matchers);
  }

  @Override
  public int hashCode() {
    int result = minLength;
    result = 31 * result + Arrays.hashCode(matchers);
    return result;
  }
}
