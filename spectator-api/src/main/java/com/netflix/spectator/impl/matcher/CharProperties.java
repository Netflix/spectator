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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Names of the character properties that java supports, e.g. {@code \p{L}} or
 * {@code \p{IsLatin}}. They are not supported by the matcher, the names are only used to
 * report a valid name as unsupported and an invalid name as an error, the same as java.
 */
final class CharProperties {

  private CharProperties() {
  }

  /** General categories and other names that can be used without a prefix. */
  private static final Set<String> CATEGORIES = new HashSet<>(Arrays.asList(
      "Cn", "Lu", "Ll", "Lt", "Lm", "Lo", "Mn", "Me", "Mc", "Nd", "Nl", "No", "Zs", "Zl", "Zp",
      "Cc", "Cf", "Co", "Cs", "Pd", "Ps", "Pe", "Pc", "Po", "Sm", "Sc", "Sk", "So", "Pi", "Pf",
      "L", "M", "N", "Z", "C", "P", "S", "LC", "LD", "L1", "all", "ASCII", "Alnum", "Alpha",
      "Blank", "Cntrl", "Digit", "Graph", "Lower", "Print", "Punct", "Space", "Upper",
      "XDigit"));

  /** Properties based on the methods of {@link Character}, e.g. {@code \p{javaLowerCase}}. */
  private static final Set<String> JAVA = new HashSet<>(Arrays.asList(
      "javaLowerCase", "javaUpperCase", "javaAlphabetic", "javaIdeographic", "javaTitleCase",
      "javaDigit", "javaDefined", "javaLetter", "javaLetterOrDigit", "javaJavaIdentifierStart",
      "javaJavaIdentifierPart", "javaUnicodeIdentifierStart", "javaUnicodeIdentifierPart",
      "javaIdentifierIgnorable", "javaSpaceChar", "javaWhitespace", "javaISOControl",
      "javaMirrored"));

  /**
   * Binary properties and POSIX names that can be used with the Is prefix, the case is
   * ignored, e.g. {@code \p{IsWord}} or {@code \p{IsALPHA}}.
   */
  private static final Set<String> BINARY = new HashSet<>(Arrays.asList(
      "ALPHABETIC", "ASSIGNED", "CONTROL", "DIGIT", "EMOJI", "EMOJI_COMPONENT",
      "EMOJI_MODIFIER", "EMOJI_MODIFIER_BASE", "EMOJI_PRESENTATION", "EXTENDED_PICTOGRAPHIC",
      "HEXDIGIT", "HEX_DIGIT", "IDEOGRAPHIC", "JOINCONTROL", "JOIN_CONTROL", "LETTER",
      "LOWERCASE", "NONCHARACTERCODEPOINT", "NONCHARACTER_CODE_POINT", "PUNCTUATION",
      "TITLECASE", "UPPERCASE", "WHITESPACE", "WHITE_SPACE", "WORD", "ALNUM", "ALPHA",
      "BLANK", "CNTRL", "GRAPH", "LOWER", "PRINT", "PUNCT", "SPACE", "UPPER", "XDIGIT"));

  /** Returns true if java supports the property name for {@code \p{name}}. */
  static boolean isValid(String name) {
    int eq = name.indexOf('=');
    if (eq > 0) {
      // Like java, the case is ignored for the key, but not for the value
      String key = name.substring(0, eq).toLowerCase(Locale.ENGLISH);
      String value = name.substring(eq + 1);
      switch (key) {
        case "sc":
        case "script":
          return isScript(value);
        case "blk":
        case "block":
          return isBlock(value);
        case "gc":
        case "general_category":
          return isProperty(value);
        default:
          return false;
      }
    } else if (name.startsWith("In")) {
      return isBlock(name.substring(2));
    } else if (name.startsWith("Is")) {
      String n = name.substring(2);
      return isProperty(n)
          || BINARY.contains(n.toUpperCase(Locale.ENGLISH))
          || isScript(n);
    }
    return isProperty(name);
  }

  /** Returns true if it is a category, POSIX name, or java property, the case must match. */
  private static boolean isProperty(String name) {
    return CATEGORIES.contains(name) || JAVA.contains(name);
  }

  private static boolean isScript(String name) {
    try {
      Character.UnicodeScript.forName(name);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private static boolean isBlock(String name) {
    try {
      Character.UnicodeBlock.forName(name);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
