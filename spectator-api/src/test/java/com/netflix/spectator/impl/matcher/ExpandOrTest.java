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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ExpandOrTest {

  private static List<String> expand(String pattern) {
    return PatternMatcher.compile(pattern)
        .expandOrClauses(50)
        .stream()
        .map(Object::toString)
        .sorted()
        .collect(Collectors.toList());
  }

  private static List<String> list(String... vs) {
    return Arrays.asList(vs);
  }

  @Test
  public void expandIndexOf() {
    Assertions.assertEquals(list(".*fooABC", ".*fooabc"), expand("foo(ABC|abc)"));
  }

  @Test
  public void expandNegativeLookaheadMatcher() {
    // OR within negative lookahead cannot be expanded without changing the
    // interpretation.
    Assertions.assertEquals(list("(.)*(?!(ABC|abc))"), expand("(?!ABC|abc)"));
  }

  @Test
  public void expandPositiveLookaheadMatcher() {
    Assertions.assertEquals(list("(.)*(?=ABC)", "(.)*(?=abc)"), expand("(?=ABC|abc)"));
  }

  @Test
  public void expandRepeatMatcher() {
    // OR within repeat cannot be expanded without changing the interpretation.
    Assertions.assertEquals(list("(.)*(?:((ABC)|(abc))){2,5}"), expand("(ABC|abc){2,5}"));
  }

  @Test
  public void expandZeroOrMoreMatcher() {
    // OR within repeat cannot be expanded without changing the interpretation, however, pattern
    // following it can be expanded.
    List<String> rs = expand("(ABC|abc)*(DEF|def)");
    List<String> expected = list(
        "(.)*((ABC|abc))*DEF",
        "(.)*((ABC|abc))*def"
    );
    Assertions.assertEquals(expected, rs);
  }

  @Test
  public void expandZeroOrOneMatcher() {
    List<String> rs = expand("(ABC|abc)?(DEF|def)");
    List<String> expected = list(
        "(.)*(?:ABC)?DEF",
        "(.)*(?:ABC)?def",
        "(.)*(?:abc)?DEF",
        "(.)*(?:abc)?def",
        ".*DEF",
        ".*def"
    );
    Assertions.assertEquals(expected, rs);
  }

  @Test
  public void expandZeroOrOneMatcherExceedsLimit() {
    List<PatternMatcher> rs = PatternMatcher
        .compile("^(ABC|abc)?(DEF|def)")
        .expandOrClauses(5);
    Assertions.assertNull(rs);
  }

  @Test
  public void expandZeroOrOneMatcherNoChange() {
    List<String> rs = expand("ab?c");
    List<String> expected = list(".*a(?:b)?c");
    Assertions.assertEquals(expected, rs);
  }

  @Test
  public void expandSeqMatcher() {
    List<String> rs = expand("(ABC|abc).(DEF|def)");
    List<String> expected = list(
       ".*ABC(.DEF)",
        ".*ABC(.def)",
        ".*abc(.DEF)",
        ".*abc(.def)"
    );
    Assertions.assertEquals(expected, rs);
  }

  @Test
  public void expandOrClauses() {
    List<String> rs = expand("abc(d|e(f|g|h)ijk|lm)nop(qrs|tuv)wxyz");
    List<String> expected = list(
        ".*abcdnopqrswxyz",
        ".*abcdnoptuvwxyz",
        ".*abcefijknopqrswxyz",
        ".*abcefijknoptuvwxyz",
        ".*abcegijknopqrswxyz",
        ".*abcegijknoptuvwxyz",
        ".*abcehijknopqrswxyz",
        ".*abcehijknoptuvwxyz",
        ".*abclmnopqrswxyz",
        ".*abclmnoptuvwxyz"
    );
    Assertions.assertEquals(expected, rs);
  }

  @Test
  public void crossProduct() {
    // Pattern with 26^10 expanded patterns, ~141 trillion
    String alpha = "(a|b|c|d|e|f|g|h|i|j|k|l|m|n|o|p|q|r|s|t|u|v|w|x|y|z)";
    String pattern = "";
    for (int i = 0; i < 10; ++i) {
      pattern += alpha;
    }
    Assertions.assertNull(PatternMatcher.compile(pattern).expandOrClauses(5));
  }
}
