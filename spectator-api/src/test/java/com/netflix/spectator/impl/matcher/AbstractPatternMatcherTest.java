/*
 * Copyright 2014-2026 Netflix, Inc.
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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public abstract class AbstractPatternMatcherTest {

  protected abstract void testRE(String regex, String value);

  private void intercept(Class<? extends Throwable> cls, String message, Runnable task) {
    try {
      task.run();
      Assertions.fail("expected " + cls.getName() + " but no exception was thrown");
    } catch (Throwable t) {
      if (!cls.isAssignableFrom(t.getClass())) {
        String error = "expected " + cls.getName() + " but received " + t.getClass().getName();
        throw new AssertionError(error, t);
      }
      if (message != null) {
        // Ignore context of the message
        String actual = t.getMessage().split("\n")[0];
        Assertions.assertEquals(message, actual);
      }
    }
  }

  private void testBadExpression(String regex, String message) {
    //Pattern.compile(regex);
    intercept(IllegalArgumentException.class, message, () -> PatternMatcher.compile(regex));
  }

  private void testUnsupported(String regex, String message) {
    // Unsupported regex features are reported as IllegalArgumentException (the pattern is the
    // problem) with the message prefixed by "unsupported:".
    intercept(IllegalArgumentException.class, "unsupported: " + message,
        () -> PatternMatcher.compile(regex));
  }

  @Test
  public void prefix() {
    Assertions.assertEquals("abc", PatternMatcher.compile("^abc").prefix());
    Assertions.assertNull(PatternMatcher.compile("abc").prefix());
    Assertions.assertEquals("abc", PatternMatcher.compile("^(abc)").prefix());
    Assertions.assertEquals("abc", PatternMatcher.compile("^(abc|abcdef)").prefix());
    Assertions.assertEquals("abc", PatternMatcher.compile("^[a][b][c]").prefix());
    Assertions.assertEquals("abc", PatternMatcher.compile("^[a][b][c]+").prefix());
  }

  @Test
  public void matchesAfterPrefix() {
    // StartsWithMatcher, bar case should match because it will not get checked and we trust
    // that the caller has already verified the prefix.
    Assertions.assertTrue(PatternMatcher.compile("^abc").matchesAfterPrefix("abcdef"));
    Assertions.assertTrue(PatternMatcher.compile("^abc").matchesAfterPrefix("bardef"));

    // SeqMatcher
    Assertions.assertTrue(PatternMatcher.compile("^abc").matchesAfterPrefix("abc[d-f]"));
    Assertions.assertTrue(PatternMatcher.compile("^abc").matchesAfterPrefix("bar[d-f]"));

    // Start-anchored IndexOfSeqMatcher: the prefix (abc) is trusted as already verified, so the
    // remainder is matched without re-checking it.
    Assertions.assertTrue(PatternMatcher.compile("^abc.*def").matchesAfterPrefix("abcxxdef"));
    Assertions.assertTrue(PatternMatcher.compile("^abc.*def").matchesAfterPrefix("barxxdef"));
    Assertions.assertFalse(PatternMatcher.compile("^abc.*def").matchesAfterPrefix("barxxxyz"));

    // Start-anchored alternation (OrStartsWithMatcher) has no literal prefix(), so
    // matchesAfterPrefix is a full match against the prefixes.
    Assertions.assertTrue(PatternMatcher.compile("^(abc|def)").matchesAfterPrefix("abcxx"));
    Assertions.assertTrue(PatternMatcher.compile("^(abc|def)").matchesAfterPrefix("defxx"));
    Assertions.assertFalse(PatternMatcher.compile("^(abc|def)").matchesAfterPrefix("xyz"));
  }

  @Test
  public void startAnchor() {
    testRE("^abc", "abcdef");
    testRE("^abc", "123456");
    testRE("^abc^def", "def");
  }

  @Test
  public void startAnchorLiteralPercent() {
    testRE("^a%", "a%tail");
    testRE("^a%", "a%");
    testRE("^a%", "ab");
  }

  @Test
  public void isStartAnchored() {
    Assertions.assertTrue(PatternMatcher.compile("^abc").isStartAnchored());
    Assertions.assertTrue(PatternMatcher.compile("^[a-z]").isStartAnchored());
    Assertions.assertTrue(PatternMatcher.compile("(^a|^b)").isStartAnchored());
    Assertions.assertFalse(PatternMatcher.compile("(^a|b)").isStartAnchored());
    Assertions.assertFalse(PatternMatcher.compile("abc").isStartAnchored());
  }

  @Test
  public void endAnchor() {
    testRE("def$", "abcdef");
    testRE("def$", "123456");
  }

  @Test
  public void endAnchorFollowedByEmptyMatch() {
    // Content after the end anchor that can match an empty string
    testRE("(a$)c*", "xa");
    testRE("(a$)c*", "xac");
    testRE("a$(b)?", "xa");
    testRE("a$c+", "xa");
    testRE("(.*a$)+", "xa");
    testRE("q(.*a$)+", "qza");
    testRE("$^", "");
    testRE("$^", "x");
    testRE("a$^", "a");
    testRE("a*$^", "");
    testRE("a$(?=b)", "a");
    testRE("a$(?!b)", "a");
  }

  @Test
  public void startAnchorFollowedByMatchAnyInOr() {
    // Start anchor can only be removed when it is at the start of the pattern
    testRE("x(^.*a|b)c", "xac");
    testRE("x(^.*a|b)c", "xbc");
    testRE("(^.*a|b)c", "xac");
    testRE("(^.*a|b)c", "ac");
    testRE("(^.*a|b)c", "xbc");
    testRE("^(.*a|b)c", "xac");
    testRE("^(.*a|b)c", "bc");
    testRE("^(.*a|b)c", "xbc");
  }

  @Test
  public void startAnchorFollowedByMatchAnyNested() {
    // Nested start anchor that is known to be checked at the start of the string
    testRE("^(^.*)?a", "ba");
    testRE("^(^.*)?a", "b");
    testRE("^(^.*)*a", "ba");
    testRE("^(^.*)+a", "ba");
    testRE("^(^.*)?$", "a");
    testRE("(^.*)?a", "ba");
    testRE("(^.*)?", "a");
    testRE("^(^.*a)?b", "xab");
    testRE("^(^.*a)?b", "b");
    testRE("^(^.*a)?b", "xb");
    testRE("b(^.*)?a", "bxa");
    testRE("b(^.*)?a", "ba");
  }

  @Test
  public void startAnchorAfterIndexOf() {
    testRE("a..*^", "a");
    testRE("a..*^", "ab");
    testRE("a.+^", "ab");
  }

  @Test
  public void startAnchorAfterContent() {
    testRE("a^b", "ab");
    testRE("a^", "a");
    testRE("x(^b|^c)", "xb");
    testRE("(a$|b)^", "a");
    testRE("(a$|b)^", "b");
  }

  @Test
  public void isEndAnchored() {
    Assertions.assertTrue(PatternMatcher.compile("abc$").isEndAnchored());
    Assertions.assertTrue(PatternMatcher.compile("[a-z]$").isEndAnchored());
    Assertions.assertTrue(PatternMatcher.compile("(a|b)$").isEndAnchored());
    Assertions.assertFalse(PatternMatcher.compile("(a$|b)").isEndAnchored());
    Assertions.assertFalse(PatternMatcher.compile("abc").isEndAnchored());
  }

  @Test
  public void alwaysMatches() {
    Assertions.assertTrue(PatternMatcher.compile(".*").alwaysMatches());
    Assertions.assertFalse(PatternMatcher.compile(".?").alwaysMatches());
    Assertions.assertFalse(PatternMatcher.compile("$.").alwaysMatches());
  }

  @Test
  public void neverMatches() {
    Assertions.assertFalse(PatternMatcher.compile(".*").neverMatches());
    Assertions.assertFalse(PatternMatcher.compile(".?").neverMatches());
    Assertions.assertTrue(PatternMatcher.compile("$.").neverMatches());
    Assertions.assertTrue(PatternMatcher.compile("a^b").neverMatches());
    Assertions.assertTrue(PatternMatcher.compile("a$^").neverMatches());
    Assertions.assertTrue(PatternMatcher.compile("a$(?=b)").neverMatches());
    Assertions.assertFalse(PatternMatcher.compile("$^").neverMatches());
    Assertions.assertFalse(PatternMatcher.compile("a*$^").neverMatches());
    Assertions.assertFalse(PatternMatcher.compile("a$(?!b)").neverMatches());
  }

  @Test
  public void exact() {
    testRE("^abc$", "abc");
    testRE("^abc$", "abcd");
    testRE("^abc$", "abd");
    testRE("^abc$", "123");
  }

  @Test
  public void indexOf() {
    testRE(".*abc", "abc");
    testRE(".*abc", "    abc");
    testRE(".*abc.*", "    abc    ");
  }

  @Test
  public void substr() {
    testRE("abc", "12345 abc 67890");
  }

  @Test
  public void glob() {
    testRE("^*.abc", "12345 abc 67890");
    testRE("^*abc", "12345 abc 67890");
  }

  @Test
  public void indexOfSeqContains() {
    testRE(".*abc.*def", "abcdef");
    testRE(".*abc.*def", "xxabcyydefzz");
    testRE(".*abc.*def", "abc");
    testRE(".*abc.*def", "defabc");
    testRE(".*abc.*def.*", "xabcxdefx");
    testRE(".*abc.*def.*", "xdefxabcx");
    // Overlap and repeated-segment cases that stress greedy vs backtracking
    testRE(".*ab.*ab", "abab");
    testRE(".*ab.*ab", "ab");
    testRE(".*ab.*ab", "abxab");
    testRE(".*a.*a.*a", "aa");
    testRE(".*a.*a.*a", "aaa");
    // Low-selectivity leading segment (the production pathology)
    testRE(".*-.*-md-preview-", "a-b-md-preview-c");
    testRE(".*-.*-md-preview-", "a-b-c-d-e-f");
  }

  @Test
  public void indexOfSeqEndAnchored() {
    testRE(".*abc.*def$", "abcdef");
    testRE(".*abc.*def$", "abcdefx");
    testRE(".*abc.*def$", "xabcyydef");
    testRE(".*ab.*cd$", "abcd");
    testRE(".*ab.*cd$", "abcdx");
    testRE(".*ab.*ab$", "abab");
    testRE(".*ab.*ab$", "abab__");
  }

  @Test
  public void indexOfEndAnchoredWithOffset() {
    // The end anchored index of is evaluated at a non-zero start position
    testRE("x.+.+a$", "xyza");
    testRE("x.+.+a$", "_xyza");
    testRE("x.+.+a$", "xyzab");
    testRE("^x.+.+a$", "xyza");
    testRE("b.+.+a$", "bb_a");
    testRE("ab.+.+cd$", "abxycd");
    // ZeroOrOne and Repeat consumers also reject a returned position before the start
    testRE("x(.*a$)?$", "zzzxa");
    testRE("x(?:.*ab$)?$", "zzzxab");
    testRE("x(.*a$){1,2}", "zzzxa");
    testRE("x(.*a$){1,2}$", "zzzxa");
    testRE("x(.*a$){1,2}$", "zzzxab");
  }

  @Test
  public void indexOfSeqStartAnchored() {
    testRE("^abc.*def", "abcdef");
    testRE("^abc.*def", "xabcdef");
    testRE("^abc.*def", "abcxxdef");
    testRE("^abc.*def.*ghi", "abcXdefYghi");
    testRE("^abc.*def.*ghi", "abcXghiYdef");
    testRE("^abc.*def$", "abcXdef");
    testRE("^abc.*def$", "abcXdefY");
    testRE("^a.*a.*a", "aaa");
    testRE("^a.*a.*a", "baaa");
  }

  @Test
  public void indexOfSeqIgnoreCase() {
    testRE("(?i).*abc.*def", "XYabcZZdef");
    testRE("(?i).*abc.*def", "XYABCzzDEF");
    testRE("(?i).*abc.*def", "XYABCzz");
    testRE("(?i)^abc.*def$", "ABCxxDEF");
    testRE("(?i)^abc.*def$", "xABCxxDEF");
  }

  @Test
  public void orStartsWith() {
    testRE("^(abc|def|ghi)", "abcxxx");
    testRE("^(abc|def|ghi)", "defxxx");
    testRE("^(abc|def|ghi)", "ghi");
    testRE("^(abc|def|ghi)", "xabc");
    testRE("^(abc|def|ghi)", "ab");
    testRE("^(a|bb|ccc)", "ccczz");
    testRE("^(a|bb|ccc)", "b");
    testRE("^(a|bb|ccc)", "a");
    // ignore case
    testRE("(?i)^(abc|def)", "ABCxx");
    testRE("(?i)^(abc|def)", "xABC");
    // start anchor in a non-leading position: the alternation fold is reached at offset>0 and must
    // still bind to absolute position 0.
    testRE("^z(^(a|bb)|cd)", "zab");
    testRE("^z(^(a|bb)|cd)", "zcd");
    testRE("^z(^(a|bb)|cd)", "zbb");
  }

  @Test
  public void indexOfSeqEmbeddedStartAnchor() {
    // A start anchor in a non-leading position can place a start-anchored fold as a sub-matcher
    // invoked at a non-zero offset. The anchor must still bind to absolute position 0.
    testRE("^a(^b.*c|d.*e)", "abc");
    testRE("^a(^b.*c|d.*e)", "ade");
    testRE("^a(^b.*c|d.*e)", "abxc");
    testRE("x(^b.*c|q)", "xbzc");
    testRE("x(^b.*c|q)", "xq");
  }

  @Test
  @Timeout(value = 10, unit = TimeUnit.SECONDS)
  public void startsWithEmbeddedAtOffset() {
    // A start-anchored literal reached at a non-zero offset must not match even if the string
    // starts with the literal. Matching would also move the position backwards, which caused an
    // endless loop in an enclosing repetition, hence the timeout.
    testRE("b^a", "aba");
    testRE("x(^b.*c|q)", "bxbzc");
    testRE("a$(?=^a)", "a");
    testRE("^b(?!(a|^b)*c)", "bab");
    testRE("^b(?!(a|^b)*c)$^", "bab");
  }

  @Test
  public void danglingModifier() {
    testBadExpression("?:?abc", "dangling modifier");
    testBadExpression("*abc", "dangling modifier");
    testBadExpression("+abc", "dangling modifier");
  }

  @Test
  public void unclosedCharClass() {
    // unclosed character class, closing brace as first character will be treated as part
    // of the set
    testBadExpression("[]", "unclosed character class");
    testBadExpression("[\\]", "unclosed character class");
    testBadExpression("[^]", "unclosed character class");
    testBadExpression("[^\\]", "unclosed character class");
    testBadExpression("[", "unclosed character class");
  }

  @Test
  public void oneOrMore() {
    testRE("a+", "abc");
    testRE("^.+$", "abc");
    testRE("^(.+)$", "abc");
  }

  @Test
  public void numbers() {
    testRE("^1*", "1000");
    testRE("^1*", "3110");
    testRE("^1*", "3691");
    testRE("^1*", "3692");
  }

  @Test
  public void unicodeChars() {
    testRE("abc_\\u003b", "abc_\u003b");
  }

  @Test
  public void predefinedCharClasses() {
    for (char c = 0; c < 128; ++c) {
      String v = "" + c;
      testRE("\\d", v);
      testRE("\\D", v);
      testRE("\\s", v);
      testRE("\\S", v);
      testRE("\\w", v);
      testRE("\\W", v);
    }
  }

  @Test
  public void posixCharClasses() {
    String[] names = {
        "Lower",
        "Upper",
        "ASCII",
        "Digit",
        "Alpha",
        "Alnum",
        "Punct",
        "Graph",
        "Print",
        "Blank",
        "Cntrl",
        "XDigit",
        "Space"
    };
    for (char c = 0; c < 128; ++c) {
      String v = Character.toString(c);
      for (String name : names) {
        testRE("\\p{" + name + "}", v);
        testRE("\\P{" + name + "}", v);
      }
    }
  }

  @Test
  public void posixAbbreviations() {
    testBadExpression("\\pabc", "unknown character property name: a");
    testBadExpression("\\pLbc", "unknown character property name: L");
  }

  @Test
  public void escapeInCharClass() {
    testRE("abc_[\\w]+_ghi", "abc_def_ghi");
    testRE("abc_[\\w-\\s]+_ghi", "abc_de f-_ghi");
    testRE("abc_[\\]]+_ghi", "abc_]_ghi");
    testRE("abc_[]]+_ghi", "abc_]_ghi");
    testRE("abc_[de\\sf]+_ghi", "abc_ \t\ndef_ghi");
    testRE("abc_[\\p{Space}9\\w]+_ghi", "abc_ \t\n9def_ghi");
    testRE("abc_[\\t- ]+_ghi", "abc_ \t\n_ghi");
  }

  @Test
  public void nestedCharClass() {
    testRE("^[a-f&&]*$", "abcdef&");
    testRE("^[a-f&]*$", "abcdef&");
    testRE("^[a-f[A-F]]*$", "abcdefABCDEF");
    testRE("^[a-f[A-F][G-I&&[^H]]]*$", "abcdefABCDEFGI");
    testRE("^[a-f[A-F][G-I&&[H]]]*$", "abcdefABCDEFH");
    testRE("^[a-f[A-F][G-I&&[H]]]*$", "abcdef&");
    testRE("^[a-f[A-F]]*$", "abcdef[]ABCDEF");
    testRE("^[a-f[A-F[0-2]]]*$", "abcdefABCDEF012");
  }

  @Test
  public void negatedNestedCharClass() {
    // Since Java 9, a negation applies to the whole class, including nested classes and
    // intersections. Java 8 did not negate the nested classes.
    Assumptions.assumeTrue(JavaVersion.major() >= 9);
    String[] patterns = {
        "^[^[^a]]$", "^[^a[b]]$", "^[^[a]b]$", "^[^a[^b]]$", "^[^a[b[c]]]$", "^[a[^b]]$",
        "^[^a-z&&[aeiou]]$", "^[^a&&b]$", "^[^ab&&b]$", "^[^[ab]&&[bc]]$", "^[^a&&[^b]]$",
        "^[^a&&[a-c]&&[b-d]]$", "^[&&a]$", "^[^&&a]$", "^[^&&[a]]$", "^[a&&]$", "^[^a&&]$",
        "^x[^a[b]]+y$"
    };
    String[] values = {"", "a", "A", "b", "B", "c", "C", "d", "e", "1", "xcdy", "xcby"};
    for (String p : patterns) {
      for (String v : values) {
        testRE(p, v);
      }
    }
  }

  @Test
  public void charClassIntersectionOperand() {
    // Since Java 9, the right operand of an intersection is everything up to the closing
    // bracket, a '^' at the start of it is a literal, and a negation applies to the whole
    // class. Java 8 parsed these differently.
    Assumptions.assumeTrue(JavaVersion.major() >= 9);
    String[] patterns = {
        "^[a-c&&[b]x]$", "^[a-c&&[b][x]]$", "^[a&&[b]c]$", "^[a-z&&^a]$", "^[a-z&&^b[c]]$",
        "^[a-e&&b-d&&c]$", "^[a-e&&[b-d]&&[c-x]]$", "^[a-z&&[^b]x]$", "^[x[a-c]&&[b]]$",
        "^[a-c&&b[x]]$", "^[a&&^]$", "^[a&&-x]$", "^[a-z&&\\w]$", "^x[a-c&&[b]x]+y$",
        "^[^a-z&&^b]$", "^[^a-c&&[b]x]$", "^[^a-z&&^b[c]]$", "^[b-d&&[b]&]$",
        "^[a-z&&[aeiou]&]$", "^[a-z&&[b]&-]$", "^[a-z&&[b]&x]$", "^[a-z&&[b]x&&[x]]$",
        "^[a-z&&[b][c]x&&[x]]$", "^[a-c&&&x]$", "^[^c&&&]$", "^[x&&&]$"
    };
    String[] values = {"", "a", "A", "b", "B", "c", "x", "^", "-", "&", "1", "xby", "xbxy"};
    for (String p : patterns) {
      for (String v : values) {
        testRE(p, v);
      }
    }
  }

  @Test
  public void quantifierAfterSurrogatePair() {
    // A quantifier applies to the whole code point, not only the low surrogate
    String[] patterns = {
        "^\uD83D\uDE00+$", "^\uD83D\uDE00{2}$", "^a\uD83D\uDE00?b$", "^\uD83D\uDE00*$",
        "^x\uD83D\uDE00+?y$", "^(\uD83D\uDE00)+$", "^\uD83D\uDE00+\uD83D\uDE01$",
        "^\\uD83D\\uDE00+$", "^\\uD83D\\uDE00{2}$",
        // Escaped code point, e.g. from a helper that escapes all non-word characters
        "^\\\uD83D\uDE00+$", "^\\\uD83D\uDE00{2}$"
    };
    String[] values = {
        "", "\uD83D\uDE00", "\uD83D\uDE00\uD83D\uDE00", "\uD83D\uDE00\uDE00", "ab",
        "a\uD83D\uDE00b", "a\uD83D\uDE00\uD83D\uDE00b", "xy", "x\uD83D\uDE00y",
        "\uD83D\uDE00\uD83D\uDE01"
    };
    for (String p : patterns) {
      for (String v : values) {
        testRE(p, v);
      }
    }
  }

  @Test
  public void quotation() {
    testRE("(\\Q])(?\\${*\\E)", "])(?\\${*");
    testRE("\\Q])(?\\${*\\Ef(o)o", "])(?\\${*foo");
    testBadExpression("\\Q])(?\\${*", "unclosed quotation");
  }

  @Test
  public void unsupportedFeatures() {
    testUnsupported("\\h", "horizontal whitespace class");
    testUnsupported("\\H", "horizontal whitespace class");
    testUnsupported("\\v", "vertical whitespace class");
    testUnsupported("\\V", "vertical whitespace class");
    testUnsupported("\\1", "back references");
    testUnsupported("\\99", "back references");
    testUnsupported("\\k<foo>", "back references");
  }

  @Test
  public void endsWith() {
    testRE("^abc_(def|ghi)$", "abc_ghi");
    testRE("^abc_(def|ghi)$", "abc_foo_ghi");
    testRE("^abcd.*def$", "abcdef");
  }

  @Test
  public void chained() {
    testRE("(a*)(b?)(b+)b{3}", "aaabbbbbbb");
  }

  @Test
  public void greedyStartAndEnd() {
    testRE("^([^!.]+).att.com!(.+)$", "gryphon.att.com!eby");
  }

  @Test
  public void or() {
    testRE("(ab)c|abc", "abc");
    testRE("(a|b)*c|(a|ab)*c", "abc");
    testRE("^a(bc+|b[eh])g|.h$", "abh");
    testRE(".*abc.*|.*def.*|.*ghi.*", "ghi");
    testRE("[a-z]|\\p{Lower}", "ghi");
    testRE("^(\\d|\\d\\d|\\d\\d\\d)$", "123");
    testRE("^(\\d|\\d\\d|\\d\\d\\d)$", "1");
  }

  @Test
  public void orDedup() {
    testRE(
        "781|910|1109|1222|1223|1193|1224|1700|1436|1080|1278|1287|1698|1699|1580|1481|770|1191|1416|1605",
        "1710");
  }

  @Test
  public void namedCapturingGroup() {
    testRE("(?<foo>abc)", "abc");
  }

  @Test
  public void charSeqRepeat() {
    testRE("(abc){2,5}", "abc");
    testRE("(abc){2,5}", "abcabc");
    testRE("(abc){2,5}", "abcc");
    testRE("(abc){2}", "abc");
    testRE("(abc){2}", "abcabc");
    testRE("(abc){2}", "abcc");
  }

  @Test
  public void unclosedNamedCapturingGroup() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> PatternMatcher.compile("(?<foo)"));
  }

  @Test
  public void unbalancedOpeningParen() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> PatternMatcher.compile("((abc)"));
  }

  @Test
  public void unbalancedClosingParen() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> PatternMatcher.compile("(abc))"));
  }

  @Test
  public void unknownQuantifier() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> PatternMatcher.compile(".+*"));
  }

  @Test
  public void controlEscape() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> PatternMatcher.compile("\\cM"));
  }

  @Test
  public void inlineFlags() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> PatternMatcher.compile("(?u)abc"));
  }

  private List<String[]> loadTestFile(String name) throws Exception {
    List<String[]> pairs = new ArrayList<>();
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    try (InputStream in = classLoader.getResourceAsStream(name)) {
      new BufferedReader(new InputStreamReader(in))
          .lines()
          .filter(line -> line.startsWith("BE") || line.startsWith("E"))
          .forEach(line -> {
            String[] parts = line.split("\\t+");
            pairs.add(new String[] {
                parts[1].replace("\\n", "\n"),
                parts[2].replace("\\n", "\n")
            });
          });
    }
    return pairs;
  }

  @Test
  public void basic() throws Exception {
    // Check against basic compatibility tests for re2j
    loadTestFile("basic.dat").forEach(pair -> testRE(pair[0], pair[1]));
  }

  @Test
  public void jdk() throws Exception {
    // Check against basic compatibility tests for jdk based on description in Pattern
    // javadocs:
    // https://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html
    loadTestFile("jdk.dat").forEach(pair -> testRE(pair[0], pair[1]));
  }

  @Test
  public void zeroOrMoreEmptyOrClause() throws Exception {
    testRE("foo-h2(prod|)*-bar-*", "foo-h2prod-abc-v123");
    testRE("foo-h2(prod|)*-bar-*", "foo-h2prod-bar-v123");
  }

  @Test
  public void repeatedEmptyOrClause() throws Exception {
    testRE("foo-h2(prod|)+-bar-*", "foo-h2prod-abc-v123");
    testRE("foo-h2(prod|)+-bar-*", "foo-h2prod-bar-v123");
  }

  @Test
  public void negativeLookaheadWithOr() {
    testRE("foo-(?!b|c|d)", "foo-a");
    testRE("foo-(?!b|c|d)", "foo-b");
    testRE("foo-(?!b|c|d)", "foo-f");
  }

  @Test
  public void positiveLookaheadWithOr() {
    testRE("foo-(?=b|c|d)", "foo-a");
    testRE("foo-(?=b|c|d)", "foo-b");
    testRE("foo-(?=b|c|d)", "foo-f");
  }

  @Test
  public void zeroOrMoreThenOr() {
    testRE("a*(b|c|d)", "aaa");
    testRE("a*(b|c|d)", "aab");
    testRE("a*(b|c|d)", "ac");
    testRE("a*(b|c|d)", "b");
    testRE("a*(b|c|d)", "af");
    testRE("a*(b|c|d)", "f");
  }

  @Test
  public void zeroOrMoreWithOr() {
    testRE("(a|b|c)*d", "aaad");
    testRE("(a|b|c)*d", "aaa");
    testRE("(a|b|c)*d", "ababcd");
  }

  @Test
  public void repeatZero() {
    testRE("(abc){0,3}def", "def");
    testRE("(abc){0,3}def", "abcdef");
    testRE("(abc){1,3}def", "def");
    testRE("(abc){1,3}def", "abcdef");
  }

  @Test
  public void repeatNoUpperBound() {
    testRE("^a{2,}$", "a");
    testRE("^a{2,}$", "aa");
    testRE("^a{2,}$", "aaaaa");
    testRE("xa{2,}b", "xab");
    testRE("xa{2,}b", "xaaab");
    testRE("^(ab){2,}$", "ababab");
    testRE("^a{0,}$", "");
    testRE("^a{0,}$", "aaa");
    testRE("^a{2,}a$", "aa");
    testRE("^a{2,}a$", "aaa");
    testRE("(a*)(b{0,1})(b{1,})b{3}", "aaabbbbbbb");
    testRE("x(a{2,})?y", "xy");
    testRE("x(a{2,})?y", "xay");
    testRE("x(a{2,})?y", "xaaay");
  }

  @Test
  public void repeatNoUpperBoundModifiers() {
    testRE("^a{2,}?$", "aaaa");
    testRE("^a{2,}?a$", "aaa");
    testRE("^a{2,}?b", "ab");
    testRE("^\\d{2,}+$", "1111");
    testRE("^a{2,}+$", "a");
    testRE("^a{2,}+a$", "aaa");
    testRE("x(a{2,}+)?y", "xy");
    testRE("x(a{2,}+)?y", "xaay");
  }

  @Test
  public void repeatOfUnbounded() {
    // Repetition of an unbounded repetition is flattened so the result is consistent
    testRE("^(a{2,}){2}$", "aaa");
    testRE("^(a{2,}){2}$", "aaaa");
    testRE("^(a{2,}){2}a$", "aaaaa");
    testRE("^(a+){2}$", "a");
    testRE("^(a+){2}$", "aa");
    testRE("^(a+){2,3}$", "aaaa");
    testRE("(a+){2}b", "xaab");
    testRE("^(a+)?ab$", "aab");
    testRE("^(?:a*)?ab$", "aab");
    testRE("^(a+)*ab$", "aab");
    testRE("^(a{2,}){0,2}$", "aaaa");
  }

  @Test
  public void repeatIgnoreCase() {
    testRE("(?i)(ab|cd){2,}", "ABCD");
    testRE("(?i)(ab|cd){2}", "abCD");
    testRE("(?i)(ab|cd){2}", "ABxCD");
  }

  @Test
  public void repeatOneNoUpperBound() {
    testRE("^ab{1,}c$", "ac");
    testRE("^ab{1,}c$", "abc");
    testRE("^ab{1,}c$", "abbbc");
    Assertions.assertEquals("abc", PatternMatcher.compile("^abc{1,}").prefix());
  }

  @Test
  public void optionalGroupWithRepeat() {
    testRE("x(a{2})?y", "xy");
    testRE("x(a{2})?y", "xaay");
    testRE("x(?:a{2,3})?y", "xy");
    testRE("x(?:a{2,3})?y", "xaaay");

    // Large repeat count nested inside another matcher
    testRE("x(a{0,2000}b)?y", "xaby");
    testRE("x(a*+)?y", "xy");
  }

  @Test
  public void oneOrMorePossessive() {
    testRE("^a++$", "");
    testRE("^a++$", "a");
    testRE("^a++$", "aaa");
    testRE("^a++b", "ab");
  }

  @Test
  public void repeatMalformed() {
    testBadExpression("a{2,3", "unclosed repetition");
    testBadExpression("a{2,3,4}", "illegal repetition");
    testBadExpression("a{2,,}", "illegal repetition");
    testBadExpression("{2}", "dangling modifier");
    testBadExpression("a{2}{3}*", "dangling modifier");
    testBadExpression("a{2}{x}", "illegal repetition");
    testBadExpression("a{x}", "illegal repetition");
    testBadExpression("a{,3}", "illegal repetition");
    testBadExpression("a?+{3}*", "dangling modifier");
    testBadExpression("a++{3}??", "dangling modifier");
    testBadExpression("a*+{3}{x}", "illegal repetition");
    testBadExpression("a{2}{3", "unclosed repetition");
    testBadExpression("a{2}{3,2}", "illegal repetition, min > max");
    testBadExpression("a{2}??", "dangling modifier");
    testBadExpression("a{2}?+", "dangling modifier");
    testBadExpression("a{2}+?", "dangling modifier");
    testBadExpression("a{2}++", "dangling modifier");
    testBadExpression("a{2}*", "dangling modifier");
    testBadExpression("a{2,}??", "dangling modifier");
    testBadExpression("a{2,3}?*", "dangling modifier");
    testBadExpression("a*{3}*", "dangling modifier");
    testBadExpression("a?{x}", "illegal repetition");
    testBadExpression("a{+2}", "illegal repetition");
    testBadExpression("a{2,+3}", "illegal repetition");
    testBadExpression("a{0,-0}", "illegal repetition");
    testBadExpression("a{２}", "illegal repetition");
  }

  @Test
  public void repeatEmptyClause() {
    // Repetition whose body can match the empty string, via the {n,m} RepeatMatcher path
    // (the +/* paths go through ZeroOrMoreMatcher which is covered separately). Compared
    // against the reference engine to confirm the empty-match handling is correct.
    testRE("foo-h2(prod|){2,5}-bar-*", "foo-h2prod-abc-v123");
    testRE("foo-h2(prod|){2,5}-bar-*", "foo-h2prod-bar-v123");
    testRE("(a|){3}", "aa");
    testRE("(a|){3}", "");
    testRE("(abc|){0,3}def", "def");
    testRE("(abc|){2,4}def", "abcdef");
  }

  @Test
  public void repeatInOptionalGroup() {
    // The '?' applies to the group, it is not a reluctant modifier for the inner repetition
    testRE("^v(\\d{2})?$", "v");
    testRE("^v(\\d{2})?$", "v12");
    testRE("^v(\\d{2})?$", "v1");
    testRE("^ab(?:c{1,2})?d", "abd");
    testRE("^ab(?:c{1,2})?d", "abccd");
  }

  @Test
  public void repeatModifiers() {
    testRE("^a{2}?$", "aa");
    testRE("^a{1,2}?b", "aab");
    testRE("^a{2}+$", "aa");
    testRE("^a{2}+$", "aaaa");
    testRE("^(?:ab){2}+$", "abababab");
    testRE("^a{2,3}+a$", "aaaa");
    testRE("^a{2,3}?$", "aaa");
  }

  @Test
  public void repeatFollowedByRepeat() {
    // Repetitions following another repetition are ignored, consistent with java.util.regex
    String[] patterns = {
        "^a{2}{3}$", "^a{2,}{3}$", "^a{2}+{3}$", "^a{2}?{3}$", "^a{2}{3}?$", "^a{2}{3}+$",
        "^a{2}{3}{4}$", "^a{2}{3,}$", "^a{1,2}{3}b$", "^a?+{3}$", "^a*+{3}$", "^a++{3}$",
        "^a++{3}b$", "^a*{3}$", "^a?{3}$", "^a+{3}$", "^a??{3}$", "^a*?{3}$", "^a+?{3}$",
        "^(aa)?{2}$", "^a+{3}b$", "^a*{3}?$", "^a?{3}+$"
    };
    String[] values = {"aa", "aaa", "aaaa", "aaaaaa", "ab", "aab", "aaab"};
    for (String p : patterns) {
      for (String v : values) {
        testRE(p, v);
      }
    }
  }

  @Test
  public void possessiveZeroOrOne() {
    testRE("^a?+a$", "aa");
    testRE("^a?+b$", "b");
    testRE("^a?+b$", "ab");
    testRE("^(ab)?+a", "aba");
    testRE("^(a??)?+a$", "a");
    testRE("^(a*?)?+a$", "a");
    testRE("^(a+?)?+a$", "aa");
    testRE("^a{1,2}a?+a$", "aaa");
    testRE("^a{0,1}a?+a$", "aa");
  }

  @Test
  public void repeatWithOr() {
    testRE("(a|b|c){1,3}d", "aaad");
    testRE("(a|b|c){1,3}d", "aaa");
    testRE("(a|b|c){1,3}d", "ababcd");
    testRE("(a|b|c){1,5}d", "ababcd");
  }

  @Test
  public void indexOfLookahead() {
    testRE("foo-(?!bar)", "foo-bar");
    testRE("foo-(?!bar)", "foo-baz");
    testRE("foo-(?=bar)", "foo-bar");
    testRE("foo-(?=bar)", "foo-baz");
  }

  @Test
  public void seqLookahead() {
    testRE("^[fF]oo-(?!bar)$", "foo-bar");
    testRE("^[fF]oo-(?!bar)$", "foo-baz");
    testRE("^[fF]oo-(?=bar)$", "foo-bar");
    testRE("^[fF]oo-(?=bar)$", "foo-baz");
  }

  @Test
  public void repeatLookahead() {
    testRE("^((?!1234)[0-9]{4}-){2,5}", "4321-1234-");
  }

  @Test
  public void repeatInvalid() {
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> PatternMatcher.compile("a{-1,2}"));
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> PatternMatcher.compile("a{4,3}"));
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> PatternMatcher.compile("a{1000,-5}"));
    testRE("a{0,100000}", "a");
  }

  @Test
  public void escapedStructuralChars() {
    // Characters from hex, octal, and unicode escapes are literals
    testRE("^a\\x2b$", "a+");
    testRE("^a\\x2b$", "aa");
    testRE("^a\\x3f$", "a?");
    testRE("^a\\x3f$", "");
    testRE("^a\\x2a$", "a*");
    testRE("^a\\u002b$", "a+");
    testRE("^a\\053$", "a+");
    testRE("^\\x28a\\x29$", "(a)");
    testRE("^\\x5ba\\x5d$", "[a]");
    testRE("^a\\x7cb$", "a|b");
    testRE("^a\\x7cb$", "b");
    testRE("^a\\x2e$", "a.");
    testRE("^a\\x2e$", "ab");
    testRE("^\\x5ea", "^a");
    testRE("a\\x24", "a$");
    testRE("^a\\x7b2\\x7d$", "a{2}");
    testRE("^a\\x5c$", "a\\");
    testRE("^[a\\x2dz]$", "-");
    testRE("^[a\\x2dz]$", "b");
    testRE("^[\\x5d]$", "]");
    testRE("^[\\x5e]$", "^");
    testRE("^[a\\x26\\x26b]$", "&");
    testRE("^[\\x20-\\x2f]$", "+");
    testRE("^[\\x20-\\x2f]$", "0");
    testRE("^[!-\\x5d]$", "]");
    testRE("^[!-\\x5d]$", "!");
    testRE("^[!-\\x5d]$", "^");
    testRE("^[!-\\x5b]$", "[");
    testRE("^[!-\\]]$", "]");
    testRE("^[!-\\]]$", "!]");
  }

  @Test
  public void charClassRanges() {
    // End of a range cannot start another range
    testRE("^[a-c-e]$", "d");
    testRE("^[a-c-e]$", "-");
    testRE("^[a-c-e]$", "e");
    testRE("^[a-\\x63-e]$", "d");
    // Literal dash can start a range
    Assertions.assertTrue(PatternMatcher.compile("^[a-c--/]$").matches("."));
    Assertions.assertTrue(PatternMatcher.compile("^[\\d--/]$").matches("."));
    Assertions.assertTrue(PatternMatcher.compile("^[--/]$").matches("."));
    // Dash before a nested class is a literal
    testRE("^[!-[a]]$", "\"");
    testRE("^[!-[a]]$", "-");
    testRE("^[!-[a]]$", "a");
    // Leading ] and a single & can start a range
    testRE("^[]-a]$", "^");
    testRE("^[^]-a]$", "^");
    testRE("^[&-(]$", "'");
    testRE("^[!-&&b]$", "\"");
    testRE("^[!-&&b]$", "b");
  }

  @Test
  public void escapesInQuotation() {
    // Escapes within a quotation are literal
    testRE("^\\Q\\x2b\\E$", "\\x2b");
    testRE("^\\Q\\x2b\\E$", "+");
    testRE("^\\Q\\t\\E$", "\\t");
    testRE("^\\Q\\t\\E$", "\t");
    testRE("^\\Qa\\E\\x2b$", "a+");
  }

  @Test
  public void lookaheadPositionNotFixed() {
    // The lookahead can match at a different position than the rest of the pattern
    String[] patterns = {
        "foo-(?!bar)", "foo-(?!bar)x", "foo-(?![a-c].)", "(foo-)*(?!bar)", "(foo-)?(?!bar)",
        "(?=b.)a", "a(?=b)", "^(?=b.)a", "^foo-(?!bar)(?!baz)", "foo-(?!bar)(?!baz)",
        "^[a-z]{3}(?!bar)x", "[a-z]+(?!bar)"
    };
    String[] values = {
        "", "ba", "ab", "foo-", "foo-b", "foo-ba", "foo-bar", "foo-baz", "foo-bar foo-baz",
        "foo-barx foo-x", "foo-d", "foo-ad", "bar", "abcx", "abcbarx"
    };
    for (String p : patterns) {
      for (String v : values) {
        testRE(p, v);
      }
    }
  }

  @Test
  public void negatedCharClassCase() {
    // If the case is ignored, java folds the case of each element in the class before
    // applying negation or intersection
    String[] patterns = {
        "^[^a]$", "^[^A]$", "^[^aB]$", "^[^b-d]$", "^[^a-z]$", "^[a-z&&[^a]]$", "^[^\\W]$",
        "^\\P{Lower}$", "^[^\\p{Lower}]$", "^[\\P{Lower}]$", "^x[^a]+y$"
    };
    String[] values = {"", "a", "A", "b", "B", "c", "C", "z", "1", "-", "xaby", "xbcy", "xBCy"};
    for (String p : patterns) {
      for (String v : values) {
        testRE(p, v);
      }
    }
  }

  @Test
  public void quotationFollowedByQuantifier() {
    // The quantifier only applies to the last character of the quotation, and an empty
    // quotation is ignored
    String[] patterns = {
        "^\\Qab\\E{2}$", "^\\Qab\\E*$", "^\\Qab\\E+$", "^\\Qab\\E?$", "^\\Qab\\E{2}?$",
        "^\\Qab\\E{1,}$", "^a\\Q\\E{2}$", "^a\\Q\\E*$", "^\\Q\\Ea$", "^\\Qa\\E?$",
        "^x\\Qa\\E\\Qb\\E{2}$", "^(\\Qab\\E)+$", "^\\Q.*\\E+$"
    };
    String[] values = {"", "a", "aa", "aaa", "ab", "abb", "abbb", "abab", "xabb", ".*", ".**"};
    for (String p : patterns) {
      for (String v : values) {
        testRE(p, v);
      }
    }
    testBadExpression("\\Q\\E*", "dangling modifier");
    testBadExpression("a|\\Q\\E{2}", "dangling modifier");
  }

  @Test
  public void emptyQuotationBetweenQuantifiers() {
    // An empty quotation joins the elements on either side, so a*\Q\E? is the reluctant
    // quantifier a*? and a{2}\Q\E{3} is a{2} with the following repeat ignored
    String[] patterns = {
        "^a{2}\\Q\\E{3}$", "^a{2}\\Q\\E?$", "^a{2}\\Q\\E+$", "^a*\\Q\\E?$", "^a*\\Q\\E+$",
        "^a?\\Q\\E?$", "^a+\\Q\\E?$", "^a*\\Q\\E{2}$", "^a\\Q\\E+\\Q\\E?$",
        "^\\Qab\\E{2}\\Q\\E{3}$", "^a\\Q\\E\\Q\\E{2}$", "^(\\Q\\E?:a)$", "^[\\Q\\E^a]$"
    };
    String[] values = {"", "a", "aa", "aaa", "aaaa", "aaaaaa", "ab", "abb", ":a", "b"};
    for (String p : patterns) {
      for (String v : values) {
        testRE(p, v);
      }
    }
    testBadExpression("a{2}+\\Q\\E?", "dangling modifier");
  }

  @Test
  public void quotationFollowedByQuantifierSurrogatePair() {
    // The quantifier applies to the last code point rather than only the low surrogate
    String e = "\uD83D\uDE00";
    String[] patterns = {"^\\Q" + e + "\\E+$", "^\\Qa" + e + "\\E{2}$"};
    String[] values = {e, e + e, e + "\uDE00", "a" + e + e, "a" + e + "\uDE00"};
    for (String p : patterns) {
      for (String v : values) {
        testRE(p, v);
      }
    }
  }
}
