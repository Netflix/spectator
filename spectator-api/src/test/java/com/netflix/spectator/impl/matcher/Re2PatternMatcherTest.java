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

import com.netflix.spectator.impl.PatternExpr;
import com.netflix.spectator.impl.PatternMatcher;
import org.junit.jupiter.api.Assertions;

import java.util.regex.Pattern;

public class Re2PatternMatcherTest extends AbstractPatternMatcherTest {

  @Override
  protected void testRE(String regex, String value) {
    PatternExpr expr = PatternMatcher.compile(regex).toPatternExpr(1000);
    if (expr == null) {
      return;
    } else {
      // Validate that all remaining patterns can be processed with RE2
      expr.toQueryString(new Re2Encoder());
    }

    Pattern pattern = Pattern.compile("^.*(" + regex + ")", Pattern.DOTALL);
    if (pattern.matcher(value).find()) {
      Assertions.assertTrue(expr.matches(value), regex + " should match " + value);
    } else {
      Assertions.assertFalse(expr.matches(value), regex + " shouldn't match " + value);
    }
  }

  private static class Re2Encoder implements PatternExpr.Encoder {

    @Override
    public String regex(PatternMatcher matcher) {
      // RE2 unicode escape is \\x{NNNN} instead of \\uNNNN
      String re = matcher.toString().replaceAll("\\\\u([0-9a-fA-F]{4})", "\\\\x{$1}");
      com.google.re2j.Pattern p = com.google.re2j.Pattern.compile(
          "^.*(" + re + ")",
          com.google.re2j.Pattern.DOTALL
      );
      return p.pattern();
    }

    @Override
    public String startAnd() {
      return "(";
    }

    @Override
    public String separatorAnd() {
      return " AND ";
    }

    @Override
    public String endAnd() {
      return ")";
    }

    @Override
    public String startOr() {
      return "(";
    }

    @Override
    public String separatorOr() {
      return " OR ";
    }

    @Override
    public String endOr() {
      return ")";
    }

    @Override
    public String startNot() {
      return "NOT ";
    }

    @Override
    public String endNot() {
      return "";
    }
  }
}
