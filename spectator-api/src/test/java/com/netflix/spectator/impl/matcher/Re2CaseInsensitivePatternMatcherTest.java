/*
 * Copyright 2014-2024 Netflix, Inc.
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
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

public class Re2CaseInsensitivePatternMatcherTest extends AbstractPatternMatcherTest {

  @Test
  public void ignoreCasePatternExpr() {
    PatternMatcher m = PatternMatcher.compile("foo");
    Assertions.assertNotNull(m.toPatternExpr(50));
    Assertions.assertNotNull(m.ignoreCase().toPatternExpr(50));
  }

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
      PatternExpr expr = PatternMatcher.compile(regex).ignoreCase().toPatternExpr(1000);
      com.google.re2j.Pattern re2;
      if (expr == null) {
        return;
      } else {
        // Validate that all remaining patterns can be processed with RE2
        expr.toQueryString(new Re2Encoder());
        // RE2 folds the case of non-ASCII letters, e.g. (?i)k matches the Kelvin sign, while
        // java and spectator only fold ASCII letters. So RE2 is only compared for ASCII. The
        // pattern passed to RE2 is checked rather than the regex, which can have escapes for
        // non-ASCII chars such as \\u212A.
        String re2Pattern = expr instanceof PatternExpr.Regex
            ? ((PatternExpr.Regex) expr).matcher().toString()
            : null;
        if (re2Pattern != null && isAscii(re2Pattern) && isAscii(value))
          re2 = compileRE2(re2Pattern);
        else
          re2 = null;
        /*try {
          re2 = compileRE2(encoded);
        } catch (Exception e) {
          re2 = null;
        }*/
      }


      Pattern pattern = Pattern.compile("^.*(" + regex + ")", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
      if (pattern.matcher(value).find()) {
        Assertions.assertTrue(expr.matches(value), regex + " should match " + value);
        if (re2 != null)
          Assertions.assertTrue(re2.matcher(value).find(), re2 + " should match " + value);
      } else {
        Assertions.assertFalse(expr.matches(value), regex + " shouldn't match " + value);
        if (re2 != null)
          Assertions.assertFalse(re2.matcher(value).find(), re2 + " shouldn't match " + value);
      }
    }
  }

  private static boolean isAscii(String str) {
    return str.chars().allMatch(c -> c < 128);
  }

  private static com.google.re2j.Pattern compileRE2(String matcher) {
    return com.google.re2j.Pattern.compile("^.*(" + matcher + ")", com.google.re2j.Pattern.DOTALL);
  }

  private static class Re2Encoder implements PatternExpr.Encoder {

    @Override
    public String regex(PatternMatcher matcher) {
      return compileRE2(matcher.toString()).pattern();
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
