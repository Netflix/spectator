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

  @Test
  public void expandTab() {
    Assertions.assertEquals("\t", PatternUtils.expandEscapedChars("\\t"));
  }

  @Test
  public void expandNewLine() {
    Assertions.assertEquals("\n", PatternUtils.expandEscapedChars("\\n"));
  }

  @Test
  public void expandCarriageReturn() {
    Assertions.assertEquals("\r", PatternUtils.expandEscapedChars("\\r"));
  }

  @Test
  public void expandFormFeed() {
    Assertions.assertEquals("\f", PatternUtils.expandEscapedChars("\\f"));
  }

  @Test
  public void expandAlert() {
    Assertions.assertEquals("\u0007", PatternUtils.expandEscapedChars("\\a"));
  }

  @Test
  public void expandEscape() {
    Assertions.assertEquals("\u001B", PatternUtils.expandEscapedChars("\\e"));
  }

  @Test
  public void expandSlash() {
    Assertions.assertEquals("\\\\", PatternUtils.expandEscapedChars("\\\\"));
  }

  @Test
  public void expandOctalEmpty() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> PatternUtils.expandEscapedChars("\\0"));
  }

  @Test
  public void expandOctal() {
    for (int i = 0; i < 128; ++i) {
      String expected = literal(i);
      String str = "\\0" + Integer.toString(i, 8);
      Assertions.assertEquals(expected, PatternUtils.expandEscapedChars(str));
    }
  }

  @Test
  public void expandOctalInvalid() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> PatternUtils.expandEscapedChars("\\0AAA"));
  }

  @Test
  public void expandOctalMax() {
    // Max octal value is \0377, for \0400 it is \040 followed by a literal 0
    Assertions.assertEquals("ÿ",PatternUtils.expandEscapedChars("\\0377"));
    Assertions.assertEquals(" 0", PatternUtils.expandEscapedChars("\\0400"));
    Assertions.assertEquals("'7", PatternUtils.expandEscapedChars("\\0477"));
  }

  private static String literal(int i) {
    String c = Character.toString((char) i);
    return "\\^$.|?*+()[]{}-&".contains(c) ? "\\" + c : c;
  }

  @Test
  public void expandHexLower() {
    for (int i = 0; i < 0xFF; ++i) {
      String expected = literal(i);
      String str = String.format("\\x%02x", i);
      Assertions.assertEquals(expected, PatternUtils.expandEscapedChars(str));
    }
  }

  @Test
  public void expandHexUpper() {
    for (int i = 0; i < 0xFF; ++i) {
      String expected = literal(i);
      String str = String.format("\\x%02X", i);
      Assertions.assertEquals(expected, PatternUtils.expandEscapedChars(str));
    }
  }

  @Test
  public void expandHexPartial() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> PatternUtils.expandEscapedChars("\\xF"));
  }

  @Test
  public void expandHexInvalid() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> PatternUtils.expandEscapedChars("\\xZZ"));
  }

  @Test
  public void expandHexSign() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> PatternUtils.expandEscapedChars("\\x+1"));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> PatternUtils.expandEscapedChars("\\x-1"));
  }

  @Test
  public void expandHexNonAsciiDigit() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> PatternUtils.expandEscapedChars("\\x\uFF11\uFF12"));
  }

  @Test
  public void expandUnicode() {
    for (int i = 0; i < 0xFFFF; ++i) {
      String expected = literal(i);
      String str = String.format("\\u%04x", i);
      Assertions.assertEquals(expected, PatternUtils.expandEscapedChars(str));
    }
  }

  @Test
  public void expandQuotation() {
    Assertions.assertEquals(
        "\\Q\\x2b\\t\\E\\+",
        PatternUtils.expandEscapedChars("\\Q\\x2b\\t\\E\\x2b"));
    Assertions.assertEquals("\\Q\\x2b", PatternUtils.expandEscapedChars("\\Q\\x2b"));
  }

  @Test
  public void expandUnicodePartial() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> PatternUtils.expandEscapedChars("\\u00A"));
  }

  @Test
  public void expandUnicodeInvalid() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> PatternUtils.expandEscapedChars("\\uZZZZ"));
  }

  @Test
  public void expandUnicodeSign() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> PatternUtils.expandEscapedChars("\\u+02b"));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> PatternUtils.expandEscapedChars("\\u-02b"));
  }

  @Test
  public void danglingEscape() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> PatternUtils.expandEscapedChars("\\"));
  }

  @Test
  public void other() {
    Assertions.assertEquals("\\[", PatternUtils.expandEscapedChars("\\["));
  }

  @Test
  public void nonEscapedData() {
    Assertions.assertEquals("abc", PatternUtils.expandEscapedChars("abc"));
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
    // starting or ending at it get covered.
    // The empty set is skipped, it prints as a pattern that never matches rather than a class.
    String chars = " ,-./[\\]^&ab";
    int n = chars.length();
    for (int i = 1; i < (1 << n); ++i) {
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
  public void charClassToStringDashRange() {
    AsciiSet set = AsciiSet.fromPattern("-./");
    Assertions.assertEquals("[-\\./]", new CharClassMatcher(set).toString());
  }
}
