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
import java.util.Objects;

/** Matcher that ignores the case when checking against the input string. */
final class IgnoreCaseMatcher implements PatternMatcher, Serializable {

  private static final long serialVersionUID = 1L;

  private final PatternMatcher matcher;

  /**
   * Underlying matcher to use for checking the string. It should have already been converted
   * to match on the lower case version of the string.
   */
  IgnoreCaseMatcher(PatternMatcher matcher) {
    this.matcher = matcher;
  }

  @Override
  public boolean matches(String str) {
    return matcher.matches(str);
  }

  @Override
  public String toString() {
    return "(?i)" + matcher.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    IgnoreCaseMatcher that = (IgnoreCaseMatcher) o;
    return Objects.equals(matcher, that.matcher);
  }

  @Override
  public int hashCode() {
    return Objects.hash(matcher);
  }
}
