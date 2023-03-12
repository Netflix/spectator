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
package com.netflix.spectator.impl;

import java.io.Serializable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.util.BitSet;
import java.util.Optional;

/**
 * Utility class for quickly checking if a string contains only characters contained within
 * the given set. The set is limited to basic ascii with ranges, if you need more advanced
 * patterns then regular expressions are a better option though it will likely come with a
 * steep performance penalty.
 *
 * <p><b>This class is an internal implementation detail only intended for use within spectator.
 * It is subject to change without notice.</b></p>
 */
public final class AsciiSet implements Serializable {

  private static final long serialVersionUID = 1L;

  // The maximum value of an ASCII character.
  static final char ASCII_MAX = 127;

  static final int MAX_BITS = ASCII_MAX + 1;

  private static boolean isJava8() {
    String version = System.getProperty("java.version", "1.8");
    return version.startsWith("1.8");
  }

  private static final MethodHandle STRING_CONSTRUCTOR;
  static {
    if (isJava8()) {
      MethodHandle handle;
      try {
        Constructor<String> ctor = String.class.getDeclaredConstructor(char[].class, boolean.class);
        ctor.setAccessible(true);
        handle = MethodHandles.lookup().unreflectConstructor(ctor);
      } catch (Exception e) {
        handle = null;
      }
      STRING_CONSTRUCTOR = handle;
    } else {
      STRING_CONSTRUCTOR = null;
    }
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
    final BitSet members = new BitSet();
    final int n = pattern.length();
    for (int i = 0; i < n; ++i) {
      final char c = pattern.charAt(i);
      if (c > ASCII_MAX) {
        throw new IllegalArgumentException("invalid pattern, '" + c + "' is not ascii");
      }

      final boolean isStartOrEnd = i == 0 || i == n - 1;
      if (isStartOrEnd || c != '-') {
        members.set(c);
      } else {
        final char s = pattern.charAt(i - 1);
        final char e = pattern.charAt(i + 1);
        for (char v = s; v <= e; ++v) {
          members.set(v);
        }
      }
    }
    return new AsciiSet(members);
  }

  /** Returns a set that matches no characters. */
  public static AsciiSet none() {
    return new AsciiSet(new BitSet(0));
  }

  /** Returns a set that matches all ascii characters. */
  public static AsciiSet all() {
    final BitSet members = new BitSet(MAX_BITS);
    members.set(0, MAX_BITS, true);
    return new AsciiSet(members);
  }

  /** Returns a set that matches ascii control characters. */
  public static AsciiSet control() {
    final BitSet members = new BitSet();
    for (char c = 0; c <= ASCII_MAX; c++) {
      if (Character.isISOControl(c)) {
        members.set(c);
      }
    }
    return new AsciiSet(members);
  }

  /**
   * Converts the members array to a pattern string. Used to provide a user friendly toString
   * implementation for the set.
   */
  private static String toPattern(BitSet members) {
    StringBuilder buf = new StringBuilder();
    if (members.get('-')) {
      buf.append('-');
    }
    boolean previous = false;
    char s = 0;

    for (char i = 0; i <= ASCII_MAX; ++i) {
      boolean isSet = members.get(i);
      if (isSet && !previous) {
        s = i;
      } else if (!isSet && previous) {
        final char e = (char) (i - 1);
        append(buf, s, e);
      }
      previous = isSet;
    }
    if (previous) {
      append(buf, s, ASCII_MAX);
    }
    return buf.toString();
  }

  private static void append(StringBuilder buf, char s, char e) {
    switch (e - s) {
      case 0:  if (s != '-') buf.append(s);         break;
      case 1:  buf.append(s).append(e);             break;
      default: buf.append(s).append('-').append(e); break;
    }
  }

  private final String pattern;
  private final BitSet members;

  private AsciiSet(BitSet members) {
    this.members = Preconditions.checkNotNull(members, "members array cannot be null");
    this.pattern = toPattern(members);
  }

  /**
   * Returns true if the character contained within the set. This is a constant time
   * operation.
   */
  public boolean contains(char c) {
    return members.get(c);
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

  /**
   * Returns a new set that will match characters either in the this set or in the
   * set that is provided.
   */
  public AsciiSet union(AsciiSet set) {
    final BitSet unionMembers = (BitSet) this.members.clone();
    unionMembers.or(set.members);
    return new AsciiSet(unionMembers);
  }

  /**
   * Returns a new set that will match characters iff they are included this set and in the
   * set that is provided.
   */
  public AsciiSet intersection(AsciiSet set) {
    final BitSet intersectionMembers = (BitSet) this.members.clone();
    intersectionMembers.and(set.members);
    return new AsciiSet(intersectionMembers);
  }

  /**
   * Returns a new set that will match characters iff they are included this set and not in the
   * set that is provided.
   */
  public AsciiSet diff(AsciiSet set) {
    final BitSet diffMembers = (BitSet) members.clone();
    diffMembers.andNot(set.members);
    return new AsciiSet(diffMembers);
  }

  /**
   * Returns a new set that will match characters that are not included this set.
   */
  public AsciiSet invert() {
    final BitSet invertMembers = (BitSet) members.clone();
    invertMembers.flip(0, MAX_BITS);
    return new AsciiSet(invertMembers);
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

  /**
   * If this set matches a single character, then return an optional with that character.
   * Otherwise return an empty optional.
   */
  public Optional<Character> character() {
    if (isEmpty()) {
      return Optional.empty();
    }
    int idx = members.nextSetBit(0);
    if (members.nextSetBit(idx + 1) != -1) {
      return Optional.empty();
    }
    return Optional.of((char) idx);
  }

  /** Returns true if this set is isEmpty. */
  public boolean isEmpty() {
    return members.isEmpty();
  }

  @Override public String toString() {
    return pattern;
  }

  @Override public int hashCode() {
    return pattern.hashCode() + 31 * members.hashCode();
  }

  @Override public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof AsciiSet)) return false;
    AsciiSet other = (AsciiSet) obj;
    return pattern.equals(other.pattern) && members.equals(other.members);
  }
}
