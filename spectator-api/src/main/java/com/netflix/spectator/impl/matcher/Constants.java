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

import com.netflix.spectator.impl.AsciiSet;

import java.util.HashMap;
import java.util.Map;

/** Constants that are used by the various matchers. */
final class Constants {

  private Constants() {
  }

  /** Position returned to indicate that there is no match. */
  static final int NO_MATCH = -1;

  /** Set of lower case alphabet characters. */
  static final AsciiSet LOWER = AsciiSet.fromPattern("a-z");

  /** Set of upper case alphabet characters. */
  static final AsciiSet UPPER = AsciiSet.fromPattern("A-Z");

  /** Set of all ASCII characters. */
  static final AsciiSet ASCII = AsciiSet.all();

  /** Set of lower and upper case alphabet characters. */
  static final AsciiSet ALPHA = LOWER.union(UPPER);

  /** Set of decimal digit characters. */
  static final AsciiSet DIGIT = AsciiSet.fromPattern("0-9");

  /** Set containing the union of {@link #ALPHA} and {@link #DIGIT}. */
  static final AsciiSet ALNUM = ALPHA.union(DIGIT);

  /** Set containing the common punctuation characters. */
  static final AsciiSet PUNCT = AsciiSet.fromPattern("-!\"#$%&'()*+,./:;<=>?@[\\]^_`{|}~");

  /**
   * Set containing the visible characters ({@link #ALPHA}, {@link #DIGIT}, and  {@link #PUNCT}).
   */
  static final AsciiSet GRAPH = PUNCT.union(ALNUM);

  /** Set containing the printable characters. */
  static final AsciiSet PRINT = GRAPH.union(AsciiSet.fromPattern(" "));

  /** Set containing space and tab. */
  static final AsciiSet BLANK = AsciiSet.fromPattern(" \t");

  /** Set containing the ASCII control characters. */
  static final AsciiSet CNTRL = AsciiSet.control();

  /** Set of hexadecimal digit characters. */
  static final AsciiSet XDIGIT = AsciiSet.fromPattern("0-9a-fA-F");

  /** Set containing the word characters. */
  static final AsciiSet WORD_CHARS = AsciiSet.fromPattern("a-zA-Z_0-9");

  /** Set containing the whitespace characters. */
  static final AsciiSet SPACE = AsciiSet.fromPattern(" \t\n\u000B\f\r");

  /** Character classes that can be looked up by name. */
  static final Map<String, AsciiSet> NAMED_CHAR_CLASSES = new HashMap<>();
  static {
    NAMED_CHAR_CLASSES.put("Lower", LOWER);
    NAMED_CHAR_CLASSES.put("Upper", UPPER);
    NAMED_CHAR_CLASSES.put("ASCII", ASCII);
    NAMED_CHAR_CLASSES.put("Alpha", ALPHA);
    NAMED_CHAR_CLASSES.put("Digit", DIGIT);
    NAMED_CHAR_CLASSES.put("Alnum", ALNUM);
    NAMED_CHAR_CLASSES.put("Punct", PUNCT);
    NAMED_CHAR_CLASSES.put("Graph", GRAPH);
    NAMED_CHAR_CLASSES.put("Print", PRINT);
    NAMED_CHAR_CLASSES.put("Blank", BLANK);
    NAMED_CHAR_CLASSES.put("Cntrl", CNTRL);
    NAMED_CHAR_CLASSES.put("XDigit", XDIGIT);
    NAMED_CHAR_CLASSES.put("Space", SPACE);
  }
}
