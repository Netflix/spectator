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

import com.netflix.spectator.impl.AsciiSet;
import com.netflix.spectator.impl.PatternMatcher;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

public class PatternUtilsTest {

  /** Check that the escaped pattern is a literal that only matches the expected string. */
  private static void assertLiteral(String expected, String pattern) {
    PatternMatcher m = PatternMatcher.compile("^" + pattern + "$");
    Assertions.assertTrue(m.matches(expected), pattern);
    Assertions.assertFalse(m.matches(expected + expected), pattern);
    Assertions.assertFalse(m.matches(""), pattern);
  }

  private static void assertInvalid(String pattern) {
    Assertions.assertThrows(IllegalArgumentException.class, () -> PatternMatcher.compile(pattern));
    Assertions.assertThrows(IllegalArgumentException.class, () -> Pattern.compile(pattern));
  }

  @Test
  public void escapedControlChars() {
    assertLiteral("\t", "\\t");
    assertLiteral("\n", "\\n");
    assertLiteral("\r", "\\r");
    assertLiteral("\f", "\\f");
    assertLiteral("\u0007", "\\a");
    assertLiteral("\u001B", "\\e");
  }

  @Test
  public void escapedSlash() {
    assertLiteral("\\", "\\\\");
  }

  @Test
  public void escapedOctal() {
    for (int i = 0; i < 128; ++i) {
      assertLiteral(Character.toString((char) i), "\\0" + Integer.toString(i, 8));
    }
    assertInvalid("\\0");
    assertInvalid("\\0AAA");
  }

  @Test
  public void escapedOctalMax() {
    // Max octal value is \0377, for \0400 it is \040 followed by a literal 0
    assertLiteral("\u00FF", "\\0377");
    assertLiteral(" 0", "\\0400");
    assertLiteral("'7", "\\0477");
  }

  @Test
  public void escapedHex() {
    for (int i = 0; i <= 0xFF; ++i) {
      String expected = Character.toString((char) i);
      assertLiteral(expected, String.format("\\x%02x", i));
      assertLiteral(expected, String.format("\\x%02X", i));
      assertLiteral(expected, String.format("\\x{%x}", i));
    }
    assertLiteral("\uD83D\uDE00", "\\x{1F600}");
    assertInvalid("\\xF");
    assertInvalid("\\xZZ");
    assertInvalid("\\x+1");
    assertInvalid("\\x-1");
    assertInvalid("\\x\uFF11\uFF12");
    assertInvalid("\\x{}");
    assertInvalid("\\x{110000}");
    assertInvalid("\\x{FFFFFFFFF}");
    assertInvalid("\\x{41");
  }

  @Test
  public void escapedUnicode() {
    for (int i = 0; i <= 0xFFFF; ++i) {
      assertLiteral(Character.toString((char) i), String.format("\\u%04x", i));
    }
    assertInvalid("\\u00A");
    assertInvalid("\\uZZZZ");
    assertInvalid("\\u+02b");
    assertInvalid("\\u-02b");
  }

  @Test
  public void escapedOther() {
    assertLiteral("[", "\\[");
    assertLiteral("+", "\\+");
    assertLiteral("\u00E9", "\\\u00E9");
    assertInvalid("\\");
    assertInvalid("\\y");
    assertInvalid("\\E");
  }

  @Test
  public void removeQuotations() {
    Assertions.assertEquals(
        "\\\\x2b\\\\t\\x2b", PatternUtils.removeQuotations("\\Q\\x2b\\t\\E\\x2b"));
    Assertions.assertEquals("a\\+b", PatternUtils.removeQuotations("\\Qa+b"));
    Assertions.assertEquals("abc", PatternUtils.removeQuotations("abc"));
    // Leading digit is a hex escape so it cannot join a preceding escape, e.g. \07
    Assertions.assertEquals("\\07\\x37a8", PatternUtils.removeQuotations("\\07\\Q7a8\\E"));
  }

  private static int[] quotationPositions(String str) {
    PatternUtils.IntArrayBuilder positions = new PatternUtils.IntArrayBuilder();
    PatternUtils.removeQuotations(str, positions);
    return positions.toArray();
  }

  @Test
  public void removeQuotationsPositions() {
    // Position in the input of each char in the result, followed by the end of the input
    Assertions.assertArrayEquals(new int[] {0, 1, 2}, quotationPositions("ab"));
    Assertions.assertArrayEquals(new int[] {2, 3, 3, 6, 7, 8}, quotationPositions("\\Qa+\\E\\."));
  }

  @Test
  public void removeEmptyQuotations() {
    Assertions.assertEquals("a*?", PatternUtils.removeQuotations("a*\\Q\\E?"));
    Assertions.assertEquals("a", PatternUtils.removeQuotations("\\Q\\E\\Q\\Ea\\Q\\E"));
    Assertions.assertEquals("\\\\Q\\E", PatternUtils.removeQuotations("\\\\Q\\E"));
    Assertions.assertEquals("", PatternUtils.removeQuotations("\\Q"));
  }

  @Test
  public void regionMatchesIgnoreCase() {
    Assertions.assertTrue(PatternUtils.regionMatchesIgnoreCase("xAbC", 1, "abc"));
    Assertions.assertTrue(PatternUtils.regionMatchesIgnoreCase("x@[`{", 1, "@[`{"));
    Assertions.assertFalse(PatternUtils.regionMatchesIgnoreCase("x@[`{", 1, "`{@["));
    Assertions.assertFalse(PatternUtils.regionMatchesIgnoreCase("\u212A", 0, "k"));
    Assertions.assertFalse(PatternUtils.regionMatchesIgnoreCase("\u00C9", 0, "\u00E9"));
    Assertions.assertFalse(PatternUtils.regionMatchesIgnoreCase("ab", 1, "bc"));
    Assertions.assertFalse(PatternUtils.regionMatchesIgnoreCase("ab", -1, "a"));
    Assertions.assertTrue(PatternUtils.regionMatchesIgnoreCase("ab", 2, ""));
    // Every pair of ASCII chars, only letters that differ by case are equal
    for (char a = 0; a < 128; ++a) {
      for (char b = 0; b < 128; ++b) {
        boolean expected = Character.toLowerCase(a) == Character.toLowerCase(b);
        String sa = String.valueOf(a);
        String sb = String.valueOf(b);
        Assertions.assertEquals(expected, PatternUtils.regionMatchesIgnoreCase(sa, 0, sb), sa + sb);
      }
    }
  }

  @Test
  public void indexOfIgnoreCase() {
    Assertions.assertEquals(2, PatternUtils.indexOfIgnoreCase("xxABcab", "abc", 0));
    Assertions.assertEquals(5, PatternUtils.indexOfIgnoreCase("xxABcabc", "abc", 3));
    Assertions.assertEquals(-1, PatternUtils.indexOfIgnoreCase("xxABcab", "abc", 3));
    Assertions.assertEquals(1, PatternUtils.indexOfIgnoreCase("x1-2", "1-2", -5));
    Assertions.assertEquals(-1, PatternUtils.indexOfIgnoreCase("ab", "abc", 0));
    Assertions.assertEquals(3, PatternUtils.indexOfIgnoreCase("abc", "", 3));
    Assertions.assertEquals(-1, PatternUtils.indexOfIgnoreCase("abc", "", 4));
    Assertions.assertEquals(-1, PatternUtils.indexOfIgnoreCase("\u212Aa", "ka", 0));
    Assertions.assertEquals(1, PatternUtils.indexOfIgnoreCase("xKa", "ka", 0));
    Assertions.assertEquals(4, PatternUtils.indexOfIgnoreCase("foo-BAR", "bar", 0));
    Assertions.assertEquals(-1, PatternUtils.indexOfIgnoreCase("foo-BAR", "bar", 5));
    Assertions.assertEquals(3, PatternUtils.indexOfIgnoreCase("abc-123", "-1", 0));
    Assertions.assertEquals(-1, PatternUtils.indexOfIgnoreCase("\u212Aelvin", "kelvin", 0));
    Assertions.assertEquals(1, PatternUtils.indexOfIgnoreCase("x\u00e9", "\u00e9", 0));
    Assertions.assertEquals(-1, PatternUtils.indexOfIgnoreCase("x\u00c9", "\u00e9", 0));
  }

  @Test
  public void toOtherCase() {
    // Only ASCII letters have another case, e.g. the Latin-1 letters that differ by the same
    // bit are unchanged
    for (char c = 0; c < 256; ++c) {
      char expected = c;
      if (c >= 'a' && c <= 'z') {
        expected = Character.toUpperCase(c);
      } else if (c >= 'A' && c <= 'Z') {
        expected = Character.toLowerCase(c);
      }
      Assertions.assertEquals(expected, PatternUtils.toOtherCase(c), String.valueOf(c));
    }
  }

  @Test
  public void escapeSpecial() {
    String input = "\t\n\r\f\\^$.|?*+[](){}";
    String expected = "\\t\\n\\r\\f\\\\\\^\\$\\.\\|\\?\\*\\+\\[\\]\\(\\)\\{\\}";
    Assertions.assertEquals(expected, PatternUtils.escape(input));
  }

  @Test
  public void escapePrintable() {
    String special = "\t\n\r\f\\^$.|?*+[](){}";
    for (char i = '!'; i <= '~'; ++i) {
      if (special.indexOf(i) != -1) {
        continue;
      }
      String expected = Character.toString(i);
      Assertions.assertEquals(expected, PatternUtils.escape(expected));
    }
  }

  @Test
  public void escapeControl() {
    String special = "\t\n\r\f";
    for (int i = 0; i < '!'; ++i) {
      if (special.indexOf(i) != -1) {
        continue;
      }
      String input = Character.toString((char) i);
      String expected = String.format("\\x%02x", i);
      Assertions.assertEquals(expected, PatternUtils.escape(input));
    }
  }

  @Test
  public void escapeDelete() {
    Assertions.assertEquals("\\x7f", PatternUtils.escape("\u007f"));
  }

  @Test
  public void escapeNonAscii() {
    Assertions.assertEquals("\\x20\\xe9\u0101", PatternUtils.escape(" \u00e9\u0101"));
  }

  @Test
  public void computeTrigramsTooShort() {
    Assertions.assertEquals(Collections.emptySortedSet(), PatternUtils.computeTrigrams(""));
    Assertions.assertEquals(Collections.emptySortedSet(), PatternUtils.computeTrigrams("a"));
    Assertions.assertEquals(Collections.emptySortedSet(), PatternUtils.computeTrigrams("ab"));
  }

  private SortedSet<String> sortedSet(String... items) {
    return new TreeSet<>(Arrays.asList(items));
  }

  @Test
  public void computeTrigrams() {
    Assertions.assertEquals(sortedSet("abc"), PatternUtils.computeTrigrams("abc"));
    Assertions.assertEquals(
        sortedSet("abc", "bcd", "cde", "def", "efg"),
        PatternUtils.computeTrigrams("abcdefg")
    );
  }

  private void checkCharClassRoundTrip(AsciiSet set) {
    String regex = new CharClassMatcher(set).toString();
    Pattern javaPattern = Pattern.compile(regex);
    com.google.re2j.Pattern re2Pattern = com.google.re2j.Pattern.compile(regex);
    PatternMatcher matcher = PatternMatcher.compile("^" + regex + "$");
    for (char c = 0; c < 128; ++c) {
      String str = String.valueOf(c);
      String msg = regex + " with " + (int) c;
      Assertions.assertEquals(set.contains(c), javaPattern.matcher(str).matches(), msg);
      Assertions.assertEquals(set.contains(c), re2Pattern.matches(str), msg);
      Assertions.assertEquals(set.contains(c), matcher.matches(str), msg);
    }
  }

  @Test
  public void charClassToStringRoundTrip() {
    // Characters that are special in a class, plus neighbors of the dash so ranges
    // starting or ending at it get covered. The empty set prints as the never matching
    // class of FalseMatcher.
    String chars = " ,-./[\\]^&ab";
    int n = chars.length();
    for (int i = 0; i < (1 << n); ++i) {
      AsciiSet set = AsciiSet.none();
      for (int j = 0; j < n; ++j) {
        if ((i & (1 << j)) != 0) {
          set = set.union(AsciiSet.fromPattern(chars.substring(j, j + 1)));
        }
      }
      checkCharClassRoundTrip(set);
      checkCharClassRoundTrip(set.invert());
    }
  }

  @Test
  public void falseMatcherToString() {
    // The pattern should never match in java, RE2, or after a round trip, with or without
    // ignoring the case
    String regex = FalseMatcher.INSTANCE.toString();
    Pattern javaPattern = Pattern.compile("^.*(" + regex + ")", Pattern.DOTALL);
    Pattern javaPatternIc = Pattern.compile(
        "^.*(" + regex + ")", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    com.google.re2j.Pattern re2Pattern = com.google.re2j.Pattern.compile(regex);
    com.google.re2j.Pattern re2PatternIc = com.google.re2j.Pattern.compile("(?i)" + regex);
    PatternMatcher matcher = PatternMatcher.compile(regex);
    PatternMatcher matcherIc = PatternMatcher.compile("(?i)" + regex);
    Assertions.assertTrue(matcher.neverMatches());
    for (String v : new String[] {"", "a", "\n", "a\n", "\r\n", "\u00e9", "\ud83d\ude00"}) {
      Assertions.assertFalse(javaPattern.matcher(v).find(), v);
      Assertions.assertFalse(javaPatternIc.matcher(v).find(), v);
      Assertions.assertFalse(re2Pattern.matcher(v).find(), v);
      Assertions.assertFalse(re2PatternIc.matcher(v).find(), v);
      Assertions.assertFalse(matcher.matches(v), v);
      Assertions.assertFalse(matcherIc.matches(v), v);
    }
  }

  @Test
  public void charClassToStringDashRange() {
    AsciiSet set = AsciiSet.fromPattern("-./");
    Assertions.assertEquals("[-\\./]", new CharClassMatcher(set).toString());
  }

  @Test
  public void caseMattersAllChars() {
    // Verify the assumptions used by indexOfIgnoreCase for every char. Case only matters for
    // ASCII letters, and then only the other case of the letter can also match.
    for (int i = 0; i <= Character.MAX_VALUE; ++i) {
      char a = (char) i;
      Assertions.assertEquals(PatternUtils.toOtherCase(a) != a, PatternUtils.caseMatters(a));
    }
    String[] ascii = new String[128];
    for (char a = 0; a < 128; ++a) {
      ascii[a] = String.valueOf(a);
    }
    for (int i = 0; i <= Character.MAX_VALUE; ++i) {
      char c = (char) i;
      String cs = String.valueOf(c);
      for (char a = 0; a < 128; ++a) {
        boolean expected = c == a || c == PatternUtils.toOtherCase(a);
        if (expected != PatternUtils.regionMatchesIgnoreCase(cs, 0, ascii[a])) {
          Assertions.fail("match: " + (int) c + " ~ " + (int) a);
        }
      }
    }
  }

  @Test
  public void caseMattersString() {
    Assertions.assertFalse(PatternUtils.caseMatters(""));
    Assertions.assertFalse(PatternUtils.caseMatters("0123-456.789_[@]`{~}"));
    Assertions.assertTrue(PatternUtils.caseMatters("0123a"));
    Assertions.assertTrue(PatternUtils.caseMatters("Z"));
    Assertions.assertFalse(PatternUtils.caseMatters("123\u00e9"));
  }

  private static int referenceIndexOfIgnoreCase(String str, String pattern, int offset) {
    final int end = str.length() - pattern.length();
    for (int i = offset; i <= end; ++i) {
      if (PatternUtils.regionMatchesIgnoreCase(str, i, pattern)) {
        return i;
      }
    }
    return -1;
  }

  @Test
  public void indexOfIgnoreCaseMatchesReference() {
    // Alphabet includes chars that match ASCII letters with Unicode case folding, but not
    // with ASCII only folding: Kelvin sign, long s, and dotted capital I.
    char[] alphabet = "aAbBkKsSiI01-_.\u212A\u017F\u0130\u00e9\u00c9".toCharArray();
    java.util.Random r = new java.util.Random(42);
    for (int i = 0; i < 100_000; ++i) {
      String str = randomString(r, alphabet, r.nextInt(20));
      String pattern = randomString(r, alphabet, 1 + r.nextInt(4));
      int offset = r.nextInt(str.length() + 2);
      Assertions.assertEquals(
          referenceIndexOfIgnoreCase(str, pattern, offset),
          PatternUtils.indexOfIgnoreCase(str, pattern, offset),
          () -> "indexOfIgnoreCase(\"" + str + "\", \"" + pattern + "\", " + offset + ")");
    }
  }

  private static String randomString(java.util.Random r, char[] alphabet, int n) {
    StringBuilder builder = new StringBuilder(n);
    for (int i = 0; i < n; ++i) {
      builder.append(alphabet[r.nextInt(alphabet.length)]);
    }
    return builder.toString();
  }

  private static Matcher optimized(String pattern) {
    return Optimizer.optimize(Parser.parse(pattern, 0));
  }

  @Test
  public void hasFixedEnd() {
    String[] fixed = {"abc", "(?:ab|ac)", "[ab]{3}", "x.*a$", "(?:a$|bc$)", "(?:ab|cd){2}"};
    for (String p : fixed) {
      Assertions.assertTrue(PatternUtils.hasFixedEnd(optimized(p)), p);
    }
    String[] notFixed = {"a|ab", "a+", "a{1,3}", "\\d{1,3}\\.", "[a-z]+-", "a?b", "(?:ods|postgres)"};
    for (String p : notFixed) {
      Assertions.assertFalse(PatternUtils.hasFixedEnd(optimized(p)), p);
    }
  }
}
