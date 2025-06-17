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
      String expected = Parser.isSpecial(i) ? "\\u" + zeroPad(i) : str;
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
}
