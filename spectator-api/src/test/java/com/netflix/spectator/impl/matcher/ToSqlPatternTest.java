/*
 * Copyright 2014-2020 Netflix, Inc.
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
import java.util.List;
import java.util.stream.Collectors;

public class ToSqlPatternTest {

  @Test
  public void any() {
    String sql = PatternMatcher.compile(".*").toSqlPattern();
    Assertions.assertEquals("%", sql);
  }

  @Test
  public void single() {
    String sql = PatternMatcher.compile("^.$").toSqlPattern();
    Assertions.assertEquals("_", sql);
  }

  @Test
  public void exactSeq() {
    String sql = PatternMatcher.compile("^abc$").toSqlPattern();
    Assertions.assertEquals("abc", sql);
  }

  @Test
  public void indexOf() {
    String sql = PatternMatcher.compile(".*foo.*").toSqlPattern();
    Assertions.assertEquals("%foo%", sql);
  }

  @Test
  public void indexOfImplicit() {
    String sql = PatternMatcher.compile("foo").toSqlPattern();
    Assertions.assertEquals("%foo%", sql);
  }

  @Test
  public void startsWith() {
    String sql = PatternMatcher.compile("^foo.*").toSqlPattern();
    Assertions.assertEquals("foo%", sql);
  }

  @Test
  public void endsWith() {
    String sql = PatternMatcher.compile(".*foo$").toSqlPattern();
    Assertions.assertEquals("%foo", sql);
  }

  @Test
  public void combination() {
    String sql = PatternMatcher.compile(".*foo.*bar.baz.*").toSqlPattern();
    Assertions.assertEquals("%foo%bar_baz%", sql);
  }

  @Test
  public void escaping() {
    String sql = PatternMatcher.compile(".*foo_bar%baz.*").toSqlPattern();
    Assertions.assertEquals("%foo\\_bar\\%baz%", sql);
  }

  @Test
  public void startsWithLiteralPercent() {
    String sql = PatternMatcher.compile("^a%").toSqlPattern();
    Assertions.assertEquals("a\\%%", sql);
  }

  @Test
  public void exactLiteralPercent() {
    String sql = PatternMatcher.compile("^a%$").toSqlPattern();
    Assertions.assertEquals("a\\%", sql);
  }

  @Test
  public void startsWithLiteralBackslashPercent() {
    String sql = PatternMatcher.compile("^a\\\\%").toSqlPattern();
    Assertions.assertEquals("a\\\\\\%%", sql);
  }

  @Test
  public void startsWithWildcard() {
    String sql = PatternMatcher.compile("^a.*").toSqlPattern();
    Assertions.assertEquals("a%", sql);
  }

  @Test
  public void expandOrLiteralPercent() {
    List<String> sql = PatternMatcher.compile("^(a%|b)")
        .expandOrClauses(5)
        .stream()
        .map(PatternMatcher::toSqlPattern)
        .collect(Collectors.toList());
    Assertions.assertEquals(Arrays.asList("a\\%%", "b%"), sql);
  }

  @Test
  public void tooComplex() {
    String sql = PatternMatcher.compile(".*foo_(bar|baz).*").toSqlPattern();
    Assertions.assertNull(sql);
  }
}
