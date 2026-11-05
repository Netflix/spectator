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

  private static final long serialVersionUID = 2L;

  /** Golden ratio. A small odd multiplier like 31 leaves a raw bit mask's structure intact. */
  private static final long HASH_MIXER = 0x9E3779B97F4A7C15L;

  private static void checkAscii(char c) {
    if (c >= 128) {
      throw new IllegalArgumentException("invalid pattern, '" + c + "' is not ascii");
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
    long b0 = 0L;
    long b1 = 0L;
    final int n = pattern.length();
    for (int i = 0; i < n; ++i) {
      final char c = pattern.charAt(i);

      final boolean isStartOrEnd = i == 0 || i == n - 1;
      if (isStartOrEnd || c != '-') {
        checkAscii(c);
        if (c < 64) {
          b0 |= 1L << c;
        } else {
          b1 |= 1L << c;
        }
      } else {
        final char s = pattern.charAt(i - 1);
        final char e = pattern.charAt(i + 1);
        for (char v = s; v <= e; ++v) {
          checkAscii(v);
          if (v < 64) {
            b0 |= 1L << v;
          } else {
            b1 |= 1L << v;
          }
        }
      }
    }
    return new AsciiSet(b0, b1);
  }

  /** Returns a set that matches no characters. */
  public static AsciiSet none() {
    return new AsciiSet(0L, 0L);
  }

  /** Returns a set that matches all ascii characters. */
  public static AsciiSet all() {
    // -1L has every bit set, so both words match every character.
    return new AsciiSet(-1L, -1L);
  }

  /** Returns a set that matches ascii control characters. */
  public static AsciiSet control() {
    long b0 = 0L;
    long b1 = 0L;
    for (char c = 0; c < 128; ++c) {
      if (Character.isISOControl(c)) {
        if (c < 64) {
          b0 |= 1L << c;
        } else {
          b1 |= 1L << c;
        }
      }
    }
    return new AsciiSet(b0, b1);
  }

  private static boolean memberBit(long b0, long b1, char c) {
    final long word = (c < 64) ? b0 : b1;
    // Relies on JLS 15.19: a long shift masks its distance to the low 6 bits, so `c` correctly
    // selects the bit within whichever word was chosen even though c can be up to 127. Masking
    // explicitly (`c & 0x3F`) is slower, since x86's shrx already masks the count in hardware.
    return c < 128 && ((word >>> c) & 1L) != 0L;
  }

  private String toPattern() {
    StringBuilder buf = new StringBuilder();
    if (contains('-')) {
      buf.append('-');
    }
    // The dash is a literal at the start. It is only also kept in a range if it is in the
    // middle, such as ",-.". A range that starts or ends with it, such as "---/" or ",-a",
    // would be parsed as a different range.
    final boolean dashInRange = contains(',') && contains('.');
    boolean previous = false;
    char s = 0;
    for (int i = 0; i < 128; ++i) {
      final boolean member = contains((char) i) && (i != '-' || dashInRange);
      if (member && !previous) {
        s = (char) i;
      } else if (!member && previous) {
        final char e = (char) (i - 1);
        append(buf, s, e);
      }
      previous = member;
    }
    if (previous) {
      append(buf, s, (char) 127);
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

  /** Membership bits for characters 0-63. */
  private final long bits0;

  /** Membership bits for characters 64-127. */
  private final long bits1;

  private AsciiSet(long bits0, long bits1) {
    this.bits0 = bits0;
    this.bits1 = bits1;
  }

  /**
   * Returns true if the character contained within the set. This is a constant time
   * operation.
   */
  public boolean contains(char c) {
    return memberBit(bits0, bits1, c);
  }

  /**
   * Returns true if all characters in the string are contained within the set.
   */
  public boolean containsAll(CharSequence str) {
    final int n = str.length();
    for (int i = 0; i < n; ++i) {
      if (!memberBit(bits0, bits1, str.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  private int indexOfNonMember(CharSequence str) {
    final int n = str.length();
    for (int i = 0; i < n; ++i) {
      if (!memberBit(bits0, bits1, str.charAt(i))) {
        return i;
      }
    }
    return n;
  }

  /**
   * Replace all characters in the input string with the replacement character.
   */
  public String replaceNonMembers(String input, char replacement) {
    if (!contains(replacement)) {
      throw new IllegalArgumentException(replacement + " is not a member of " + toString());
    }
    int i = indexOfNonMember(input);
    return i < input.length() ? replaceNonMembersImpl(input, i, replacement) : input;
  }

  /**
   * Returns a new set that will match characters either in the this set or in the
   * set that is provided.
   */
  public AsciiSet union(AsciiSet set) {
    return new AsciiSet(bits0 | set.bits0, bits1 | set.bits1);
  }

  /**
   * Returns a new set that will match characters iff they are included this set and in the
   * set that is provided.
   */
  public AsciiSet intersection(AsciiSet set) {
    return new AsciiSet(bits0 & set.bits0, bits1 & set.bits1);
  }

  /**
   * Returns a new set that will match characters iff they are included this set and not in the
   * set that is provided.
   */
  public AsciiSet diff(AsciiSet set) {
    return new AsciiSet(bits0 & ~set.bits0, bits1 & ~set.bits1);
  }

  /**
   * Returns a new set that will match characters that are not included this set.
   */
  public AsciiSet invert() {
    // Every bit maps to a real ascii character, so there are no padding bits to mask off.
    return new AsciiSet(~bits0, ~bits1);
  }

  private String replaceNonMembersImpl(String input, int start, char replacement) {
    final char[] buf = input.toCharArray();
    buf[start] = replacement;
    for (int i = start + 1; i < buf.length; ++i) {
      if (!memberBit(bits0, bits1, input.charAt(i))) {
        buf[i] = replacement;
      }
    }
    return new String(buf);
  }

  /**
   * If this set matches a single character, then return an optional with that character.
   * Otherwise return an empty optional.
   */
  public Optional<Character> character() {
    if (bits1 == 0L && Long.bitCount(bits0) == 1) {
      return Optional.of((char) Long.numberOfTrailingZeros(bits0));
    }
    if (bits0 == 0L && Long.bitCount(bits1) == 1) {
      return Optional.of((char) (64 + Long.numberOfTrailingZeros(bits1)));
    }
    return Optional.empty();
  }

  /** Returns true if this set is isEmpty. */
  public boolean isEmpty() {
    return (bits0 | bits1) == 0L;
  }

  @Override public String toString() {
    return toPattern();
  }

  @Override public int hashCode() {
    // Folding the words straight through Long.hashCode would hash every set like its
    // complement, since ~hi ^ ~lo == hi ^ lo. The xor-shift between them is load bearing too:
    // 2^63 is a fixed point of odd multiplication, so char 63 would hash like char 127.
    long h = bits0 * HASH_MIXER;
    h ^= h >>> 32;
    h = (h + bits1) * HASH_MIXER;
    return (int) (h ^ (h >>> 32));
  }

  @Override public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof AsciiSet)) return false;
    AsciiSet other = (AsciiSet) obj;
    return bits0 == other.bits0 && bits1 == other.bits1;
  }
}
