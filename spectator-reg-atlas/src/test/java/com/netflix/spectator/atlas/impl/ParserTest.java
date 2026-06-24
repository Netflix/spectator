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
package com.netflix.spectator.atlas.impl;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ParserTest {

  private static String zeroPad(int i) {
    StringBuilder builder = new StringBuilder();
    Parser.zeroPad(Integer.toHexString(i), builder);
    return builder.toString();
  }

  @Test
  public void escape() {
    for (char i = 0; i < Short.MAX_VALUE; ++i) {
      String str = Character.toString(i);
      final String expected;
      if ("(".equals(str)) {
        // A standalone parenthesis is structural and is always escaped when used as a value.
        expected = "\\u0028";
      } else if (")".equals(str)) {
        expected = "\\u0029";
      } else {
        expected = Parser.isSpecial(i) ? "\\u" + zeroPad(i) : str;
      }
      Assertions.assertEquals(expected, Parser.escape(str));
    }
  }

  @Test
  public void unescape() {
    for (char i = 0; i < Short.MAX_VALUE; ++i) {
      String str = Character.toString(i);
      String escaped = "\\u" + zeroPad(i);
      Assertions.assertEquals(str, Parser.unescape(escaped));
    }
  }

  @Test
  public void unescapeTooShort() {
    String str = "foo\\u000";
    Assertions.assertEquals(str, Parser.unescape(str));
  }

  @Test
  public void unescapeUnknownType() {
    String str = "foo\\x0000";
    Assertions.assertEquals(str, Parser.unescape(str));
  }

  @Test
  public void unescapeInvalid() {
    String str = "foo\\uzyff";
    Assertions.assertEquals(str, Parser.unescape(str));
  }

  private void assertRoundTrip(String value) {
    Assertions.assertEquals(value, Parser.unescape(Parser.escape(value)));
  }

  @Test
  public void escapeBackslashBeginningUnicodeEscape() {
    // A value literally containing "\\u002c" must have its backslash escaped so unescape does
    // not decode it back into a comma. Cover the escape both mid-string and at the end.
    Assertions.assertEquals("a\\u005cu002cb", Parser.escape("a\\u002cb"));
    Assertions.assertEquals("x\\u005cu002c", Parser.escape("x\\u002c"));
    assertRoundTrip("a\\u002cb");
    assertRoundTrip("\\u002c");
  }

  @Test
  public void escapeBackslashNotBeginningUnicodeEscape() {
    // Backslashes that do not begin a unicode escape are left readable (e.g. regex-style "\\d").
    Assertions.assertEquals("\\d", Parser.escape("\\d"));
    assertRoundTrip("\\d");
    assertRoundTrip("C:\\users\\foo");
  }

  @Test
  public void escapeBackslashAdjacentToSpecialChar() {
    // A literal backslash immediately before a character that escape() renders as a \\uXXXX
    // sequence (here the comma -> \\u002c) must stay a literal backslash and round-trip; it
    // must not be merged with the following escape when unescaping.
    Assertions.assertEquals("\\\\u002c", Parser.escape("\\,"));
    assertRoundTrip("\\,");
  }

  @Test
  public void escapeIncompleteUnicodeEscape() {
    // Trailing or non-hex sequences are not unicode escapes: escape() leaves them unchanged
    // (so this also pins the length boundary, e.g. "\\u002" has too few chars) and they
    // round-trip.
    for (String v : new String[] {"foo\\u", "foo\\u00", "foo\\u002", "foo\\uzzzz"}) {
      Assertions.assertEquals(v, Parser.escape(v));
      assertRoundTrip(v);
    }
  }

  @Test
  public void escapeRoundTripSpecialChars() {
    assertRoundTrip("a,b");
    assertRoundTrip("a:b");
    assertRoundTrip("a b");
    assertRoundTrip("(");
    assertRoundTrip(")");
  }

  @Test
  public void escapeSupplementaryCodePoint() {
    // Characters above the BMP are not special, so escape() passes them through unchanged
    // (they are never routed through escapeCodePoint) and they round-trip via unescape. This
    // is why the surrogate-pair handling needed in the Atlas Strings escaper is not required
    // here.
    String emoji = "a😀b"; // U+1F600
    Assertions.assertEquals(emoji, Parser.escape(emoji));
    assertRoundTrip(emoji);
    // Adjacent to a special character that does get escaped.
    assertRoundTrip("a😀,b");
  }

  @Test
  public void unescapeSupplementaryCodePoint() {
    // An above-BMP character encoded as a surrogate pair of \\uXXXX escapes (the form produced
    // by the Atlas Strings escaper) must unescape back to the original code point.
    Assertions.assertEquals("😀", Parser.unescape("\\ud83d\\ude00")); // U+1F600
    Assertions.assertEquals("a😀b", Parser.unescape("a\\ud83d\\ude00b"));
  }
}
