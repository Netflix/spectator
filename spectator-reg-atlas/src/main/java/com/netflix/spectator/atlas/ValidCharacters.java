/*
 * Copyright 2014-2016 Netflix, Inc.
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
package com.netflix.spectator.atlas;

/**
 * Utility class to deal with rewriting keys/values to the character set accepted by atlas.
 */
public final class ValidCharacters {
  /** Only allow letters, numbers, underscores, dashes and dots. */
  private static final boolean[] CHARS_ALLOWED = new boolean[128];

  static {
    CHARS_ALLOWED['.'] = true;
    CHARS_ALLOWED['-'] = true;
    CHARS_ALLOWED['_'] = true;
    for (char ch = '0'; ch <= '9'; ch++) {
      CHARS_ALLOWED[ch] = true;
    }
    for (char ch = 'A'; ch <= 'Z'; ch++) {
      CHARS_ALLOWED[ch] = true;
    }
    for (char ch = 'a'; ch <= 'z'; ch++) {
      CHARS_ALLOWED[ch] = true;
    }
  }

  private ValidCharacters() {
  }

  /** Convert a given string to one where all characters are valid. */
  public static String toValidCharset(String str) {
    final int n = str.length();
    final StringBuilder buf = new StringBuilder(n + 1);
    for (int i = 0; i < n; i++) {
      final char c = str.charAt(i);
      if (c < CHARS_ALLOWED.length && CHARS_ALLOWED[c]) {
        buf.append(c);
      } else {
        buf.append('_');
      }
    }
    return buf.toString();
  }
}
