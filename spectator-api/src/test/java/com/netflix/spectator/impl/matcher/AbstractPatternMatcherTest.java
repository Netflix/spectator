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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

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

  private static final String REPEAT_UNSUPPORTED =
      "repetition or group of alternatives that can match different lengths followed by more of the pattern";

  private static final String CHAIN_UNSUPPORTED =
      "too many ways to match chained repetitions that can match the same chars";

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
    // Optional, so it matches every string
    Assertions.assertTrue(PatternMatcher.compile(".?").alwaysMatches());
    Assertions.assertFalse(PatternMatcher.compile("^.?$").alwaysMatches());
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
  public void charClassZeroOrMore() {
    testRE("^[0-9]*$", "12345");
    testRE("^[0-9]*$", "123a5");
    testRE("^[0-9]*$", "");
    testRE("^a[0-9]*b", "a123b");
    testRE("^a[0-9]*b", "a12x3b");
    testRE("^a[0-9]*b", "ab");
    testRE("[a-c]*d", "xxabcabd");
    testRE("[a-c]*d", "xxABCABD");
    testRE("^[a-c]*$", "abcABC");
    testRE("^[^0-9]*5", "abc5");
    testRE("^[^0-9]*5", "ab4c5");
    testRE("^[a-z]*[0-9]", "abcDEF1");
    testRE("^\\d*\\d$", "1");
    testRE("^(ab)[0-9]*", "ab");
    testRE("^[0-9]*12$", "0001212");
  }

  @Test
  public void indexOfIgnoreCaseNoLetters() {
    testRE("123-456", "abc-123-456-def");
    testRE("123-456", "abc-123-457-def");
    testRE("^.*12.*-34", "x12y-34z");
    testRE("^.*12.*-34", "x12y-35z");
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
    // Valid for java, but not supported
    testUnsupported("\\pLbc", "character property \\p{L}");
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
  public void alternativesWithSurrogatePairs() {
    // A common prefix is split by code point, so a surrogate pair is not split
    String[] patterns = {
        "^(\uD83D\uDE00|\uD83D\uDE01)+$", "^(\uD83D\uDE00a|\uD83D\uDE00b)$",
        "^(\uD83D\uDE00|\uD83D\uDE01)x$", "(\uD83D\uDE00|\uD83D\uDE01)*y"
    };
    String[] values = {
        "", "\uD83D\uDE00", "\uD83D\uDE00\uD83D\uDE01", "\uD83D\uDE02", "\uD83D\uDE00a",
        "\uD83D\uDE00b", "\uD83D\uDE01x", "y", "\uD83D\uDE01y"
    };
    for (String p : patterns) {
      for (String v : values) {
        testRE(p, v);
      }
    }
  }

  @Test
  public void nonAsciiCase() {
    // Like java with CASE_INSENSITIVE, only the case of ASCII letters is ignored, e.g. 'k'
    // does not match the Kelvin sign and 'e' with an accent does not match the upper case
    String[] patterns = {
        "^k$", "^K$", "\u212A", "^\u00E9$", "^\u00C9$", "^\uD801\uDC00+$", "^\uD801\uDC28+$",
        "^(\uD801\uDC00x|\uD801\uDC01y)$", "^ab.*\u00E9", "\u00E9.*\u00E9", "x\u00E9y",
        "^(\u00E9a|\u00E9b)", "^(\u00E9a|\u00C9b)", "^a\u00C9b$", "[a-z]\u00E9", "\u00E9$",
        "^a.*b.*\u00E9$"
    };
    String[] values = {
        "k", "K", "\u212A", "\u00E9", "\u00C9", "\uD801\uDC00", "\uD801\uDC28\uD801\uDC28",
        "\uD801\uDC29y", "\uD801\uDC00x", "AB\u00C9", "ab\u00E9", "\u00C9 \u00E9", "X\u00C9Y",
        "x\u00E9y", "\u00C9A", "\u00E9a", "\u00C9B", "\u00E9B", "A\u00E9B", "a\u00C9B", "Z\u00E9",
        "z\u00C9"
    };
    for (String p : patterns) {
      for (String v : values) {
        testRE(p, v);
      }
    }
  }

  @Test
  public void codePoints() {
    // Like java, '.' and a class that includes non-ASCII characters, e.g. [^a], match a
    // code point, so a surrogate pair is matched as a whole
    String[] patterns = {
        "^.$", "^..$", "^.{2}$", "^a.b$", "^[^a]$", "^[^a]{2}$", "^[^a]+$", "^[^a]*$",
        "^x[^a]y$", "^\\S$", "^\\W+$", "^\\D\\D$", "^\\P{Alpha}$", "[^a-z]", "^[^\\s\\S]$",
        "^[\\x00-\\x{10ffff}]$", "^[\\x{80}-\\x{10ffff}]+$", "^[^\\x{80}-\\x{10ffff}]$",
        "^[a\\x{80}-\\x{10ffff}]$", "^[^a\\x{80}-\\x{10ffff}]$", "^[a-c\\W]$", "(?!b)\\W$",
        // Repetitions, including the implicit .* at the start, should not stop between the
        // chars of a surrogate pair
        "(?!😀)\\S", "(?!😀).$", "^.*(?!😀)\\W$",
        "^[^a]*(?!😀)\\W$", "x.*(?!😀)[^a]$", "(?!😀)(?=.{3})",
        "^[\\x{80}-􏿿]$"
    };
    String[] values = {
        "", "a", "b", "-", "\u00E9", "ab", "a\u00E9", "\uD83D\uDE00", "\uD83D\uDE00\uD83D\uDE00",
        "x\uD83D\uDE00y", "a\uD83D\uDE00b", "\uD83D", "\uDE00", "\uDE00\uD83D", "a\uD83D\uDE00",
        "\uD83D\uDE00a", "\uD801\uDC00", "\uDBFF\uDFFF"
    };
    for (String p : patterns) {
      for (String v : values) {
        testRE(p, v);
      }
    }

    // Nested classes and intersections are parsed differently by java 8
    if (JavaVersion.major() >= 9) {
      String[] nested = {
          "^[^[^b]]$", "^[^a&&[^b]]$", "^[\\S&&[^a]]$", "^[a-z&&\\W]$", "^[\\W&&\\S]$"
      };
      for (String p : nested) {
        for (String v : values) {
          testRE(p, v);
        }
      }
    }
  }

  @Test
  public void nonAsciiClasses() {
    // Like java, a class can list any code point and ranges of them. RE2J is very slow to
    // compile a class with a large range when ignoring case, so both the class and the
    // negated form having one, e.g. [^\x{1f600}-\x{10ffff}], is avoided.
    String[] patterns = {
        "^[\u00E9]$", "^[\u00E9a]+$", "^[^\u00E9]$", "^[\u00E0-\u00FF]+$", "^[a-z\u00E0-\u00FF]+$",
        "^[\uD83D\uDE00]$", "^[\uD83D\uDE00-\uD83D\uDE02]+$", "^[^\uD83D\uDE00]$",
        "^[\\x{1F600}-\\x{1F64F}]{2}$", "^[\\w\uD83D\uDE00]+$", "[\uD83D\uDE00\u00E9]",
        "^[\uD83D\uDE00\uD83D\uDE01]$", "^[^\uD83D\uDE00-\uD83D\uDE4F]$", "^[\\x{d800}-\\x{dfff}]$",
        "^[\u00E9-\\x{10ffff}]$", "^x[\u00E9\uD83D\uDE00]*y$", "^[\u00E9\u00C9]?a$",
        "(?i)^[\u00E9a]+$", "(?i)^[^\u00E9a]$",
        // A lone surrogate does not match half of a pair, so it is not a char sequence
        "^[\\x{d83d}]", "^[\\x{d83d}]+", "^(?![\\x{d83d}]).*$"
    };
    String[] values = {
        "", "a", "A", "\u00E9", "\u00C9", "\u00FF", "\u00E9a", "\uD83D\uDE00", "\uD83D\uDE01",
        "\uD83D\uDE02", "\uD83D\uDE03", "\uD83D\uDE00\uD83D\uDE01", "\uD83D", "\uDE00",
        "x\uD83D\uDE00\u00E9y", "xy", "\u00E9a", "\uDBFF\uDFFF", "\u0100"
    };
    for (String p : patterns) {
      for (String v : values) {
        testRE(p, v);
      }
    }

    // Nested classes and intersections are parsed differently by java 8
    if (JavaVersion.major() >= 9) {
      String[] nested = {
          "^[\u00E0-\u00FF&&[^\u00E9]]$", "^[^\u00E9[\uD83D\uDE00]]$", "^[\\S&&[^\u00E9]]$"
      };
      for (String p : nested) {
        for (String v : values) {
          testRE(p, v);
        }
      }
    }
  }

  @Test
  public void loneSurrogates() {
    // Like java, a lone surrogate in the pattern only matches a lone surrogate in the input,
    // not half of a pair. A quoted lone surrogate is not joined with a surrogate outside of
    // the quotation, nor are surrogates on either side of an empty quotation. Java does match
    // an unanchored literal sequence that starts with a low surrogate at the second char of a
    // pair, e.g. \x{de00}a on an emoji followed by 'a', but not \x{de00} or [\x{de00}]a, those
    // are not included.
    String[] patterns = {
        "^\uD83D\\Q\uDE00\\E$", "^\uD83D\\Q\uDE00\\E*$", "^\\Q\uD83D\uDE00\\E$",
        "^\\Q\uD83D\\E\uDE00", "^(?:\uD83D\\Q\uDE00\\E)*$",
        "^\\x{d83d}", "\\x{de00}", "^\\uD83D", "\\uDE00$", "^\uD83D", "\uDE00",
        "^\\x{d83d}\\x{de00}$", "^\\x{d83d}+", "^(?:\\x{d83d}|a)", "^.\\x{de00}", "\\x{d83d}x",
        "x\\x{de00}", "^\\Q\uD83D\\E", "^\\x{d83d}?\\x{de00}", "^a\\x{d83d}", "\\x{d83d}$",
        "^\uD83D\\Q\\E\uDE00$", "^[\uD83D\\Q\\E\uDE00]$", "^\\\uD83D\\Q\\E\uDE00"
    };
    String[] values = {
        "\uD83D\uDE00", "\uD83D", "\uDE00", "\uDE00\uD83D", "x\uD83D", "\uD83Dx",
        "a\uD83D\uDE00", "x\uDE00", "\uD83D\uD83D\uDE00", "\uD83D\uDE00\uDE00", "a\uD83D",
        "\uD83D\uDE00x"
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
    // Like java, an unclosed quotation extends to the end of the pattern. The test helper
    // wraps the pattern in a group, so it would also be quoted for java.
    Assertions.assertTrue(PatternMatcher.compile("\\Q])(?\\${*").matches("x])(?\\${*"));
    Assertions.assertTrue(PatternMatcher.compile("^a\\Qb+c$").matches("ab+c$"));
    Assertions.assertFalse(PatternMatcher.compile("^a\\Qb+c$").matches("abc"));
  }

  @Test
  public void quotationInCharClass() {
    // Quotations are removed before parsing, so they can be used anywhere a literal can
    String[] patterns = {
        "^[a-\\Qz\\E]$", "^[a\\Q]\\E]$", "^[a\\Q-\\E]$", "^[a\\Q-\\Ec]$", "^[\\Qa-c\\E]$",
        "^[\\Q^\\Ea]$", "^\\Q\\\\E$", "^\\Qa\\\\E$"
    };
    String[] values = {"", "a", "b", "c", "m", "z", "-", "]", "^", "\\", "a\\"};
    for (String p : patterns) {
      for (String v : values) {
        testRE(p, v);
      }
    }
  }

  @Test
  public void escapesDecodedByParser() {
    // Escaped characters are always literals, so they cannot act as syntax
    testBadExpression("a{2\\x2c3}", "illegal repetition");
    // Like java, an escape is not part of the group syntax
    testBadExpression("(?\\x3aa)", "unknown inline modifier");
    // Like java, a code point can be written as \x{h...h} and an escaped surrogate pair is
    // a single code point
    testRE("^\\x{1F600}+$", "\uD83D\uDE00\uD83D\uDE00");
    testRE("^\\x{61}{2}$", "aa");
    testRE("^[\\x{61}-c]$", "b");
    testRE("^\\uD83D\\uDE00{2}$", "\uD83D\uDE00\uD83D\uDE00");
    // Like java, an unknown escape for an ASCII letter or digit is an error. Boundary
    // matchers and sequences are not allowed in a character class.
    String[] illegal = {
        "\\y", "\\E", "[\\y]", "[\\1]", "[0-\\9]", "[a-\\y]",
        "[\\b]", "[\\B]", "[\\A]", "[\\G]", "[\\Z]", "[\\z]", "[\\R]", "[\\X]", "[a-\\b]"
    };
    for (String p : illegal) {
      testBadExpression(p, "illegal/unsupported escape sequence");
    }
  }

  @Test
  public void quotationLeadingDigit() {
    // Like java, a digit at the start of a quotation is not joined with an escape or a
    // repetition count before the quotation
    testRE("^\\07\\Q7\\E$", "\u00077");
    testRE("^\\07\\Q7\\E$", "?");
    testRE("^\\Q12\\E{2}$", "122");
    testRE("^[\\Q1\\E-3]$", "2");
    testRE("^\\0\\Q\\E7$", "\u0007");
    testBadExpression("\\0\\Q1\\E", "invalid octal escape sequence");
    testBadExpression("\\x4\\Q1\\E", "invalid hexadecimal escape sequence");
    testBadExpression("\\x\\Q41\\E", "invalid hexadecimal escape sequence");
    testBadExpression("\\u004\\Q1\\E", "invalid unicode escape sequence");
    testBadExpression("a{\\Q2\\E}", "illegal repetition");
    testBadExpression("a{1\\Q2\\E}", "illegal repetition");
  }

  @Test
  public void syntaxNotMixedWithWrapper() {
    // The pattern is parsed on its own, so it cannot close or extend the group used to
    // match anywhere in the string
    testBadExpression("a)|(b", "unmatched closing parenthesis");
    testBadExpression("a)", "unmatched closing parenthesis");
    testBadExpression(")", "unmatched closing parenthesis");
    testBadExpression("abc\\", "dangling escape");
    testBadExpression("(a\\)", "unclosed group");
    testBadExpression("(?=a", "unclosed group");
    testBadExpression("(?!a", "unclosed group");
    testBadExpression("(?", "unclosed group");
    testBadExpression("(?<name", "named capturing group is missing trailing '>'");
    testBadExpression("(?<name>a", "unclosed group");
    testBadExpression("[a\\]", "unclosed character class");
    testBadExpression("[&&", "unclosed character class");
    testBadExpression("[!-", "illegal character range");
    testBadExpression("\\p{Lower", "unclosed character property name");
    testBadExpression("\\p", "missing character property name");
    testBadExpression("[\\P", "missing character property name");
    testBadExpression("?", "dangling modifier");
  }

  @Test
  public void errorContextIsPattern() {
    // The context shows the pattern and position without the wrapper text
    assertErrorMessage("a)|(b", "unmatched closing parenthesis\na)|(b\n ^");
  }

  @Test
  public void quantifierAtEndOfPattern() {
    String[] patterns = {"a*", "a+", "a?", "a*?", "a+?", "a??", "a*+", "a++", "a?+", "a{2}", "a{2,}?"};
    String[] values = {"", "a", "aa", "aaa", "b"};
    for (String p : patterns) {
      for (String v : values) {
        testRE(p, v);
      }
    }
  }

  @Test
  public void unsupportedFeatures() {
    testUnsupported("\\h", "horizontal whitespace class");
    testUnsupported("\\H", "horizontal whitespace class");
    testUnsupported("\\v", "vertical whitespace class");
    testUnsupported("\\V", "vertical whitespace class");
    testUnsupported("\\1", "back references");
    testUnsupported("\\99", "back references");
    testUnsupported("(?<foo>a)\\k<foo>", "back references");
    testUnsupported("\\cA", "control character");
    testUnsupported("[\\cA]", "control character");
    testUnsupported("a\\b", "word boundary");
    testUnsupported("a\\B", "word boundary");
    testUnsupported("\\Aa", "boundary matcher");
    testUnsupported("a\\z", "boundary matcher");
    testUnsupported("a\\Z", "boundary matcher");
    testUnsupported("\\Ga", "boundary matcher");
    testUnsupported("\\R", "escape sequence \\R");
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
    testUnsupported("(a|b)*c|(a|bc)*c", REPEAT_UNSUPPORTED);
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
    testRE("(?<a1>x)(?<B>y)", "xy");
    testRE("x(?<abc>(?<d>y))", "xy");
    testRE("(?<a>)", "");
  }

  @Test
  public void namedCapturingGroupInvalidName() {
    // Like java, a name is an ASCII letter followed by ASCII letters or digits and can only
    // be used once
    String start = "capturing group name does not start with a Latin letter";
    testBadExpression("(?<1a>x)", start);
    testBadExpression("(?<>x)", start);
    testBadExpression("(?<\u00E9>x)", start);
    testBadExpression("(?<", start);
    String trailing = "named capturing group is missing trailing '>'";
    testBadExpression("(?<a-b>x)", trailing);
    testBadExpression("(?<a_b>x)", trailing);
    testBadExpression("(?<a b>x)", trailing);
    testBadExpression("(?<a", trailing);
    testBadExpression("(?<a>x)(?<a>y)", "named capturing group <a> is already defined");
    testBadExpression("(?<a>x)|(?<a>y)", "named capturing group <a> is already defined");
    // Like java, the error points to the '>' of the repeated name
    assertErrorMessage("(?<a>x)(?<a>y)",
        "named capturing group <a> is already defined\n(?<a>x)(?<a>y)\n           ^");
  }

  @Test
  public void malformedOrUnsupportedLikeJava() {
    // Like java, a malformed pattern is an error and a valid one that uses a feature that is
    // not supported throws unsupported, even if the error comes after the unsupported feature
    testBadExpression("(?<=a)b(", "unclosed group");
    testBadExpression("\\cA[", "unclosed character class");
    testBadExpression("(?i", "unknown inline modifier");
    testBadExpression("(?q)", "unknown inline modifier");
    testBadExpression("(?i-", "unknown inline modifier");
    testUnsupported("(?i:a)", "inline flags");
    testUnsupported("(?is-m:a)b", "inline flags");
    testUnsupported("(?>ab)", "atomic groups");
    testBadExpression("\\k", "\\k is not followed by '<' for named capturing group");
    testBadExpression("\\kx", "\\k is not followed by '<' for named capturing group");
    testBadExpression("\\k<a>", "named capturing group <a> does not exist");
    testUnsupported("(?<a>x)\\k<a>", "back references");
    testBadExpression("\\c", "illegal control escape sequence");
    testUnsupported("\\cA", "control character");
    testBadExpression("\\N", "illegal character name escape sequence");
    testBadExpression("\\Nx", "illegal character name escape sequence");
    // The name is not checked, it is unsupported either way
    testUnsupported("\\N{LATIN SMALL LETTER A}", "escape sequence \\N");
    testUnsupported("\\N{}", "escape sequence \\N");
    testUnsupported("\\p{L}", "character property \\p{L}");
    testUnsupported("[\\P{Lu}]", "character property \\p{Lu}");
    testUnsupported("\\p{IsLatin}", "character property \\p{IsLatin}");
    testUnsupported("\\p{javaLowerCase}", "character property \\p{javaLowerCase}");
    testBadExpression("\\p{Foo}", "unknown character property name: Foo");
    testBadExpression("a(?i)*", "dangling meta character '*'");
    testBadExpression("\\px", "unknown character property name: x");
    // An escape that is not supported is still an element of a class, and like java, a
    // control character can be used for a range
    testUnsupported("[\\cA&&]", "control character");
    testUnsupported("[\\N{LATIN SMALL LETTER A}&&]", "escape sequence \\N");
    testUnsupported("[\\cA-z]", "control character");
    testUnsupported("[\\x00-\\cA]", "control character");
    testBadExpression("[\\cA-\\x00]", "illegal character range");
    testBadExpression("[a-\\cA]", "illegal character range");
  }

  @Test
  public void validForJavaIsUnsupported() {
    // CANON_EQ is a valid inline flag for java
    testUnsupported("a(?c)b", "inline flags");
    testUnsupported("a(?ic-c:b)", "inline flags");
    // With comments, java ignores the rest of the line after a '#', and with the U flag the
    // POSIX names ignore the case, so the rest of the pattern is not checked
    testUnsupported("a(?x)#(", "inline flags");
    testUnsupported("a(?x:b #[\n)", "inline flags");
    testUnsupported("a(?U)\\p{alpha}", "inline flags");
    testBadExpression("a(?-x)#(", "unclosed group");
    // A flag that is set and then cleared has no effect
    testBadExpression("a(?x-x)#(", "unclosed group");
    testUnsupported("a(?U-u)\\p{alpha}", "inline flags");
    // Grapheme cluster boundary
    testUnsupported("\\b{g}", "word boundary");
    testUnsupported("a\\b{g}+", "word boundary");
    testUnsupported("\\b{2}", "word boundary");
    testBadExpression("\\b{g", "illegal/unsupported escape sequence");
    testBadExpression("\\B{g}", "illegal repetition");
    // Like java, the case is ignored for the key of a property
    testUnsupported("\\p{Script=Latin}", "character property \\p{Script=Latin}");
    testUnsupported("\\p{SC=Latin}", "character property \\p{SC=Latin}");
    testUnsupported("\\p{Block=BasicLatin}", "character property \\p{Block=BasicLatin}");
    testUnsupported("\\p{GC=Lu}", "character property \\p{GC=Lu}");
    testBadExpression("\\p{gc=lu}", "unknown character property name: gc=lu");
    // Binary properties and POSIX names ignore the case with the Is prefix, java properties
    // can be used with the Is prefix or as a general category
    testUnsupported("\\p{IsWord}", "character property \\p{IsWord}");
    testUnsupported("\\p{IsALNUM}", "character property \\p{IsALNUM}");
    testUnsupported("\\p{Isalpha}", "character property \\p{Isalpha}");
    testUnsupported("\\p{IsjavaLowerCase}", "character property \\p{IsjavaLowerCase}");
    testUnsupported("\\p{gc=javaDigit}", "character property \\p{gc=javaDigit}");
    testBadExpression("\\p{Isjavalowercase}", "unknown character property name: Isjavalowercase");
  }

  @Test
  public void lookbehindUnsupported() {
    testUnsupported("(?<=a)b", "lookbehind");
    testUnsupported("(?<!a)b", "lookbehind");
    // Not read as a group name up to the '>', like java it is an error for the unmatched ')'
    testBadExpression("(?<=a)>)", "unmatched closing parenthesis");
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
    // Check against basic compatibility tests for re2j. Repetitions or groups of alternatives
    // that can match different lengths, followed by more of the pattern, are not supported.
    List<String> unsupported = Arrays.asList(
        "(ab|a)(bc|c)",
        "(a|b)*c|(a|ab)*c",
        "(^|[ (,;])((([Ff]eb[^ ]* *|0*2/|\\* */?)0*[6-7]))([^0-9]|$)",
        "M[ou]'?am+[ae]r .*([AEae]l[- ])?[GKQ]h?[aeu]+([dtz][dhz]?)+af[iy]");
    for (String p : unsupported) {
      testUnsupported(p, REPEAT_UNSUPPORTED);
    }
    loadTestFile("basic.dat").stream()
        .filter(pair -> !unsupported.contains(pair[0]))
        .forEach(pair -> testRE(pair[0], pair[1]));
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
    testRE("^a{1,2}a?+a$", "aaa");
    testRE("^a{0,1}a?+a$", "aa");
  }

  private static void assertErrorMessage(String regex, String expected) {
    IllegalArgumentException e = Assertions.assertThrows(
        IllegalArgumentException.class, () -> PatternMatcher.compile(regex));
    Assertions.assertEquals(expected, e.getMessage());
  }

  @Test
  public void errorPositionRelativeToPattern() {
    // Errors show the pattern as written, not after the leading (?i) or quotations are
    // removed, and the caret points to the position in that pattern
    assertErrorMessage("(?i)a)", "unmatched closing parenthesis\n(?i)a)\n     ^");
    assertErrorMessage("\\Q+*\\Ea)", "unmatched closing parenthesis\n\\Q+*\\Ea)\n       ^");
    assertErrorMessage("(?i)\\Q..\\E(a", "unclosed group\n(?i)\\Q..\\E(a\n            ^");
    assertErrorMessage("a\\Q\\E**", "dangling modifier\na\\Q\\E**\n      ^");
    assertErrorMessage("\\Q7\\E\\Q8\\Ex{2,1}",
        "illegal repetition, min > max\n\\Q7\\E\\Q8\\Ex{2,1}\n                ^");
    assertErrorMessage("(?i)\\b", "unsupported: word boundary\n(?i)\\b\n     ^");
    // Each char of an escape outside of a quotation keeps its own position
    assertErrorMessage("\\Qa\\E\\y", "illegal/unsupported escape sequence\n\\Qa\\E\\y\n      ^");
  }

  @Test
  public void stackedModifiers() {
    // Like java, only a single modifier can follow a quantifier
    String[] patterns = {"a+++", "a?+?", "a**", "a*?+", "a+?*", "(a)+++b", "a{2}++", "a??+"};
    for (String p : patterns) {
      testBadExpression(p, "dangling modifier");
    }
    // The error points to the extra modifier
    assertErrorMessage("(a)+++b", "dangling modifier\n(a)+++b\n     ^");
  }

  @Test
  public void backtrackingQuantifiers() {
    // Optional portions and bounded repetitions are checked with the rest of the pattern,
    // possessive repetitions are not. Without capture groups, only the yes or no answer is
    // needed, so this is done by rewriting rather than general backtracking.
    String[] patterns = {
        "^(b.*)?a", "^(b.*)?a$", "^(b[a-z]*)?a", "^(ab|a)?bc", "^(a|ab)?c", "^(a.*)?(b.*)?c$",
        "^a{2,3}a$", "^(ab){1,2}ab$", "^[ab]{1,3}b$", "^a{0,2}a{2}$", "^(a|b){1,3}ab$",
        "^\\d{1,3}\\.\\d{1,3}$", "^.{1,3}x", "x.{0,2}y", "^(a{1,2}){2}a$",
        "^(a+){2}+a$", "^(a+){2}+$", "^a*+a$", "^a++a", "^a?+a$", "^(ab)*+ab$", "^[ab]*+b$",
        "^.*+x", "^a{1,3}+a$", "^a{2,}+a$", "^a{2,}+$", "^(ab|cd){1,2}+cd$",
        "^a*+b$", "^(a*)+b", "^(?:a+)?+a$", "^(?:a+)?+$", "^(a{2,})?+a$",
        "x(?:ab)?+b", "^a{2}+a$", "^(?:a[\\s\\S]*)++b", "^(?:a[\\s\\S]*)?+b", "^(a[\\s\\S]+)++b",
        "^(?:a[\\s\\S]*){2}+$"
    };
    String[] values = {
        "", "a", "aa", "aaa", "aaaa", "ab", "abb", "abc", "abab", "abcd", "abcdcd", "b", "bxa",
        "ba", "aab", "c", "ac", "bc", "abxc", "1.2", "123.45", "1234.5", "ax", "abcx", "xaby",
        "xy", "xabcy", "xabb"
    };
    for (String p : patterns) {
      for (String v : values) {
        testRE(p, v);
      }
    }
  }

  @Test
  public void variableLengthRepeatUnsupported() {
    // A repetition does not backtrack into earlier repetitions, so the repeated portion must
    // always match the same length unless nothing follows the repetition. Similarly, a group
    // of alternatives does not backtrack once more of the pattern has matched.
    String[] patterns = {
        "^(a|ab){2}c$", "^(a|ab){1,3}c$", "^(a|ab)+c", "(a|ab)*c", "^(a|ab){2}", "^(a+b?){2}$",
        "^(\\w+\\s?){1,20}$", "^([a-z]+[0-9]*){10}$", "^((a|b*){2}..){1,2}.",
        "(x(a|ab){3}|y)+z", "^(a.*|b){2}c$", "^(([ab][^a]*){2})*$", "((b|.*){3}-){2,}",
        "^((a|-|..){2,}){2}$", "^(?:q|z.*a(b|bc)(c|cd)){2}$", "(?i)^([a-z]+A){2}x",
        "^(foo|foobar)+$", "(?=(a|ab){2}c)a", "^(a{2,}){0,2}$", "^(?:(?:a|bc){1,2}x?){2}$",
        "foo-(|bar)+-baz", "^(?:(?:ab|a){0,1}x?){2}$", "^(ods|postgres|aurora|crdb)*$",
        "^(\\d+,?)*$", "b(a(b+)?-?){2}", "^\\[((\\d*\\],\\[\\d*)*|\\d*)\\]$",
        "(a|ab)(c|bcd)(d*)",
        // A lookahead after the repetition could pass for an earlier end of the repetition
        "^(?:,\\d+)*(?=\\d)", "^(?:,\\d+){1,2}(?=\\d)", "^(?:,\\d+)*(?!,)",
        // Optional portion followed by more of the pattern, found by fuzzing
        "^(.{1,2}[ab])?b((bb*?)*|a+?(([ab]{2}.+?[ab]{1,2}){2,}(a+[ab]??a|[ab])*([ab]+|[ab]{2,})*?)+?.)?"
    };
    for (String p : patterns) {
      testUnsupported(p, REPEAT_UNSUPPORTED);
    }
  }

  @Test
  public void variableLengthRepeatSupported() {
    // The repeated portion always matches the same length, or nothing after the repetition
    // depends on where it ends
    String[] patterns = {
        "^(\\d\\.){3}\\d{1,3}$", "^([a-z]{2}-){2}v\\d+$", "^(a|ab)*", "x(a|ab)+",
        "^x(a|ab)?", "^(ods|postgres|aurora|crdb)*", "^(a|b)+c", "^(a|ab)?c", "^(?:a+)+b",
        "(?:ab|cd){2,}x", "foo-(bar|)+-baz", "x(.*a$){1,2}$", "^b(?!(a|^b)*c)",
        "^x(ab|cd)(e|fg)", "^(ab|cd)(e|fg)",
        // An empty last alternative can be used for any of the repetitions, so the min
        // does not matter
        "^(a|){2}ab", "^(a|){2,3}ab", "x(ab|){1,2}abc$", "(c|){2,4}.{2,}c",
        // Only fixed length parts and repetitions of chars that cannot be followed by a char
        // they match
        "^(\\d{1,3}\\.){3}\\d{1,3}$", "^([a-z]+-){2}v\\d+$", "^(?:[^.]+\\.)+com$",
        "^\\d+(,\\d+)*$", "^[a-z]+(-[a-z]+)*$", "^(\\w+\\.)*com$", "^\\[\\d*(\\],\\[\\d*)*\\]$",
        "^(\\d+,)*\\d$", "^(a-?b*,)+$"
    };
    String[] values = {
        "", "a", "ab", "aba", "abab", "ababc", "abc", "c", "bc", "xab", "x", "10.0.0.1",
        "1.22.333.4", "1.2.3", "ab-cd-v1", "ab-v1", "odspostgres", "crdbaurora", "odsx", "abAcdAx",
        "abAx", "aab", "foo.bar.com", "foo.com", "abcdx", "1,22,333", "1,", "ab-cd-ef", "ab--cd",
        "[1],[22]", "[]", "[1]", "1,2", "ab,a-b,", "a-,", "xabc", "xababc", "cac"
    };
    for (String p : patterns) {
      for (String v : values) {
        testRE(p, v);
      }
    }
  }

  @Test
  public void possessiveUnsupported() {
    // Reluctant quantifiers are matched the same as greedy, so the first match java would
    // keep for each repetition cannot be determined
    String reluctant = "reluctant quantifier within a possessive repetition";
    for (String p : new String[] {"^(a??)?+a$", "^(a*?)?+a$", "^(a+?){2}+", "(a(b*?))++"}) {
      testUnsupported(p, reluctant);
    }
    // The first match depends on the order of alternatives or on backtracking
    String varying = "possessive repetition of a portion that can match different lengths";
    for (String p : new String[] {"(a|ab)++c", "(a+b?)++", "([ab]+[ab]{2}a)++", "((a+){2}){1,2}+",
        "(a.*b)*+", "((a+){2})?+"}) {
      testUnsupported(p, varying);
    }
    // Supported forms
    for (String p : new String[] {"a*+", "[ab]{2,}+", "(ab|cd){1,3}+", "(a+){2}+", "(a?)++",
        "(ab*)++", "(a{1,3})?+", "((a+){2}+)?+", ".*+x", "(?:ab)?+b", "(a{2}{3}?)++"}) {
      PatternMatcher.compile(p);
    }
  }

  private static String repeat(String s, int n) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < n; ++i) {
      builder.append(s);
    }
    return builder.toString();
  }

  /** Length of the optimized matcher, it is checked even if the pattern is not supported. */
  private static int optimizedLength(String pattern) {
    return Optimizer.optimize(Parser.parse(pattern, 0)).toString().length();
  }

  @Test
  public void adjacentRepeatedChars() {
    // Adjacent repetitions where one set of chars contains the other are reduced to one
    // repetition, check that the answers are the same as java
    String[] patterns = {
        "^[a-z]*[a-z]*!", "^\\w+\\w+$", "^[a-z]*[a-z0-9]*[a-z]+\\w*!", "^a*a*a+$",
        "[a-z]+[a-z]+", "^(?:a|b)*[ab]+c", "^\\w*[a-z]\\w*-", "^\\w+[a-z]*\\w+$", "^.*[a-z]*!",
        "^[^a]*[b-z]*x", "^\\d+\\w+\\d+$", "^[a-c]*[b-d]*x"
    };
    String[] values = {
        "", "!", "a!", "ab!", "abc", "a1!", "1a!", "aa", "aab", "ab", "abc", "a-", "1a1", "12",
        "1", "a", "x", "bx", "dx", "\u00e9x", "\u00e9\u00e9!"
    };
    for (String p : patterns) {
      for (String v : values) {
        testRE(p, v);
      }
    }
  }

  @Test
  public void chainedRepetitionsUnsupported() {
    // Each repetition in a chain is tried at each position where the one before could stop,
    // so the time grows with the length to the power of the number of repetitions
    String[] patterns = {
        "^\\w+-?\\w+-?\\w+$", "^[a-c]*[b-d]*[a-c]*!", "^\\w*:?\\w*:?\\w*$",
        "(?i)^[a-z]+-?[A-Z]+-?[a-z]+$", "\\w+-?\\w+-?\\w+!", "^(?=\\w+-?\\w+-?\\w+!)",
        "^x(?:[a-c]+[b-d]+|y)[a-c]+!", "^[a-c]*(?:ab)*[b-d]*!",
        "^[a-c]{0,10}[b-d]{0,10}[a-c]{0,10}[b-d]{0,10}[a-c]{0,10}[b-d]{0,10}x",
        // A required repetition can be in the chain with the repetitions on either side
        "^\\w*-?a{1,2}b\\w*-?\\w*!", "^\\w*(?=\\w)a{1,2}b\\w*-?\\w*!",
        // Only one way to match the last repetition, but it consumes the chars for each attempt,
        // including a repetition at the end of a lookahead
        "^\\w*-?\\w*-?\\w*+!", "^\\w*-?\\w*-?[a-z]*\\d!",
        "(?=.{2,}[b-d](?!b+))-{0,10}[ab]{1,2}[^a]{0,3}$",
        // A repetition of a portion with a chain, each repetition can scan past the chars it
        // consumes, e.g. for ((.+b++)?b)* on "bbb...", so the cost of the chain is multiplied
        // by the number of repetitions. Found by fuzzing.
        "((.+b++)?b)*", "((.+b++)?b){0,300}", "^(b*?(([ab][ab]b|[ab]+?)*.*+a|(b){1,3})*)",
        "^(a*?((b{1,3})++a{0,2}a{2,}+|a+)b{1,2}|[ab]{0,2})*?.*?$",
        "^(a{2}+((.+?a*+)[ab]a{0,2}+)*?(b[ab]?)*?|((.+b++)?[ab]{1,2}|[ab]{2}+([ab]+){1,3})*?)??",
        // The same for a chain in a lookahead within a repetition that is followed by more of
        // the pattern, including a possessive repetition
        "^(?:(?!\\w+-?\\w+!)a)*b", "^(?:(?!\\w+-?\\w+!)a)*+b"
    };
    for (String p : patterns) {
      testUnsupported(p, CHAIN_UNSUPPORTED);
    }
  }

  @Test
  public void chainedRepetitionsSupported() {
    // At most two repetitions in a chain, or the repetitions are separated by chars that the
    // repetition before cannot match
    String[] patterns = {
        "^\\w+-?\\w+$", "\\w+-?\\w+!", "^\\w+-\\w+-\\w+-\\w+$", "^[a-z]+[0-9]*[a-z]+$",
        "^(\\d{1,3})\\.?(\\d{1,3})\\.?\\d{1,3}$", "^aws_.*_.*v\\d+", "^\\d+\\w+\\d+!",
        "^.*a.*b.*c$", "^.*a.b.*c.d.*e", "^x.*ab.*cd", "^\\s*\\w+\\s*=\\s*\\w+\\s*$",
        "^(?=\\w+\\w*-?\\w+)\\w", "^\\w+-?\\w+-?\\w+", "^\\w{1,5}\\w{1,5}\\w{1,5}!",
        "^a.*-?.*-?\\w+!", "^a?a?a?a?a?a?a?a?a?a?b", "^[ab]{0,3}[ab]{1,3}[ab]+c", "^(?:a|b)?(?:a|b)?c",
        "^\\w+[a-z]{1,3}c", "^a{1,3}a*b", "^a*a{1,3}b", "^[a-c]?[b-d]?[a-c]?[b-d]?\\w+-?\\w+!",
        // The trailing optional always matches, so the repetition before it is not retried
        "^\\w+-?\\w+-?\\w+-?",
        // A lookahead after a repetition that always ends at the same position
        "^(?:[a-z]+-)*(?=[a-z])",
        // A repetition that can only stop before the separator is not counted as many ways
        "^\\w+(?:-\\w+)*-\\w+$", "^\\d+(?:,\\d+)*,\\d+$", "^[a-z0-9]+(?:-[a-z0-9]+)*-v\\d+$",
        // A repetition of a portion with a single repetition, or nested repetitions, each
        // repetition consumes the chars it scans
        "^(?:\\w+\\.)*\\w+$", "^(?:.+b)*", "(?:a|(?:a|(?:a|(?:a|)*)*)*)*"
    };
    String[] values = {
        "", "a", "ab", "a-b", "a-b-c", "a-b-c-d", "ab12cd", "1.2.3", "123456789", "aws_x_v1",
        "aws_x_y_v", "1a1!", "abc", "axbzcyd", "xabcd", "xcdab", " a = b ", "a=b", "zazbzc",
        "aabbcc", "a-b!", "ab!", "1,2", "1,2,3", "a-b-v1", "a-b-c-v12", "a-v1"
    };
    for (String p : patterns) {
      for (String v : values) {
        testRE(p, v);
      }
    }
  }

  @Test
  public void chainedRepetitionsNested() {
    // The cost of a chain within a repeated portion is computed once for each portion,
    // otherwise the check would be exponential for nested repetitions
    String pattern = "^" + repeat("(?:x|", 40) + "a.b" + repeat(")*", 40);
    Assertions.assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
      PatternMatcher.compile(pattern);
    });
  }

  @Test
  public void manySequentialOptionals() {
    // Each optional portion copies the rest of the pattern, which is limited so that the
    // size does not grow exponentially. Past the limit, the optional portion would not
    // backtrack, so the pattern is not supported.
    PatternMatcher m = PatternMatcher.compile("^" + repeat("(a.*)?", 3) + "b$");
    Assertions.assertTrue(m.matches("b"));
    Assertions.assertTrue(m.matches("axb"));
    Assertions.assertTrue(m.matches("axaxaxb"));
    Assertions.assertFalse(m.matches("axbx"));
    testUnsupported("^" + repeat("(a.*)?", 30) + "b$", REPEAT_UNSUPPORTED);
    Assertions.assertTrue(
        optimizedLength("^" + repeat("(a.*)?", 30) + "b$") < 100_000, "pattern too large");
  }

  @Test
  public void manyGroupedOptionals() {
    // The rest of the pattern is merged into each optional portion before it is rewritten,
    // so the size limit applies to all of it. Otherwise, the rest would be copied into both
    // alternatives for each group and the size would double for each one. Past the limit,
    // the pattern is not supported.
    PatternMatcher m = PatternMatcher.compile("^" + repeat("(?:(a.*)?b)-", 3));
    Assertions.assertTrue(m.matches("b-b-b-"));
    Assertions.assertTrue(m.matches("b-b-axb-"));
    Assertions.assertFalse(m.matches("b-b-"));
    testUnsupported("^" + repeat("(?:(a.*)?b)-", 30), REPEAT_UNSUPPORTED);
    Assertions.assertTrue(
        optimizedLength("^" + repeat("(?:(a.*)?b)-", 30)) < 100_000, "pattern too large");
  }

  @Test
  public void nestedBacktrackingQuantifiers() {
    // The inner repetition can match different lengths, so it is not supported within
    // another repetition
    testUnsupported("^(?:(?:a|bc){1,2}x){2}$", REPEAT_UNSUPPORTED);

    // {0,1} is the same as ?
    testRE("^(?:a.*){0,1}b$", "abab");
    testRE("^(?:a.*){0,1}b$", "ab");
    testRE("^(?:a.*){0,1}b$", "b");

    // The optional portion in a group is rewritten after the rest of the pattern is merged in
    testRE("^(?:(?:a|ab)?(?:a|ab))$", "ab");
    testRE("^(?:(?:a|ab)?(?:a|ab))$", "aab");
    testRE("(?:(?:a|ab)?(?:a|ab))$", "xab");
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
  public void charClassInvalid() {
    // Like java, a backwards range, a range ending with a named class, and an intersection
    // without operands are errors
    String[] ranges = {
        "[z-a]", "[b-a]", "[a-\\]]", "[a-\\x5d]", "[a-\\t]", "[\\x7f-\\x00]", "[a-&&b]",
        "[a-\\w]", "[a-\\d]", "[a-\\s]", "[a-\\p{Lower}]"
    };
    for (String p : ranges) {
      Assertions.assertThrows(IllegalArgumentException.class, () -> Pattern.compile(p), p);
      testBadExpression(p, "illegal character range");
    }
    String[] intersections = {"[&&]", "[^&&]", "[&&&]", "[&&&a]"};
    for (String p : intersections) {
      Assertions.assertThrows(IllegalArgumentException.class, () -> Pattern.compile(p), p);
      testBadExpression(p, "bad class syntax");
    }
  }

  @Test
  public void charClassValidEdgeCases() {
    // Like java, these are valid. The negated [^&&a] is checked by negatedNestedCharClass,
    // it is limited to Java 9+ as Java 8 did not negate the intersection.
    String[] patterns = {
        "^[a-a]$", "^[\\w-a]$", "^[a&&]$", "^[&&a]$", "^[a&&&b]$", "^[a-c&&&]$",
        "^[a-c&&b&&]$", "^[a-[b]]$", "^[!-&&b]$", "^[\\]-a]$"
    };
    String[] values = {"", "a", "b", "c", "&", "-", "^", "!", "1"};
    for (String p : patterns) {
      for (String v : values) {
        testRE(p, v);
      }
    }
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
