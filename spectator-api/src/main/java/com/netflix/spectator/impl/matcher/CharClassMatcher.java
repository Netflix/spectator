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

import com.netflix.spectator.impl.AsciiSet;

import java.io.Serializable;
import java.util.Objects;

/** Matcher that matches any character from a specified set. */
final class CharClassMatcher implements Matcher, Serializable {

  private static final long serialVersionUID = 1L;

  private final AsciiSet set;
  private final boolean ignoreCase;

  /** Create a new instance. */
  CharClassMatcher(AsciiSet set) {
    this(set, false);
  }

  /** Create a new instance. */
  CharClassMatcher(AsciiSet set, boolean ignoreCase) {
    this.set = set;
    this.ignoreCase = ignoreCase;
  }

  /** Set of characters that will match. */
  AsciiSet set() {
    return set;
  }

  private char toOtherCase(char c) {
    final char amount = 'a' - 'A';
    if (c >= 'A' && c <= 'Z') {
      return (char) (c + amount);
    } else if (c >= 'a' && c <= 'z') {
      return (char) (c - amount);
    } else {
      return c;
    }
  }

  @Override
  public int matches(String str, int start, int length) {
    if (length > 0) {
      char c = str.charAt(start);
      boolean matched = ignoreCase
          ? set.contains(c) || set.contains(toOtherCase(c))
          : set.contains(c);
      return matched ? start + 1 : Constants.NO_MATCH;
    } else {
      return Constants.NO_MATCH;
    }
  }

  @Override
  public int minLength() {
    return 1;
  }

  @Override
  public String toString() {
    String p = set.toString();
    String n = set.invert().toString();
    String str = (p.length() < n.length() || n.isEmpty())
        ? PatternUtils.escape(p)
        : "^" + PatternUtils.escape(n);
    return "[" + str + "]";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    CharClassMatcher that = (CharClassMatcher) o;
    return ignoreCase == that.ignoreCase && Objects.equals(set, that.set);
  }

  @Override
  public int hashCode() {
    int result = 1;
    result = 31 * result + set.hashCode();
    result = 31 * result + Boolean.hashCode(ignoreCase);
    return result;
  }
}
