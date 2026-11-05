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

import java.util.regex.Pattern;

public class CaseInsensitivePatternMatcherTest extends AbstractPatternMatcherTest {

  private boolean shouldCheckRegex(String regex) {
    // Java regex has inconsistent behavior for POSIX character classes and the literal version
    // of the same character class. For now we skip regex that use POSIX classes.
    // https://bugs.openjdk.java.net/browse/JDK-8214245
    // Bug was fixed in jdk15.
    return JavaVersion.major() >= 15
        || (!regex.contains("\\p{") && !regex.contains("\\P{"));
  }

  @Override
  protected void testRE(String regex, String value) {
    if (shouldCheckRegex(regex)) {
      int flags = Pattern.DOTALL | Pattern.CASE_INSENSITIVE;
      Pattern pattern = Pattern.compile("^.*(" + regex + ")", flags);
      PatternMatcher matcher = PatternMatcher.compile(regex).ignoreCase();
      if (pattern.matcher(value).find()) {
        Assertions.assertTrue(matcher.matches(value), regex + " should match " + value);
      } else {
        Assertions.assertFalse(matcher.matches(value), regex + " shouldn't match " + value);
      }

      // Check pattern can be recreated from toString
      PatternMatcher actual = PatternMatcher.compile(matcher.toString());
      Assertions.assertEquals(matcher, actual);
    }
  }

  @Test
  public void negatedCharClassFoldsToEmpty() {
    // The class only has lower case letters, and none of them if the case is ignored. The
    // toString cannot express that, so it is not part of the round trip checks.
    for (String p : new String[] {"^[^\\x00-`b-\\x7f]$", "^[^\\x00-`c-\\x7f]$"}) {
      Assertions.assertTrue(PatternMatcher.compile(p).matches("a"), p);
      PatternMatcher m = PatternMatcher.compile(p).ignoreCase();
      for (String v : new String[] {"a", "A", "b", "B", "1"}) {
        Assertions.assertFalse(m.matches(v), p + " shouldn't match " + v);
      }
      Assertions.assertNotNull(m.toPatternExpr(1000), p);
    }
  }

  @Test
  public void intersectionEmptyUnlessCaseIgnored() {
    // Nothing matches unless the case is ignored. Without ignoring the case, these are
    // false and the toString cannot express the folded set, so they are not part of the
    // shared tests that check the round trip.
    String[] patterns = {
        "^[a&&A]$", "^[a&&[A]]$", "^[A-Z&&a-z]$", "^[a-c&&[B-D]]$", "^x[a&&A]*y$", "^x[a&&A]+y$"
    };
    String[] values = {"", "a", "A", "b", "B", "c", "C", "d", "z", "1", "xy", "xay", "xAay"};
    for (String p : patterns) {
      Assertions.assertFalse(PatternMatcher.compile(p).matches("a"), p);
      for (String v : values) {
        testRE(p, v);
      }
    }
  }

  @Test
  public void toStringKeepsFoldedSet() {
    // Written as [^a], it would not match 'a' when ignoring the case
    String[] patterns = {"^[\\x00-`b-\\x7f]$", "^[^\\x00-`c-\\x7f]$", "^[^\\x00-`b-\\x7f]$"};
    for (String p : patterns) {
      PatternMatcher m = PatternMatcher.compile(p);
      PatternMatcher actual = PatternMatcher.compile(m.toString());
      Assertions.assertEquals(m, actual, p);
      for (String v : new String[] {"a", "A", "b", "B", "c", "1"}) {
        Assertions.assertEquals(m.ignoreCase().matches(v), actual.ignoreCase().matches(v), p);
      }
    }
  }

  @Test
  public void caseIndependentProperties() {
    // Ignoring the case does not change the length, anchoring, or whether the pattern can
    // match at all
    String[] patterns = {
        "", ".*", "^.*", "[^\\s\\S]", "a^b", "^abc$", "abc", "^ab.*", "x{3}y", "(?:)", "[^a]+$"
    };
    for (String p : patterns) {
      PatternMatcher m = PatternMatcher.compile(p);
      PatternMatcher ic = m.ignoreCase();
      Assertions.assertEquals(m.minLength(), ic.minLength(), p);
      Assertions.assertEquals(m.isStartAnchored(), ic.isStartAnchored(), p);
      Assertions.assertEquals(m.isEndAnchored(), ic.isEndAnchored(), p);
      Assertions.assertEquals(m.alwaysMatches(), ic.alwaysMatches(), p);
      Assertions.assertEquals(m.neverMatches(), ic.neverMatches(), p);
    }
    Assertions.assertTrue(PatternMatcher.compile(".*").ignoreCase().alwaysMatches());
    Assertions.assertTrue(PatternMatcher.compile("[^\\s\\S]").ignoreCase().neverMatches());
    Assertions.assertEquals(3, PatternMatcher.compile("^abc$").ignoreCase().minLength());
  }
}
