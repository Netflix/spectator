/*
 * Copyright 2014-2018 Netflix, Inc.
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

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class PatternUtilsTest {

  @Test
  public void expandTab() {
    Assert.assertEquals("\t", PatternUtils.expandEscapedChars("\\t"));
  }

  @Test
  public void expandNewLine() {
    Assert.assertEquals("\n", PatternUtils.expandEscapedChars("\\n"));
  }

  @Test
  public void expandCarriageReturn() {
    Assert.assertEquals("\r", PatternUtils.expandEscapedChars("\\r"));
  }

  @Test
  public void expandFormFeed() {
    Assert.assertEquals("\f", PatternUtils.expandEscapedChars("\\f"));
  }

  @Test
  public void expandAlert() {
    Assert.assertEquals("\u0007", PatternUtils.expandEscapedChars("\\a"));
  }

  @Test
  public void expandEscape() {
    Assert.assertEquals("\u001B", PatternUtils.expandEscapedChars("\\e"));
  }

  @Test
  public void expandSlash() {
    Assert.assertEquals("\\\\", PatternUtils.expandEscapedChars("\\\\"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void expandOctalEmpty() {
    PatternUtils.expandEscapedChars("\\0");
  }

  @Test
  public void expandOctal() {
    for (int i = 0; i < 128; ++i) {
      String expected = Character.toString((char) i);
      String str = "\\0" + Integer.toString(i, 8);
      Assert.assertEquals(expected, PatternUtils.expandEscapedChars(str));
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void expandOctalInvalid() {
    PatternUtils.expandEscapedChars("\\0AAA");
  }

  @Test
  public void expandHexLower() {
    for (int i = 0; i < 0xFF; ++i) {
      String expected = Character.toString((char) i);
      String str = String.format("\\x%02x", i);
      Assert.assertEquals(expected, PatternUtils.expandEscapedChars(str));
    }
  }

  @Test
  public void expandHexUpper() {
    for (int i = 0; i < 0xFF; ++i) {
      String expected = Character.toString((char) i);
      String str = String.format("\\x%02X", i);
      Assert.assertEquals(expected, PatternUtils.expandEscapedChars(str));
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void expandHexPartial() {
    PatternUtils.expandEscapedChars("\\xF");
  }

  @Test(expected = IllegalArgumentException.class)
  public void expandHexInvalid() {
    PatternUtils.expandEscapedChars("\\xZZ");
  }

  @Test
  public void expandUnicode() {
    for (int i = 0; i < 0xFFFF; ++i) {
      String expected = Character.toString((char) i);
      String str = String.format("\\u%04x", i);
      Assert.assertEquals(expected, PatternUtils.expandEscapedChars(str));
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void expandUnicodePartial() {
    PatternUtils.expandEscapedChars("\\u00A");
  }

  @Test(expected = IllegalArgumentException.class)
  public void expandUnicodeInvalid() {
    PatternUtils.expandEscapedChars("\\uZZZZ");
  }

  @Test(expected = IllegalArgumentException.class)
  public void danglingEscape() {
    PatternUtils.expandEscapedChars("\\");
  }

  @Test
  public void other() {
    Assert.assertEquals("\\[", PatternUtils.expandEscapedChars("\\["));
  }

  @Test
  public void nonEscapedData() {
    Assert.assertEquals("abc", PatternUtils.expandEscapedChars("abc"));
  }

  @Test
  public void escapeSpecial() {
    String input = "\t\n\r\f\\^$.?*+[](){}";
    String expected = "\\t\\n\\r\\f\\\\\\^\\$\\.\\?\\*\\+\\[\\]\\(\\)\\{\\}";
    Assert.assertEquals(expected, PatternUtils.escape(input));
  }

  @Test
  public void escapePrintable() {
    String special = "\t\n\r\f\\^$.?*+[](){}";
    for (char i = '!'; i <= '~'; ++i) {
      if (special.indexOf(i) != -1) {
        continue;
      }
      String expected = Character.toString(i);
      Assert.assertEquals(expected, PatternUtils.escape(expected));
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
      Assert.assertEquals(expected, PatternUtils.escape(input));
    }
  }

  @Test
  public void escapeDelete() {
    Assert.assertEquals("\\u007f", PatternUtils.escape("\u007f"));
  }
}
