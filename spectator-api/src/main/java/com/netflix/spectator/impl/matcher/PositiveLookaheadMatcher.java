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
 * Matcher that does a positive lookahead. If the sub-matcher matches, then it will return
 * a match, but will not advance the position.
 */
final class PositiveLookaheadMatcher implements Matcher {

  private final Matcher matcher;

  /** Create a new instance. */
  PositiveLookaheadMatcher(Matcher matcher) {
    this.matcher = matcher;
  }

  @Override
  public int matches(String str, int start, int length) {
    int pos = matcher.matches(str, start, length);
    return (pos >= 0) ? start : Constants.NO_MATCH;
  }

  @Override
  public int minLength() {
    return 0;
  }

  @Override
  public Matcher rewrite(Function<Matcher, Matcher> f) {
    return f.apply(new PositiveLookaheadMatcher(matcher.rewrite(f)));
  }

  @Override
  public Matcher rewriteEnd(Function<Matcher, Matcher> f) {
    return f.apply(new PositiveLookaheadMatcher(matcher.rewriteEnd(f)));
  }

  @Override
  public String toString() {
    return "(?=" + matcher + ")";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PositiveLookaheadMatcher that = (PositiveLookaheadMatcher) o;
    return Objects.equals(matcher, that.matcher);
  }

  @Override
  public int hashCode() {
    return Objects.hash(matcher);
  }
}
