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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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
      String expected = Character.toString((char) i);
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
  public void expandHexLower() {
    for (int i = 0; i < 0xFF; ++i) {
      String expected = Character.toString((char) i);
      String str = String.format("\\x%02x", i);
      Assertions.assertEquals(expected, PatternUtils.expandEscapedChars(str));
    }
  }

  @Test
  public void expandHexUpper() {
    for (int i = 0; i < 0xFF; ++i) {
      String expected = Character.toString((char) i);
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
  public void expandUnicode() {
    for (int i = 0; i < 0xFFFF; ++i) {
      String expected = Character.toString((char) i);
      String str = String.format("\\u%04x", i);
      Assertions.assertEquals(expected, PatternUtils.expandEscapedChars(str));
    }
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
    String input = "\t\n\r\f\\^$.?*+[](){}";
    String expected = "\\t\\n\\r\\f\\\\\\^\\$\\.\\?\\*\\+\\[\\]\\(\\)\\{\\}";
    Assertions.assertEquals(expected, PatternUtils.escape(input));
  }

  @Test
  public void escapePrintable() {
    String special = "\t\n\r\f\\^$.?*+[](){}";
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
      String expected = String.format("\\u%04x", i);
      Assertions.assertEquals(expected, PatternUtils.escape(input));
    }
  }

  @Test
  public void escapeDelete() {
    Assertions.assertEquals("\\u007f", PatternUtils.escape("\u007f"));
  }
}
