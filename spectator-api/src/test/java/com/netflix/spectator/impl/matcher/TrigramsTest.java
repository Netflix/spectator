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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

public class TrigramsTest {

  @Test
  public void startsWith() {
    PatternMatcher m = PatternMatcher.compile("^abcd");
    Assertions.assertEquals(sortedSet("abc", "bcd"), m.trigrams());
  }

  @Test
  public void contains() {
    PatternMatcher m = PatternMatcher.compile(".*abcd");
    Assertions.assertEquals(sortedSet("abc", "bcd"), m.trigrams());
  }

  @Test
  public void endsWith() {
    PatternMatcher m = PatternMatcher.compile(".*abcd$");
    Assertions.assertEquals(sortedSet("abc", "bcd"), m.trigrams());
  }

  @Test
  public void zeroOrMore() {
    PatternMatcher m = PatternMatcher.compile("(abc)*def");
    Assertions.assertEquals(sortedSet("abc", "def"), m.trigrams());
  }

  @Test
  public void zeroOrOne() {
    PatternMatcher m = PatternMatcher.compile("(abc)?def");
    Assertions.assertEquals(sortedSet("abc", "def"), m.trigrams());
  }

  @Test
  public void oneOrMore() {
    PatternMatcher m = PatternMatcher.compile("(abc)+def");
    Assertions.assertEquals(sortedSet("abc", "def"), m.trigrams());
  }

  @Test
  public void repeat() {
    PatternMatcher m = PatternMatcher.compile("(abc){0,5}def");
    Assertions.assertEquals(sortedSet("abc", "def"), m.trigrams());
  }

  @Test
  public void partOfSequence() {
    PatternMatcher m = PatternMatcher.compile(".*[0-9]abcd[efg]hij");
    Assertions.assertEquals(sortedSet("abc", "bcd", "hij"), m.trigrams());
  }

  @Test
  public void multiple() {
    PatternMatcher m = PatternMatcher.compile("^abc.*def.*ghi");
    Assertions.assertEquals(sortedSet("abc", "def", "ghi"), m.trigrams());
  }

  @Test
  public void orClause() {
    PatternMatcher matcher = PatternMatcher.compile(".*(abc|def|ghi)");
    Assertions.assertEquals(Collections.emptySortedSet(), matcher.trigrams());

    List<PatternMatcher> ms = matcher.expandOrClauses(5);
    Assertions.assertEquals(3, ms.size());
    SortedSet<String> trigrams = new TreeSet<>();
    for (PatternMatcher m : ms) {
      SortedSet<String> ts = m.trigrams();
      Assertions.assertEquals(1, ts.size());
      trigrams.addAll(ts);
    }
    Assertions.assertEquals(sortedSet("abc", "def", "ghi"), trigrams);
  }

  private SortedSet<String> sortedSet(String... items) {
    return new TreeSet<>(Arrays.asList(items));
  }
}
