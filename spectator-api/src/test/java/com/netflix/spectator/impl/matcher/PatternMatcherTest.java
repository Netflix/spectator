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

import com.netflix.spectator.impl.PatternExpr;
import com.netflix.spectator.impl.PatternMatcher;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class PatternMatcherTest extends AbstractPatternMatcherTest {

  @Override
  protected void testRE(String regex, String value) {
    Pattern pattern = Pattern.compile("^.*(" + regex + ")", Pattern.DOTALL);
    PatternMatcher matcher = PatternMatcher.compile(regex);
    if (pattern.matcher(value).find()) {
      Assertions.assertTrue(matcher.matches(value), regex + " should match " + value);
    } else {
      Assertions.assertFalse(matcher.matches(value), regex + " shouldn't match " + value);
    }

    // Check pattern can be recreated from toString. A class such as [^a] may be optimized
    // away, e.g. [^a]* is always true, so only one of them may keep the pattern before it was
    // optimized for ignoring the case.
    PatternMatcher actual = PatternMatcher.compile(matcher.toString());
    Assertions.assertEquals(caseSensitive(matcher), caseSensitive(actual));

    // The strings are used with other regex engines, so they should not split a surrogate
    // pair unless the input already had a lone surrogate
    if (!hasLoneSurrogate(regex)) {
      Assertions.assertFalse(hasLoneSurrogate(matcher.toString()), matcher.toString());
      PatternExpr expr = matcher.toPatternExpr(1000);
      if (expr != null) {
        Assertions.assertFalse(hasLoneSurrogate(expr.toString()), expr.toString());
      }
    }

    // Check we can serialize and deserialize the matchers
    MatcherSerializationTest.checkSerde(matcher);
  }

  /** Returns the matcher used for checking strings when the case is not ignored. */
  static PatternMatcher caseSensitive(PatternMatcher matcher) {
    return matcher instanceof CaseSensitiveMatcher
        ? ((CaseSensitiveMatcher) matcher).matcher()
        : matcher;
  }

  static boolean hasLoneSurrogate(String str) {
    for (int i = 0; i < str.length(); i = str.offsetByCodePoints(i, 1)) {
      // A code point is only a surrogate if it is not part of a pair. The code point should
      // not be cast to char, that would truncate a supplementary code point like U+1D800.
      int cp = str.codePointAt(i);
      if (cp >= Character.MIN_SURROGATE && cp <= Character.MAX_SURROGATE) {
        return true;
      }
    }
    return false;
  }

  @Test
  public void repeatStepBackWithLoneSurrogate() {
    // A lone surrogate in the pattern used to match half of a pair in the input, so the
    // position for fewer repetitions could not be found by stepping back by code points. That
    // went before the start of the repetitions or out of bounds. Like java, it now only
    // matches a lone surrogate in the input, these are kept to check that is still the case.
    String e = "😀";
    String[][] cases = {
        {"^(?:\\uD83D.){0,2}\\uDE00", e + e},
        {"^\\uD83D.{0,1}\\uD83D", e}
    };
    for (String[] c : cases) {
      boolean expected = Pattern.compile(c[0]).matcher(c[1]).find();
      Assertions.assertEquals(expected, PatternMatcher.compile(c[0]).matches(c[1]), c[0]);
    }
  }

  @Test
  @Timeout(value = 10, unit = TimeUnit.SECONDS)
  public void adjacentRepeatedCharsTerminate() {
    // Each adjacent repetition would multiply the time for a string that does not match by
    // the length, these took several seconds before they were reduced to one repetition
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < 400; ++i) {
      builder.append('a');
    }
    String str = builder.toString();
    String[] patterns = {
        "^[a-z]*[a-z]*[a-z]*[a-z]*!", "^\\w+\\w+\\w+\\w+!", "^a*a*a*a*!",
        "^[a-z]*[a-z0-9]*[a-z]+\\w*!", "^(?:a|b)*(?:a|b)*(?:a|b)*(?:a|b)*!"
    };
    for (String p : patterns) {
      Assertions.assertFalse(PatternMatcher.compile(p).matches(str), p);
    }
  }

  @Test
  public void caseSpecificClasses() {
    // Classes such as [^a-z] have a different set once the case is ignored, so some rewrites
    // of adjacent repetitions are only valid for the case as written. The case sensitive
    // matcher still uses them, but ignoring the case folds the pattern before it is optimized,
    // the same as the (?i) flag.
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < 20000; ++i) {
      builder.append('A');
    }
    String str = builder.append('1').toString();
    String[] patterns = {
        "^\\w*[^B]+b+$", "^[^a-z]*[A-Z]+[^a-z]*[A-Z]+$", "^[^a]*A*[^a]*x", "^[^a-z]*[A-Z]+$"
    };
    for (String p : patterns) {
      PatternMatcher m = PatternMatcher.compile(p);
      Assertions.assertTrue(m instanceof CaseSensitiveMatcher, p);
      Assertions.assertFalse(m.matches(str), p);
      MatcherSerializationTest.checkSerde(m);
    }

    // [^a-z]*[A-Z]+ is reduced to [^a-z]*[A-Z], it would be wrong once the case is ignored
    PatternMatcher m = PatternMatcher.compile("^[^a-z]*[A-Z]+$");
    Assertions.assertEquals("(^([^a-z])*([A-Z]$))", m.toString());
    Assertions.assertFalse(m.matches("AAb"));
    Assertions.assertTrue(m.ignoreCase().matches("AAb"));
    Assertions.assertEquals(PatternMatcher.compile("(?i)^[^a-z]*[A-Z]+$"), m.ignoreCase());

    // An expression from toPatternExpr uses the expression for the pattern that ignores the
    // case, rather than ignoring the case for each of the sub-expressions
    for (String p : new String[] {"x[^a]*A?y", "^[^a]*A?y", "(?:[^a&&A]+[^B]?|zz)q"}) {
      PatternMatcher pm = PatternMatcher.compile(p);
      PatternExpr expr = pm.toPatternExpr(100);
      Assertions.assertNotNull(expr, p);
      Assertions.assertEquals(pm.ignoreCase().toPatternExpr(100).toString(),
          expr.ignoreCase().toString(), p);
    }
    Assertions.assertTrue(PatternMatcher.compile("x[^a]*A?y").toPatternExpr(100)
        .ignoreCase().matches("xAy"));

    // The expression for a part also uses the expression for the pattern that ignores the case
    PatternExpr alt = ((PatternExpr.Or) PatternMatcher.compile("x[^a]*A?y|zz").toPatternExpr(100))
        .exprs().get(0);
    PatternMatcher leaf = ((PatternExpr.Regex) alt).matcher();
    Assertions.assertTrue(leaf.ignoreCase().matches("xAy"));
    Assertions.assertTrue(leaf.toPatternExpr(100).ignoreCase().matches("xAy"));

    // If the expression for the pattern that ignores the case has more clauses than the max,
    // then ignoring the case for the expression throws
    PatternExpr small = PatternMatcher.compile("x[^a]*(?:A|B)?y").toPatternExpr(2);
    Assertions.assertNotNull(small);
    Assertions.assertThrows(IllegalArgumentException.class, small::ignoreCase);

    // The expression is equal for the same pattern
    Assertions.assertEquals(PatternMatcher.compile("x[^a]*A?y|zz").toPatternExpr(100),
        PatternMatcher.compile("x[^a]*A?y|zz").toPatternExpr(100));

    // If the expression for the pattern that ignores the case has a different structure,
    // e.g. separate alternatives for A? that the case-sensitive pattern drops, then the
    // whole expression uses it, but there is no matching part for a sub-expression
    PatternMatcher pm = PatternMatcher.compile("x[^a]*(?:A|B)?y|foo");
    PatternExpr mismatch = pm.toPatternExpr(100);
    Assertions.assertEquals(pm.ignoreCase().toPatternExpr(100), mismatch.ignoreCase());
    Assertions.assertTrue(mismatch.ignoreCase().matches("xAy"));
    PatternExpr part = ((PatternExpr.Or) mismatch).exprs().get(0);
    Assertions.assertThrows(IllegalArgumentException.class, part::ignoreCase);

    // Clauses from expandOrClauses ignore the case using the clause at the same position
    // for the pattern that ignores the case
    for (String p : new String[] {"x[^a]*A?y|zz", "(?:[^a&&A]+[^B]?|zz)q"}) {
      PatternMatcher cm = PatternMatcher.compile(p);
      PatternMatcher ic = cm.ignoreCase();
      List<PatternMatcher> clauses = cm.expandOrClauses(10);
      Assertions.assertEquals(2, clauses.size(), p);
      for (String s : new String[] {"xAy", "xay", "xBy", "ZZQ", "aq", "Aq", "bq"}) {
        boolean cs = false;
        boolean folded = false;
        for (PatternMatcher c : clauses) {
          cs |= c.matches(s);
          folded |= c.ignoreCase().matches(s);
        }
        Assertions.assertEquals(cm.matches(s), cs, p + " " + s);
        Assertions.assertEquals(ic.matches(s), folded, p + " " + s);
      }
    }
    Assertions.assertTrue(PatternMatcher.compile("x[^a]*A?y|zz").expandOrClauses(5).get(0)
        .ignoreCase().matches("xAy"));

    // If the clauses are different once the case is ignored, then it throws
    PatternMatcher different = PatternMatcher.compile("x[^a]*(?:A|B)?y|foo");
    PatternMatcher first = different.expandOrClauses(10).get(0);
    Assertions.assertThrows(IllegalArgumentException.class, first::ignoreCase);

    // Clauses for a part of the expression also ignore the case the same as the pattern
    Assertions.assertTrue(leaf.expandOrClauses(5).get(0).ignoreCase().matches("xAy"));

    // Other patterns are not wrapped
    for (String p : new String[] {"^[a-z]*[A-Z]+$", "^[^0-9]*[A-Z]+$", "(?i)^[^a-z]*[A-Z]+$"}) {
      Assertions.assertFalse(PatternMatcher.compile(p) instanceof CaseSensitiveMatcher, p);
    }
  }

  @Test
  @Timeout(value = 10, unit = TimeUnit.SECONDS)
  public void repeatLargeBoundTerminates() {
    // A repetition with a huge count whose body can match the empty string must not loop up
    // to that count with no forward progress (previously a CPU-exhaustion vector). It must
    // still return the correct result quickly, hence the timeout to fail fast on a regression.
    PatternMatcher empty = PatternMatcher.compile("foo-(bar|){2000000000}-baz");
    Assertions.assertTrue(empty.matches("foo-barbar-baz"));
    Assertions.assertTrue(empty.matches("foo--baz"));
    Assertions.assertFalse(empty.matches("foo-bar-qux"));

    // A huge count on a non-empty body would overflow min * minLength; the saturating
    // computation keeps minLength non-negative and matching bounded by the input length.
    PatternMatcher nonEmpty = PatternMatcher.compile("(ab){2000000000}");
    Assertions.assertFalse(nonEmpty.matches("abab"));
  }

  @Test
  public void repeatLargeBoundMinLengthDoesNotOverflow() {
    // The repeat saturates its minLength at Integer.MAX_VALUE. Adding the literal length in the
    // enclosing index of matcher must not overflow to a negative value, which would corrupt the
    // bounds check of the outer matcher and reject the alternative branch.
    PatternMatcher m = PatternMatcher.compile("q(.*x(ab){2000000000}|c)");
    Assertions.assertTrue(m.matches("qc"));
    Assertions.assertFalse(m.matches("qd"));

    PatternMatcher m2 = PatternMatcher.compile("q.*(xy(ab){2000000000}|c)");
    Assertions.assertTrue(m2.matches("qzc"));
    Assertions.assertFalse(m2.matches("qzd"));

    // Same for the sum in a sequence matcher
    PatternMatcher m3 = PatternMatcher.compile("q(x(ab){2000000000}|c)");
    Assertions.assertTrue(m3.matches("qc"));
    Assertions.assertFalse(m3.matches("qd"));
  }

  @Test
  @Timeout(value = 10, unit = TimeUnit.SECONDS)
  public void repeatLargeBoundCharClass() {
    // A repeated class is checked directly. Adding a large min to the start position must
    // not overflow, that would match without the repetitions or loop forever.
    PatternMatcher m = PatternMatcher.compile("x(?=[ab]{2147483647})");
    Assertions.assertFalse(m.matches("xa"));

    PatternMatcher m2 = PatternMatcher.compile("xx(?:[ab]{2147483646,2147483647}y|z)");
    Assertions.assertFalse(m2.matches("xxay"));
    Assertions.assertFalse(m2.matches("xxaq"));
    Assertions.assertTrue(m2.matches("xxz"));
  }

  @Test
  public void possessiveClassMatchesAnyOnceCaseIgnored() {
    // The class only matches any code point once the case is ignored and is then converted
    // to '.', the possessive repetition should still consume the rest of the string
    PatternMatcher m = PatternMatcher.compile("^(?:x[[^a]A]*)?+y").ignoreCase();
    Assertions.assertFalse(m.matches("xy"));
    Assertions.assertFalse(m.matches("xay"));
    Assertions.assertTrue(m.matches("y"));
  }

  @Test
  public void negatedNestedCharClassAnyJdk() {
    // The negation applies to the whole class as in Java 9+, independent of the JDK version.
    // Named differently so it does not override the shared JDK 9+ test in the base class.
    PatternMatcher m = PatternMatcher.compile("^[^a[b]]$");
    Assertions.assertFalse(m.matches("a"));
    Assertions.assertFalse(m.matches("b"));
    Assertions.assertTrue(m.matches("c"));
    m = PatternMatcher.compile("^[^[^a]]$");
    Assertions.assertTrue(m.matches("a"));
    Assertions.assertFalse(m.matches("b"));
    m = PatternMatcher.compile("^[^a-z&&[aeiou]]$");
    Assertions.assertFalse(m.matches("a"));
    Assertions.assertTrue(m.matches("b"));
    Assertions.assertTrue(m.matches("A"));
    m = PatternMatcher.compile("^[^a[b]]$").ignoreCase();
    Assertions.assertFalse(m.matches("A"));
    Assertions.assertFalse(m.matches("B"));
    Assertions.assertTrue(m.matches("C"));
  }

  @Test
  public void charClassIntersectionOperandAnyJdk() {
    // The right operand is parsed as in Java 9+, independent of the JDK version. Named
    // differently so it does not override the shared JDK 9+ test in the base class.
    PatternMatcher m = PatternMatcher.compile("^[a-c&&[b]x]$");
    Assertions.assertTrue(m.matches("b"));
    Assertions.assertFalse(m.matches("x"));
    m = PatternMatcher.compile("^[a-z&&^a]$");
    Assertions.assertTrue(m.matches("a"));
    Assertions.assertFalse(m.matches("b"));
    m = PatternMatcher.compile("^[^a-z&&^b]$");
    Assertions.assertFalse(m.matches("b"));
    Assertions.assertTrue(m.matches("a"));
    Assertions.assertTrue(m.matches("^"));
    // A '&' right after a nested class ends the operand
    m = PatternMatcher.compile("^[a-z&&[b]&]$");
    Assertions.assertTrue(m.matches("b"));
    Assertions.assertTrue(m.matches("&"));
    Assertions.assertFalse(m.matches("a"));
    m = PatternMatcher.compile("^[a-z&&[b]x&&[x]]$");
    Assertions.assertTrue(m.matches("b"));
    Assertions.assertTrue(m.matches("x"));
  }

  @Test
  public void charClassLongIntersectionChain() {
    // Each intersection with a nested class should not need another stack frame
    StringBuilder builder = new StringBuilder("^[a-c");
    for (int i = 0; i < 20_000; ++i) {
      builder.append("&&[ab]");
    }
    builder.append("]$");
    PatternMatcher m = PatternMatcher.compile(builder.toString());
    Assertions.assertTrue(m.matches("a"));
    Assertions.assertFalse(m.matches("c"));
  }

  @Test
  public void toStringKeepsSurrogatePairs() {
    // The string is used with other regex engines, so it should not have a lone surrogate
    PatternMatcher m = PatternMatcher.compile("^(\uD83D\uDE00|\uD83D\uDE01)+$");
    String str = m.toString();
    Assertions.assertFalse(hasLoneSurrogate(str), str);
    Pattern p = Pattern.compile(str);
    Assertions.assertTrue(p.matcher("\uD83D\uDE00\uD83D\uDE01").find());
    Assertions.assertFalse(p.matcher("\uD83D\uDE02").find());
  }
}
