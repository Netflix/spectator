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

import java.io.Serializable;
import java.util.Arrays;

/**
 * Immutable set of non-ASCII code points for a character class, stored as sorted ranges
 * that do not overlap or touch. The ASCII chars are stored separately in an
 * {@link com.netflix.spectator.impl.AsciiSet} so that the common case is fast.
 */
final class CodePointRanges implements Serializable {

  private static final long serialVersionUID = 1L;

  /** First non-ASCII code point. */
  static final int MIN = 0x80;

  /** Last code point. */
  static final int MAX = Character.MAX_CODE_POINT;

  /** Empty set. */
  static final CodePointRanges NONE = new CodePointRanges(new int[0]);

  /** Set with all non-ASCII code points. */
  static final CodePointRanges ALL = new CodePointRanges(new int[] {MIN, MAX});

  /**
   * Returns the set for the range from start to end, inclusive. The portion of the range
   * that is ASCII is ignored.
   */
  static CodePointRanges range(int start, int end) {
    int s = Math.max(start, MIN);
    if (end < s) {
      return NONE;
    }
    return (s == MIN && end == MAX) ? ALL : new CodePointRanges(new int[] {s, end});
  }

  /** Returns the set with a single code point, or empty if it is ASCII. */
  static CodePointRanges of(int cp) {
    return range(cp, cp);
  }

  /** Pairs with the start and end, inclusive, of each range. */
  private final int[] ranges;

  private CodePointRanges(int[] ranges) {
    this.ranges = ranges;
  }

  /** Use the shared instances for the common cases. */
  private Object readResolve() {
    return create(ranges);
  }

  private static CodePointRanges create(int[] ranges) {
    if (ranges.length == 0) {
      return NONE;
    } else if (ranges.length == 2 && ranges[0] == MIN && ranges[1] == MAX) {
      return ALL;
    }
    return new CodePointRanges(ranges);
  }

  /** Returns true if the set is empty. */
  boolean isEmpty() {
    return ranges.length == 0;
  }

  /** Returns true if the set has all non-ASCII code points. */
  boolean isAll() {
    return ranges.length == 2 && ranges[0] == MIN && ranges[1] == MAX;
  }

  /** Returns the code point if the set has exactly one, otherwise -1. */
  int single() {
    return ranges.length == 2 && ranges[0] == ranges[1] ? ranges[0] : -1;
  }

  /** Returns true if the code point is in the set. */
  boolean contains(int cp) {
    // Binary search for the last range with a start <= cp. Most sets have one or two
    // ranges, so the loop is short.
    int lo = 0;
    int hi = ranges.length / 2 - 1;
    while (lo <= hi) {
      int mid = (lo + hi) >>> 1;
      int i = mid * 2;
      if (cp < ranges[i]) {
        hi = mid - 1;
      } else if (cp > ranges[i + 1]) {
        lo = mid + 1;
      } else {
        return true;
      }
    }
    return false;
  }

  /** Returns a set with the code points in either set. */
  CodePointRanges union(CodePointRanges other) {
    if (other.isEmpty() || isAll()) {
      return this;
    } else if (isEmpty() || other.isAll()) {
      return other;
    }
    // Merge the ranges sorted by start, then join any that overlap or touch
    int[] merged = new int[ranges.length + other.ranges.length];
    int i = 0;
    int j = 0;
    int n = 0;
    while (i < ranges.length || j < other.ranges.length) {
      int s;
      int e;
      if (j >= other.ranges.length || (i < ranges.length && ranges[i] <= other.ranges[j])) {
        s = ranges[i];
        e = ranges[i + 1];
        i += 2;
      } else {
        s = other.ranges[j];
        e = other.ranges[j + 1];
        j += 2;
      }
      if (n > 0 && s <= merged[n - 1] + 1) {
        merged[n - 1] = Math.max(merged[n - 1], e);
      } else {
        merged[n++] = s;
        merged[n++] = e;
      }
    }
    return create(Arrays.copyOf(merged, n));
  }

  /** Returns a set with the non-ASCII code points that are not in this set. */
  CodePointRanges invert() {
    if (isEmpty()) {
      return ALL;
    } else if (isAll()) {
      return NONE;
    }
    int[] inverted = new int[ranges.length + 2];
    int n = 0;
    int next = MIN;
    for (int i = 0; i < ranges.length; i += 2) {
      if (ranges[i] > next) {
        inverted[n++] = next;
        inverted[n++] = ranges[i] - 1;
      }
      next = ranges[i + 1] + 1;
    }
    if (next <= MAX) {
      inverted[n++] = next;
      inverted[n++] = MAX;
    }
    return create(Arrays.copyOf(inverted, n));
  }

  /** Returns a set with the code points in both sets. */
  CodePointRanges intersection(CodePointRanges other) {
    if (isEmpty() || other.isAll()) {
      return this;
    } else if (other.isEmpty() || isAll()) {
      return other;
    }
    return invert().union(other.invert()).invert();
  }

  /**
   * Returns true if every code point in the other set is also in this set. The ranges of
   * this set do not touch, so each range of the other set must be within a single one.
   */
  boolean containsAll(CodePointRanges other) {
    int i = 0;
    for (int j = 0; j < other.ranges.length; j += 2) {
      final int s = other.ranges[j];
      while (i < ranges.length && ranges[i + 1] < s) {
        i += 2;
      }
      if (i >= ranges.length || ranges[i] > s || ranges[i + 1] < other.ranges[j + 1]) {
        return false;
      }
    }
    return true;
  }

  /** Returns true if the sets have any code points in common. */
  boolean overlaps(CodePointRanges other) {
    // Walk both sorted lists, advancing the range that ends first
    int i = 0;
    int j = 0;
    while (i < ranges.length && j < other.ranges.length) {
      if (ranges[i + 1] < other.ranges[j]) {
        i += 2;
      } else if (other.ranges[j + 1] < ranges[i]) {
        j += 2;
      } else {
        return true;
      }
    }
    return false;
  }

  /** Number of code points in the set. */
  int count() {
    int n = 0;
    for (int i = 0; i < ranges.length; i += 2) {
      n += ranges[i + 1] - ranges[i] + 1;
    }
    return n;
  }

  /**
   * Returns the ranges as the body of a regex character class, e.g.
   * {@code \x{e9}\x{1f600}-\x{1f64f}}.
   */
  String toPattern() {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < ranges.length; i += 2) {
      appendCodePoint(builder, ranges[i]);
      if (ranges[i + 1] != ranges[i]) {
        builder.append('-');
        appendCodePoint(builder, ranges[i + 1]);
      }
    }
    return builder.toString();
  }

  /** Append the code point as a hex escape, e.g. {@code \x{1f600}}. */
  static void appendCodePoint(StringBuilder builder, int cp) {
    builder.append("\\x{").append(Integer.toHexString(cp)).append('}');
  }

  @Override
  public String toString() {
    return toPattern();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    return Arrays.equals(ranges, ((CodePointRanges) o).ranges);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(ranges);
  }
}
