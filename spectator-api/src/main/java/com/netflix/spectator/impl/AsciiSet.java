/*
 * Copyright 2014-2017 Netflix, Inc.
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
package com.netflix.spectator.impl;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;

/**
 * Utility class for quickly checking if a string contains only characters contained within
 * the given set. The set is limited to basic ascii with ranges, if you need more advanced
 * patterns then regular expressions are a better option though it will likely come with a
 * steep performance penalty.
 *
 * <p><b>This class is an internal implementation detail only intended for use within spectator.
 * It is subject to change without notice.</b></p>
 */
public final class AsciiSet {

  private static final MethodHandle STRING_CONSTRUCTOR;
  static {
    MethodHandle handle;
    try {
      Constructor<String> ctor = String.class.getDeclaredConstructor(char[].class, boolean.class);
      ctor.setAccessible(true);
      handle = MethodHandles.lookup().unreflectConstructor(ctor);
    } catch (Exception e) {
      handle = null;
    }
    STRING_CONSTRUCTOR = handle;
  }

  /**
   * Creates a new string without copying the buffer if possible. The String class has a
   * package private constructor that allows the buffer to be shared.
   */
  @SuppressWarnings("PMD.AvoidCatchingThrowable")
  private static String newString(char[] buf) {
    if (STRING_CONSTRUCTOR != null) {
      try {
        return (String) STRING_CONSTRUCTOR.invokeExact(buf, true);
      } catch (Throwable t) {
        // Note: `invokeExact` explicitly throws Throwable to propagate any exception of the
        // method unchanged. For our purposes we just fallback to the string constructor.
        return new String(buf);
      }
    } else {
      return new String(buf);
    }
  }

  /**
   * Create a set containing ascii characters using a simple pattern. The pattern is similar
   * to a character set in regex. For example, {@code ABC} would contain the characters
   * {@code A}, {@code B}, and {@code C}. Ranges are supported so all uppercase letters could
   * be specified as {@code A-Z}. The dash, {@code -}, will be included as part of the set if
   * it is at the start or end of the pattern.
   *
   * @param pattern
   *     String specification of the character set.
   * @return
   *     Set containing the characters specified in {@code pattern}.
   */
  public static AsciiSet fromPattern(String pattern) {
    final boolean[] members = new boolean[128];
    final int n = pattern.length();
    for (int i = 0; i < n; ++i) {
      final char c = pattern.charAt(i);
      if (c >= members.length) {
        throw new IllegalArgumentException("invalid pattern, '" + c + "' is not ascii");
      }

      final boolean isStartOrEnd = i == 0 || i == n - 1;
      if (isStartOrEnd || c != '-') {
        members[c] = true;
      } else {
        final char s = pattern.charAt(i - 1);
        final char e = pattern.charAt(i + 1);
        for (char v = s; v <= e; ++v) {
          members[v] = true;
        }
      }
    }
    return new AsciiSet(members);
  }

  /**
   * Converts the members array to a pattern string. Used to provide a user friendly toString
   * implementation for the set.
   */
  private static String toPattern(boolean[] members) {
    StringBuilder buf = new StringBuilder();
    boolean previous = false;
    char s = 0;
    for (int i = 0; i < members.length; ++i) {
      if (members[i] && !previous) {
        s = (char) i;
      } else if (!members[i] && previous) {
        final char e = (char) (i - 1);
        append(buf, s, e);
      }
      previous = members[i];
    }
    if (previous) {
      final char e = (char) (members.length - 1);
      append(buf, s, e);
    }
    return buf.toString();
  }

  private static void append(StringBuilder buf, char s, char e) {
    switch (e - s) {
      case 0:  buf.append(s);                       break;
      case 1:  buf.append(s).append(e);             break;
      default: buf.append(s).append('-').append(e); break;
    }
  }

  private final String pattern;
  private final boolean[] members;

  private AsciiSet(boolean[] members) {
    this.members = Preconditions.checkNotNull(members, "members array cannot be null");
    this.pattern = toPattern(members);
  }

  /**
   * Returns true if the character contained within the set. This is a constant time
   * operation.
   */
  public boolean contains(char c) {
    return c < 128 && members[c];
  }

  /**
   * Returns true if all characters in the string are contained within the set.
   */
  public boolean containsAll(CharSequence str) {
    final int n = str.length();
    for (int i = 0; i < n; ++i) {
      if (!contains(str.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Replace all characters in the input string with the replacement character.
   */
  public String replaceNonMembers(String input, char replacement) {
    if (!contains(replacement)) {
      throw new IllegalArgumentException(replacement + " is not a member of " + pattern);
    }
    return containsAll(input) ? input : replaceNonMembersImpl(input, replacement);
  }

  private String replaceNonMembersImpl(String input, char replacement) {
    final int n = input.length();
    final char[] buf = input.toCharArray();
    for (int i = 0; i < n; ++i) {
      final char c = buf[i];
      if (!contains(c)) {
        buf[i] = replacement;
      }
    }
    return newString(buf);
  }

  @Override public String toString() {
    return pattern;
  }

  @Override public int hashCode() {
    return pattern.hashCode();
  }

  @Override public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || !(obj instanceof AsciiSet)) return false;
    AsciiSet other = (AsciiSet) obj;
    return pattern.equals(other.pattern);
  }
}
