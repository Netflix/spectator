/*
 * Copyright 2014-2022 Netflix, Inc.
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

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * Parses a pattern into a {@link Matcher} object.
 */
class Parser {
  private static final AsciiSet META = AsciiSet.fromPattern("?*+");

  private final String tokens;
  private int current;

  /** Create a new instance. */
  Parser(String tokens) {
    this.tokens = tokens;
    this.current = 0;
  }

  private IllegalArgumentException error(String message) {
    return PatternUtils.error(message, tokens, current);
  }

  private IllegalArgumentException unsupported(String message) {
    return PatternUtils.unsupported(message, tokens, current);
  }

  /** Return the matcher for the top level expression. */
  Matcher parse() {
    Matcher m = expr();
    if (!isAtEnd()) {
      throw error("unmatched closing parenthesis");
    }
    return m;
  }

  private Matcher expr() {
    List<Matcher> matchers = new ArrayList<>();
    matchers.add(term());
    while (!isAtEnd() && peek() != ')' && peek() == '|') {
      advance();
      matchers.add(term());
    }
    if (!isAtEnd() && peek() == ')') {
      advance();
    }
    return OrMatcher.create(matchers);
  }

  private Matcher term() {
    List<Matcher> matchers = new ArrayList<>();
    while (!isAtEnd() && peek() != ')' && peek() != '|') {
      char c = advance();
      switch (c) {
        case '\\':
          if (peek() == 'Q') {
            quotation(matchers);
          } else {
            matchers.add(escape());
          }
          break;
        case '^':
          matchers.add(StartMatcher.INSTANCE);
          break;
        case '$':
          matchers.add(EndMatcher.INSTANCE);
          break;
        case '[':
          matchers.add(charClass());
          break;
        case '(':
          matchers.add(group());
          break;
        case '{':
          // Repetitions following a quantifier are handled by repeat() and meta(), so if
          // empty, then the repetition is at the start of a term, e.g. {3} or a|{3}.
          if (matchers.isEmpty()) {
            throw error("dangling modifier");
          }
          matchers.add(repeat(pop(matchers)));
          break;
        case '.':
          matchers.add(AnyMatcher.INSTANCE);
          break;
        case '?':
        case '*':
        case '+':
          if (matchers.isEmpty()) {
            throw error("dangling modifier");
          }
          matchers.add(meta(pop(matchers)));
          break;
        default:
          matchers.add(new CharSeqMatcher(c));
          break;
      }
    }
    return SeqMatcher.create(matchers);
  }

  private Matcher pop(List<Matcher> matchers) {
    return matchers.remove(matchers.size() - 1);
  }

  private Matcher escape() {
    char c = peek();
    if (c == 'c') {
      throw unsupported("control character");
    } else if (Constants.DIGIT.contains(c) || c == 'k') {
      throw unsupported("back references");
    } else {
      CharClassMatcher m = namedCharClass();
      if (m == null) {
        advance();
        return new CharSeqMatcher(String.valueOf(c));
      } else {
        return m;
      }
    }
  }

  /**
   * Add the literal text of a quotation, {@code \\Q...\\E}. Like java, a quantifier
   * following the quotation only applies to the last code point, e.g. {@code \\Qab\\E{2}}
   * is {@code ab{2}}. An empty quotation adds nothing. Empty quotations are normally
   * removed by {@link PatternUtils#expandEscapedChars(String)} so that the elements on
   * either side are joined, e.g. {@code a*\\Q\\E?} is {@code a*?}.
   */
  private void quotation(List<Matcher> matchers) {
    int start = current + 1;
    int end = tokens.indexOf("\\E", start);
    if (end == -1) {
      throw error("unclosed quotation");
    }
    current = end + 2;
    if (start < end) {
      // Character before start is 'Q', so the offset cannot be before the start
      int last = tokens.offsetByCodePoints(end, -1);
      if (start < last) {
        matchers.add(new CharSeqMatcher(tokens.substring(start, last)));
      }
      matchers.add(new CharSeqMatcher(tokens.substring(last, end)));
    }
  }

  private Matcher groupExpr() {
    Matcher m = expr();
    if (previous() != ')') {
      throw error("unclosed group");
    }
    return m;
  }

  private Matcher group() {
    if (peek() == '?') {
      advance();
      char c = advance();
      switch (c) {
        case '<': // Named capturing group
          advance(v -> v != '>');
          if (isAtEnd()) {
            throw error("unclosed name for capturing group");
          }
          return groupExpr();
        case ':': // Non-capturing group
          return groupExpr();
        case '=':
          return new PositiveLookaheadMatcher(expr());
        case '!':
          return new NegativeLookaheadMatcher(expr());
        default:
          throw unsupported("inline flags");
      }
    } else {
      return groupExpr();
    }
  }

  @SuppressWarnings("PMD.ImplicitSwitchFallThrough")
  private CharClassMatcher namedCharClass() {
    boolean invert = false;
    char c = advance();
    switch (c) {
      case 'd': return new CharClassMatcher(Constants.DIGIT);
      case 'D': return negated(Constants.DIGIT);
      case 's': return new CharClassMatcher(Constants.SPACE);
      case 'S': return negated(Constants.SPACE);
      case 'w': return new CharClassMatcher(Constants.WORD_CHARS);
      case 'W': return negated(Constants.WORD_CHARS);
      case 'h':
      case 'H':
        throw unsupported("horizontal whitespace class");
      case 'v':
      case 'V':
        throw unsupported("vertical whitespace class");
      case 'P':
        invert = true;
      case 'p':
        return newNamedCharSet(name(), invert);
      default:
        --current;
        return null;
    }
  }

  private String name() {
    char c = advance();
    if (c == '{') {
      StringBuilder builder = new StringBuilder();
      while (peek() != '}') {
        builder.append(advance());
      }
      advance();
      return builder.toString();
    } else {
      return String.valueOf(c);
    }
  }

  /**
   * Builds the sets for a character class. Along with the set of characters, it tracks the
   * set to use if the case is ignored. Java folds the case of each element before applying
   * negation or intersection, so {@code (?i)[^a]} matches neither 'a' nor 'A'.
   */
  private static final class CharClassBuilder {
    private AsciiSet set = AsciiSet.none();
    private AsciiSet folded = AsciiSet.none();
    private boolean empty = true;

    private void add(AsciiSet s, AsciiSet f) {
      set = set.union(s);
      folded = folded.union(f);
      empty = false;
    }

    void add(AsciiSet s) {
      add(s, CharClassMatcher.withOtherCase(s));
    }

    void add(char c) {
      add(AsciiSet.fromPattern(Character.toString(c)));
    }

    /** Add a range of characters from s to e, inclusive. */
    void add(char s, char e) {
      add(AsciiSet.fromPattern(new String(new char[] {s, '-', e})));
    }

    /** Add a named or nested class to the class. */
    void add(CharClassMatcher m) {
      add(m.set(), m.foldedSet());
    }

    /** Intersect with a class. If nothing has been added yet, e.g. [&&a], then use it as is. */
    void intersection(CharClassMatcher m) {
      if (empty) {
        add(m);
      } else {
        set = set.intersection(m.set());
        folded = folded.intersection(m.foldedSet());
      }
    }

    /**
     * Create the matcher. Since Java 9, a negation applies to the whole class, including
     * nested classes and intersections, e.g. {@code [^a[b]]} matches neither 'a' nor 'b'.
     */
    CharClassMatcher build(boolean invert) {
      return invert
          ? new CharClassMatcher(set.invert(), folded.invert(), false)
          : new CharClassMatcher(set, folded, false);
    }
  }

  @SuppressWarnings("PMD")
  private CharClassMatcher charClass() {
    CharClassMatcher tmp;
    // Literal character that can be used as the start of a range, -1 if the previous
    // element cannot start a range.
    int rangeStart = -1;
    boolean invert = peek() == '^';
    if (invert) {
      advance();
    }
    CharClassBuilder builder = new CharClassBuilder();
    if (peek() == ']' || peek() == '-') {
      char c = advance();
      rangeStart = c;
      builder.add(c);
    }
    while (!isAtEnd() && peek() != ']') {
      char c = advance();
      switch (c) {
        case '[':
          builder.add(charClass());
          rangeStart = -1;
          break;
        case '&':
          if (peek() == '&') {
            advance();
            if (peek() == '[') {
              advance();
              builder.intersection(charClass());
            } else if (peek() != ']') {
              builder.intersection(charClass());
              --current;
            }
            rangeStart = -1;
          } else {
            builder.add(c);
            rangeStart = c;
          }
          break;
        case '\\':
          tmp = namedCharClass();
          if (tmp == null) {
            c = advance();
            rangeStart = c;
            builder.add(c);
          } else {
            rangeStart = -1;
            builder.add(tmp);
          }
          break;
        case '-':
          // The end of the range is consumed so it cannot start another range, e.g. [a-c-e]
          // is a-c, '-', and 'e'. If followed by a nested class, then '-' is a literal.
          if (rangeStart >= 0 && peek() != ']' && peek() != '[') {
            if (peek() == '\\') {
              advance();
              tmp = namedCharClass();
              if (tmp == null) {
                builder.add((char) rangeStart, advance());
              } else {
                builder.add('-');
                builder.add(tmp);
              }
            } else {
              builder.add((char) rangeStart, advance());
            }
            rangeStart = -1;
          } else {
            builder.add('-');
            rangeStart = c;
          }
          break;
        default:
          rangeStart = c;
          builder.add(c);
          break;
      }
    }
    if (advance() != ']') {
      throw error("unclosed character class");
    }
    return builder.build(invert);
  }

  private CharClassMatcher newNamedCharSet(String name, boolean invert) {
    AsciiSet set = Constants.NAMED_CHAR_CLASSES.get(name);
    if (set == null) {
      throw error("unknown character property name: " + name);
    }
    return invert ? negated(set) : new CharClassMatcher(set);
  }

  /**
   * Create a class that matches characters not in the set. If the case is ignored, then
   * the case is folded before negation, e.g. {@code (?i)\\P{Lower}} doesn't match any letter.
   */
  private static CharClassMatcher negated(AsciiSet set) {
    return new CharClassMatcher(set.invert(), CharClassMatcher.withOtherCase(set).invert(), false);
  }

  private Matcher repeat(Matcher matcher) {
    int[] bounds = repeatBounds();
    int min = bounds[0];
    boolean unbounded = bounds[1] < 0;
    int max = unbounded ? Integer.MAX_VALUE : bounds[1];

    boolean possessive = repeatModifier();
    ignoreFollowingRepeats();

    if (unbounded && !possessive) {
      // No upper bound, treat as min repetitions followed by zero or more. This is
      // consistent with how * and + are handled.
      Matcher zeroOrMore = new ZeroOrMoreMatcher(matcher, term());
      if (min == 0) {
        return zeroOrMore;
      }
      Matcher prefix = (min == 1) ? matcher : new RepeatMatcher(matcher, min, min);
      return SeqMatcher.create(prefix, zeroOrMore);
    }

    // RepeatMatcher is greedy without backtracking, so it also covers the possessive case
    return new RepeatMatcher(matcher, min, max);
  }

  /**
   * Additional repetitions that follow a quantifier are ignored, e.g. a{2}{3} is the same as
   * a{2}. This is consistent with java.util.regex. They are still validated. After that, only
   * a single modifier is allowed, e.g. a{2}?? or a{2}* are invalid.
   */
  private void ignoreFollowingRepeats() {
    while (!isAtEnd() && peek() == '{') {
      advance();
      repeatBounds();
      repeatModifier();
    }
    if (!isAtEnd() && META.contains(peek())) {
      throw error("dangling modifier");
    }
  }

  /**
   * Parse the bounds for a repetition after the opening brace. Returns an array with the
   * min and max. The max will be -1 if there is no upper bound.
   */
  private int[] repeatBounds() {
    int start = current;
    advance(c -> c != '}');
    if (previous() != '}') {
      throw error("unclosed repetition");
    }
    // Use a negative limit so the trailing empty string for "{n,}" is preserved
    String[] numbers = tokens.subSequence(start, current - 1).toString().split(",", -1);
    if (numbers.length > 2) {
      throw error("illegal repetition");
    }
    int min = parseRepeatCount(numbers[0]);
    if (numbers.length == 1) {
      return new int[] {min, min};
    } else if (numbers[1].isEmpty()) {
      return new int[] {min, -1};
    }
    int max = parseRepeatCount(numbers[1]);
    if (min > max) {
      throw error("illegal repetition, min > max");
    }
    return new int[] {min, max};
  }

  @SuppressWarnings("PMD.PreserveStackTrace")
  private int parseRepeatCount(String num) {
    // Validate explicitly as Integer.parseInt allows a leading sign and non-ascii digits
    for (int i = 0; i < num.length(); ++i) {
      if (!Constants.DIGIT.contains(num.charAt(i))) {
        throw error("illegal repetition");
      }
    }
    try {
      return Integer.parseInt(num);
    } catch (NumberFormatException e) {
      throw error("illegal repetition");
    }
  }

  /**
   * Consume the optional possessive (+) or reluctant (?) modifier for a repetition and
   * return true if it is possessive. Reluctant matching behavior is the same as greedy,
   * consistent with *? and +?. Modifiers are consumed here rather than in meta() because
   * meta() cannot tell a modifier, a{2}?, apart from a quantifier on a group that ends
   * with a repeat, (a{2})?.
   */
  private boolean repeatModifier() {
    if (!isAtEnd() && (peek() == '+' || peek() == '?')) {
      return advance() == '+';
    }
    return false;
  }

  private Matcher meta(Matcher matcher) {
    int start = current - 1;
    advance(c -> META.contains((char) c));
    --current;
    String quantifier = tokens.subSequence(start, current).toString();
    // Must be checked before the rest of the term is parsed, e.g. a*{3} is the same as a*
    ignoreFollowingRepeats();
    switch (quantifier) {
      case "?":
      case "??":
      case "?+":
        // Possessive is treated as greedy. A RepeatMatcher would avoid backtracking, but
        // it is not possible to tell it apart from a greedy repeat when converting to a
        // string or optimizing, and a ZeroOrOneMatcher is more consistent with the other
        // greedy matchers that do not backtrack.
        return new ZeroOrOneMatcher(matcher, term());
      case "*":
      case "*?":
        return new ZeroOrMoreMatcher(matcher, term());
      case "*+":
        return new RepeatMatcher(matcher, 0, Integer.MAX_VALUE);
      case "+":
      case "+?":
        return SeqMatcher.create(matcher, new ZeroOrMoreMatcher(matcher, term()));
      case "++":
        return SeqMatcher.create(matcher, new RepeatMatcher(matcher, 0, Integer.MAX_VALUE));
      default:
        throw new IllegalArgumentException("unknown quantifier: " + quantifier);
    }
  }

  private boolean isAtEnd() {
    return current >= tokens.length();
  }

  private char peek() {
    return tokens.charAt(current);
  }

  private char previous() {
    return tokens.charAt(current - 1);
  }

  private char advance() {
    if (!isAtEnd()) current++;
    return previous();
  }

  @SuppressWarnings("PMD.AvoidBranchingStatementAsLastInLoop")
  private void advance(IntPredicate condition) {
    while (!isAtEnd() && condition.test(advance())) {
      continue; // loop is just used to advance the position
    }
  }
}
