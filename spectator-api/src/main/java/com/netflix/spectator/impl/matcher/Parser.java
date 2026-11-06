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

  /** Parse alternatives up to the end of the pattern or a closing parenthesis. */
  private Matcher expr() {
    List<Matcher> matchers = new ArrayList<>();
    matchers.add(term());
    while (!isAtEnd() && peek() == '|') {
      advance();
      matchers.add(term());
    }
    return OrMatcher.create(matchers);
  }

  private Matcher term() {
    List<Matcher> matchers = new ArrayList<>();
    while (!isAtEnd() && peek() != ')' && peek() != '|') {
      char c = advance();
      switch (c) {
        case '\\':
          matchers.add(escape());
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
          matchers.add(literal());
          break;
      }
    }
    return SeqMatcher.create(matchers);
  }

  private Matcher pop(List<Matcher> matchers) {
    return matchers.remove(matchers.size() - 1);
  }

  /**
   * Create a matcher for the literal character that was just consumed. If it is the high
   * surrogate of a pair, then the low surrogate is consumed as well so that a quantifier
   * applies to the whole code point like java, e.g. for an emoji.
   */
  private Matcher literal() {
    int start = current - 1;
    current = tokens.offsetByCodePoints(start, 1);
    return new CharSeqMatcher(tokens.substring(start, current));
  }

  private Matcher escape() {
    checkEscape();
    char c = peek();
    switch (c) {
      case 'b':
      case 'B':
        throw unsupported("word boundary");
      case 'A':
      case 'G':
      case 'Z':
      case 'z':
        throw unsupported("boundary matcher");
      case 'R':
      case 'X':
        throw unsupported("escape sequence \\" + c);
      case 'k':
        throw unsupported("back references");
      default:
        if (c != '0' && Constants.DIGIT.contains(c)) {
          throw unsupported("back references");
        }
        break;
    }
    CharClassMatcher m = namedCharClass();
    return m == null ? new CharSeqMatcher(new String(Character.toChars(escapedChar()))) : m;
  }

  /**
   * Check the character after a backslash for escapes that are valid in any context, but
   * are not supported. The backslash has already been consumed. Escapes that are only valid
   * outside of a character class, e.g. boundary matchers, are checked by {@link #escape()}.
   * Like java, they are illegal in a class and are rejected by {@link #escapedChar()}.
   */
  private void checkEscape() {
    if (isAtEnd()) {
      throw error("dangling escape");
    }
    char c = peek();
    if (c == 'c') {
      throw unsupported("control character");
    } else if (c == 'N') {
      throw unsupported("escape sequence \\N");
    }
  }

  /**
   * Decode an escaped literal character after the backslash and return the code point.
   * Like java, ASCII letters and digits that are not a known escape are an error, any other
   * character is a literal, e.g. \\+.
   */
  private int escapedChar() {
    char c = advance();
    switch (c) {
      case 't': return '\t';
      case 'n': return '\n';
      case 'r': return '\r';
      case 'f': return '\f';
      case 'a': return '\u0007';
      case 'e': return '\u001B';
      case '0': return octalEscape();
      case 'x': return hexEscape();
      case 'u': return unicodeEscape();
      default:
        if (Constants.ALNUM.contains(c)) {
          --current;
          throw error("illegal/unsupported escape sequence");
        }
        // Keep a surrogate pair together, e.g. for an escaped emoji
        int cp = tokens.codePointAt(current - 1);
        current = tokens.offsetByCodePoints(current - 1, 1);
        return cp;
    }
  }

  /** Octal escape, \\0n, \\0nn, or \\0mnn where m is 0-3. */
  private int octalEscape() {
    int start = current;
    int end = start;
    while (end < tokens.length() && end < start + 3 && isOctal(tokens.charAt(end))) {
      ++end;
    }
    if (end == start) {
      throw error("invalid octal escape sequence");
    }
    if (end - start == 3 && tokens.charAt(start) > '3') {
      // Max is \\0377, otherwise the last digit is a literal following the escape
      --end;
    }
    current = end;
    return Integer.parseInt(tokens.substring(start, end), 8);
  }

  private static boolean isOctal(char c) {
    return c >= '0' && c <= '7';
  }

  /** Hexadecimal escape, \\xhh or \\x{h...h}. */
  private int hexEscape() {
    if (!isAtEnd() && peek() == '{') {
      int start = current + 1;
      int end = tokens.indexOf('}', start);
      if (end <= start) {
        throw error("invalid hexadecimal escape sequence");
      }
      int cp = parseHex(start, end, "hexadecimal");
      if (cp > Character.MAX_CODE_POINT) {
        throw error("hexadecimal code point is too big");
      }
      current = end + 1;
      return cp;
    }
    return parseFixedHex(2, "hexadecimal");
  }

  /**
   * Unicode escape, \\uhhhh. Like java, a high surrogate followed by an escaped low
   * surrogate is a single code point.
   */
  private int unicodeEscape() {
    int c = parseFixedHex(4, "unicode");
    if (Character.isHighSurrogate((char) c) && tokens.startsWith("\\u", current)) {
      int saved = current;
      current += 2;
      int low = parseFixedHex(4, "unicode");
      if (Character.isLowSurrogate((char) low)) {
        return Character.toCodePoint((char) c, (char) low);
      }
      current = saved;
    }
    return c;
  }

  private int parseFixedHex(int n, String mode) {
    int start = current;
    int end = start + n;
    if (end > tokens.length()) {
      throw error("invalid " + mode + " escape sequence");
    }
    int v = parseHex(start, end, mode);
    current = end;
    return v;
  }

  @SuppressWarnings("PMD.PreserveStackTrace")
  private int parseHex(int start, int end, String mode) {
    // Validate explicitly as Integer.parseInt allows a leading sign and non-ascii digits
    for (int i = start; i < end; ++i) {
      if (!Constants.XDIGIT.contains(tokens.charAt(i))) {
        throw error("invalid " + mode + " escape sequence");
      }
    }
    try {
      return Integer.parseInt(tokens.substring(start, end), 16);
    } catch (NumberFormatException e) {
      throw error("hexadecimal code point is too big");
    }
  }

  /** Parse the expression for a group and the closing parenthesis. */
  private Matcher groupExpr() {
    Matcher m = expr();
    if (isAtEnd()) {
      throw error("unclosed group");
    }
    advance();
    return m;
  }

  private Matcher group() {
    if (peek() == '?') {
      advance();
      if (isAtEnd()) {
        throw error("unclosed group");
      }
      char c = advance();
      switch (c) {
        case '<': // Named capturing group
          advance(v -> v != '>');
          if (previous() != '>') {
            throw error("unclosed name for capturing group");
          }
          return groupExpr();
        case ':': // Non-capturing group
          return groupExpr();
        case '=':
          return new PositiveLookaheadMatcher(groupExpr());
        case '!':
          return new NegativeLookaheadMatcher(groupExpr());
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
    if (isAtEnd()) {
      throw error("missing character property name");
    }
    char c = advance();
    if (c == '{') {
      int start = current;
      int end = tokens.indexOf('}', start);
      if (end < 0) {
        throw error("unclosed character property name");
      }
      current = end + 1;
      return tokens.substring(start, end);
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

    /** Returns true if nothing has been added. */
    boolean isEmpty() {
      return empty;
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

  /** Parse a character class after the opening bracket. */
  private CharClassMatcher charClass() {
    boolean invert = peek() == '^';
    if (invert) {
      advance();
    }
    return charClass(invert);
  }

  /**
   * Check that a decoded character can be used in a character class. Classes only support
   * ASCII characters.
   */
  private int classChar(int cp) {
    if (cp >= 128) {
      throw error("invalid pattern, '" + new String(Character.toChars(cp)) + "' is not ascii");
    }
    return cp;
  }

  /** Add a range of characters. Like java, it is an error if the end is before the start. */
  private void addRange(CharClassBuilder builder, char s, char e) {
    if (e < s) {
      throw error("illegal character range");
    }
    builder.add(s, e);
  }

  /**
   * Parse the rest of a character class up to and including the closing bracket. A leading
   * negation, if any, has already been consumed by the caller and is passed as
   * {@code invert}. A '^' at the current position is treated as a literal.
   */
  @SuppressWarnings("PMD")
  private CharClassMatcher charClass(boolean invert) {
    CharClassMatcher tmp;
    // Literal character that can be used as the start of a range, -1 if the previous
    // element cannot start a range.
    int rangeStart = -1;
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
            // As with Java, the right operand is any nested classes followed by the rest of
            // the elements up to the closing bracket, e.g. [a-c&&[b]x] is a-c and {b, x}. A
            // '^' at the start of those elements is a literal. A '&' right after a nested
            // class ends the operand, e.g. [a-c&&[b]&] is the intersection of a-c and {b},
            // then '&'. The closing bracket is left for the outer class. Nested classes are
            // parsed here rather than by a recursive call so that a long chain such as
            // [a&&[b]&&[c]...] does not need a stack frame for each intersection.
            CharClassBuilder right = new CharClassBuilder();
            while (!isAtEnd() && peek() != ']' && peek() != '&') {
              if (peek() == '[') {
                advance();
                right.add(charClass());
              } else {
                right.add(charClass(false));
                --current;
              }
            }
            if (!right.isEmpty()) {
              builder.intersection(right.build(false));
            } else if (builder.isEmpty() && !isAtEnd()) {
              // Like java, an intersection needs at least one operand, e.g. [&&] is an error.
              // At the end of the pattern, e.g. [&&, it is reported as an unclosed class.
              throw error("bad class syntax");
            }
            rangeStart = -1;
          } else {
            builder.add(c);
            rangeStart = c;
          }
          break;
        case '\\':
          checkEscape();
          tmp = namedCharClass();
          if (tmp == null) {
            rangeStart = classChar(escapedChar());
            builder.add((char) rangeStart);
          } else {
            rangeStart = -1;
            builder.add(tmp);
          }
          break;
        case '-':
          // The end of the range is consumed so it cannot start another range, e.g. [a-c-e]
          // is a-c, '-', and 'e'. If followed by a nested class, then '-' is a literal.
          // Like java, a range that ends with a named class, e.g. [a-\\w], is an error.
          if (rangeStart >= 0 && peek() != ']' && peek() != '[') {
            int end;
            if (peek() == '\\') {
              advance();
              checkEscape();
              if (namedCharClass() != null) {
                throw error("illegal character range");
              }
              end = classChar(escapedChar());
            } else {
              end = advance();
            }
            addRange(builder, (char) rangeStart, (char) end);
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
    if (isAtEnd()) {
      throw error("unclosed character class");
    }
    advance();
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
    while (!isAtEnd() && META.contains(peek())) {
      advance();
    }
    String quantifier = tokens.substring(start, current);
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

  /** Returns the next character or 0 if at the end of the pattern. */
  private char peek() {
    return isAtEnd() ? '\0' : tokens.charAt(current);
  }

  private char previous() {
    return tokens.charAt(current - 1);
  }

  /**
   * Consume and return the next character or 0 if at the end of the pattern. Like
   * {@link #peek()}, returning 0 rather than the last character means a caller that does
   * not check for the end cannot mistake an earlier character for the next one.
   */
  private char advance() {
    return isAtEnd() ? '\0' : tokens.charAt(current++);
  }

  @SuppressWarnings("PMD.AvoidBranchingStatementAsLastInLoop")
  private void advance(IntPredicate condition) {
    while (!isAtEnd() && condition.test(advance())) {
      continue; // loop is just used to advance the position
    }
  }
}
