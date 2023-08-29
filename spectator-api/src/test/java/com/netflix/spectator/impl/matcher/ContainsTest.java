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

public class ContainsTest {

  private PatternMatcher re(String pattern) {
    return PatternMatcher.compile(pattern);
  }

  private void assertStartsWith(String pattern) {
    PatternMatcher m = re(pattern);
    Assertions.assertTrue(m.isPrefixMatcher(), pattern);
    Assertions.assertTrue(m.isContainsMatcher(), pattern);
  }

  private void assertContainsOnly(String pattern) {
    PatternMatcher m = re(pattern);
    Assertions.assertFalse(m.isPrefixMatcher(), pattern);
    Assertions.assertTrue(m.isContainsMatcher(), pattern);
  }

  @Test
  public void startsWith() {
    assertStartsWith("^abc");
    assertStartsWith("^abc[.]def");
    assertStartsWith("^abc\\.def");
  }

  @Test
  public void notStartsWith() {
    Assertions.assertFalse(re("abc").isPrefixMatcher());
    Assertions.assertFalse(re("ab[cd]").isPrefixMatcher());
    Assertions.assertFalse(re("^abc.def").isPrefixMatcher());
  }

  @Test
  public void contains() {
    assertContainsOnly("abc");
    assertContainsOnly(".*abc");
    assertContainsOnly("abc\\.def");
  }

  @Test
  public void notContains() {
    Assertions.assertFalse(re("ab[cd]").isContainsMatcher());
    Assertions.assertFalse(re("abc.def").isContainsMatcher());
  }

  @Test
  public void containedString() {
    Assertions.assertEquals("abc", re("abc").containedString());
    Assertions.assertEquals("abc", re(".*abc").containedString());
    Assertions.assertEquals("abc", re(".*abc$").containedString());
    Assertions.assertEquals("ab", re(".*ab[cd]").containedString());
    Assertions.assertEquals("abc", re("abc.def").containedString());
    Assertions.assertEquals("abc.def", re("abc\\.def").containedString());
    Assertions.assertEquals("abc", re("^abc.def").containedString());
    Assertions.assertEquals("abc.def", re("^abc\\.def").containedString());
  }

  @Test
  public void containsZeroOrMore() {
    Assertions.assertEquals("def", re("(abc)*def").containedString());
  }

  @Test
  public void containsZeroOrOne() {
    Assertions.assertEquals("def", re("(abc)?def").containedString());
  }

  @Test
  public void containsOneOrMore() {
    Assertions.assertEquals("abc", re("(abc)+def").containedString());
  }

  @Test
  public void containsRepeat() {
    Assertions.assertEquals("def", re("(abc){0,5}def").containedString());
  }

  @Test
  public void containsRepeatAtLeastOne() {
    Assertions.assertEquals("abc", re("(abc){1,5}def").containedString());
  }

  @Test
  public void containsPartOfSequence() {
    Assertions.assertEquals("abcd", re(".*[0-9]abcd[efg]hij").containedString());
  }

  @Test
  public void containsMultiple() {
    Assertions.assertEquals("abc", re("^abc.*def.*ghi").containedString());
  }
}
