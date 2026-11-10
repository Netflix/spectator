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

import com.netflix.spectator.impl.AsciiSet;

import java.util.List;

/**
 * Set of code points used to analyze a pattern. Like {@link CharClassMatcher}, it has a
 * set for the ASCII chars and the ranges for the non-ASCII code points.
 */
final class CharSet {

  /** Set with all code points. */
  static final CharSet ANY = new CharSet(AsciiSet.all(), CodePointRanges.ALL);

  /** Empty set. */
  static final CharSet NONE = new CharSet(AsciiSet.none(), CodePointRanges.NONE);

  /**
   * Set for each ASCII char, see {@link #of(int, boolean)}. The first 128 are the char
   * itself and the next 128 also have the other case of a letter.
   */
  private static final CharSet[] ASCII_CHARS = asciiChars();

  private static CharSet[] asciiChars() {
    CharSet[] sets = new CharSet[256];
    for (char c = 0; c < 128; ++c) {
      AsciiSet set = AsciiSet.fromPattern(String.valueOf(c));
      sets[c] = new CharSet(set, CodePointRanges.NONE);
      sets[c + 128] = new CharSet(CharClassMatcher.withOtherCase(set), CodePointRanges.NONE);
    }
    return sets;
  }

  /**
   * Returns the set for a matcher that always matches a single char, or null if the matcher
   * could match something else.
   */
  static CharSet of(Matcher matcher) {
    if (matcher instanceof AnyMatcher) {
      return ANY;
    } else if (matcher instanceof CharClassMatcher) {
      CharClassMatcher m = matcher.as();
      return new CharSet(m.isIgnoreCase() ? m.foldedSet() : m.set(), m.nonAscii());
    } else if (matcher instanceof CharSeqMatcher) {
      CharSeqMatcher m = matcher.as();
      String pattern = m.pattern();
      return !pattern.isEmpty() && pattern.offsetByCodePoints(0, 1) == pattern.length()
          ? of(pattern.codePointAt(0), m.isIgnoreCase())
          : null;
    } else if (matcher instanceof OrMatcher) {
      // Alternatives of single chars, e.g. (?:a|b)
      CharSet set = NONE;
      for (Matcher m : matcher.<OrMatcher>as().matchers()) {
        CharSet cs = of(m);
        if (cs == null) {
          return null;
        }
        set = set.union(cs);
      }
      return set;
    }
    return null;
  }

  /**
   * Returns a set with the code point. If the case is ignored, then it also has the other
   * case of an ASCII letter. Only ASCII letters are folded.
   */
  static CharSet of(int cp, boolean ignoreCase) {
    if (cp >= 128) {
      return new CharSet(AsciiSet.none(), CodePointRanges.of(cp));
    }
    return ASCII_CHARS[ignoreCase ? cp + 128 : cp];
  }

  /**
   * Returns the set of chars that could be consumed by the matcher. It returns the set of
   * all chars if it is not known.
   */
  static CharSet consumed(Matcher matcher) {
    CharSet single = of(matcher);
    if (single != null) {
      return single;
    } else if (matcher instanceof CharSeqMatcher) {
      CharSeqMatcher m = matcher.as();
      return chars(m.pattern(), m.isIgnoreCase());
    } else if (matcher instanceof TrueMatcher
        || matcher instanceof StartMatcher
        || matcher instanceof EndMatcher
        || matcher instanceof PositiveLookaheadMatcher
        || matcher instanceof NegativeLookaheadMatcher) {
      return NONE;
    } else if (matcher instanceof SeqMatcher) {
      return consumed(matcher.<SeqMatcher>as().matchers());
    } else if (matcher instanceof OrMatcher) {
      return consumed(matcher.<OrMatcher>as().matchers());
    } else if (matcher instanceof ZeroOrMoreMatcher) {
      ZeroOrMoreMatcher m = matcher.as();
      return consumed(m.repeated()).union(consumed(m.next()));
    } else if (matcher instanceof MinOrMoreMatcher) {
      MinOrMoreMatcher m = matcher.as();
      return consumed(m.repeated()).union(consumed(m.next()));
    } else if (matcher instanceof ZeroOrOneMatcher) {
      ZeroOrOneMatcher m = matcher.as();
      return consumed(m.repeated()).union(consumed(m.next()));
    } else if (matcher instanceof RepeatMatcher) {
      RepeatMatcher m = matcher.as();
      return consumed(m.repeated()).union(consumed(m.next()));
    }
    return ANY;
  }

  private static CharSet consumed(List<Matcher> matchers) {
    CharSet set = NONE;
    for (Matcher m : matchers) {
      set = set.union(consumed(m));
    }
    return set;
  }

  /** Returns the set of chars in the string. */
  private static CharSet chars(String str, boolean ignoreCase) {
    CharSet set = NONE;
    for (int i = 0; i < str.length(); i = str.offsetByCodePoints(i, 1)) {
      set = set.union(of(str.codePointAt(i), ignoreCase));
    }
    return set;
  }

  private final AsciiSet ascii;
  private final CodePointRanges nonAscii;

  private CharSet(AsciiSet ascii, CodePointRanges nonAscii) {
    this.ascii = ascii;
    this.nonAscii = nonAscii;
  }

  /** Returns true if this set contains all of the chars in the other set. */
  boolean containsAll(CharSet other) {
    return other.ascii.diff(ascii).isEmpty() && nonAscii.containsAll(other.nonAscii);
  }

  /** Returns true if there is a char in both sets. */
  boolean overlaps(CharSet other) {
    return !ascii.intersection(other.ascii).isEmpty() || nonAscii.overlaps(other.nonAscii);
  }

  /** Returns a set with the chars from both sets. */
  CharSet union(CharSet other) {
    return new CharSet(ascii.union(other.ascii), nonAscii.union(other.nonAscii));
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof CharSet)) {
      return false;
    }
    CharSet other = (CharSet) obj;
    return nonAscii.equals(other.nonAscii) && ascii.equals(other.ascii);
  }

  @Override
  public int hashCode() {
    return 31 * ascii.hashCode() + nonAscii.hashCode();
  }
}
