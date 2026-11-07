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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * Parses a pattern into a {@link Matcher} object.
 */
final class Parser {
  private static final AsciiSet META = AsciiSet.fromPattern("?*+");

  private final String tokens;
  private int current;

  /**
   * Number of reluctant quantifiers that have been parsed. It is used to check if the portion
   * a possessive quantifier applies to has a reluctant quantifier, see {@link #possessive}.
   */
  private int reluctantCount;

  /** First feature that is not supported, see {@link #unsupported(String)}. */
  private IllegalArgumentException firstUnsupported;

  /** Names of the capturing groups, like java a name can only be used once. */
  private final Set<String> groupNames = new HashSet<>();

  /** Pattern as written, used for error messages. */
  private final String source;

  /**
   * Position relative to {@link #offset} in the source for each char of the tokens, followed
   * by the position for the end of the tokens. Null if the tokens are the source starting at
   * the offset.
   */
  private final int[] positions;

  /** Offset of the tokens in the source, e.g. if a leading (?i) was removed. */
  private final int offset;

  private Parser(String tokens, String source, int[] positions, int offset) {
    this.tokens = tokens;
    this.source = source;
    this.positions = positions;
    this.offset = offset;
    this.current = 0;
  }

  /**
   * Parse the pattern starting at {@code offset}, e.g. after a leading (?i) flag. Quotations
   * are removed before parsing, errors are reported relative to the pattern as written.
   */
  static Matcher parse(String pattern, int offset) {
    String p = pattern.substring(offset);
    if (!p.contains("\\Q")) {
      return new Parser(p, pattern, null, offset).parse();
    }
    PatternUtils.IntArrayBuilder builder = new PatternUtils.IntArrayBuilder();
    String tokens = PatternUtils.removeQuotations(p, builder);
    return new Parser(tokens, pattern, builder.toArray(), offset).parse();
  }

  /** Position in the source for the current position in the tokens. */
  private int sourcePosition() {
    int pos = Math.min(current, tokens.length());
    return offset + (positions == null ? pos : positions[pos]);
  }

  private IllegalArgumentException error(String message) {
    return PatternUtils.error(message, source, sourcePosition());
  }

  private IllegalArgumentException unsupportedError(String message) {
    return PatternUtils.unsupported(message, source, sourcePosition());
  }

  /** Consume the current char and return the matcher. */
  private Matcher skip(Matcher m) {
    advance();
    return m;
  }

  /**
   * Record a feature that is not supported and return a placeholder so parsing can
   * continue. Like java, a pattern that is malformed is an error even if it also uses a
   * feature that is not supported, so the first unsupported feature is only thrown once the
   * whole pattern has been parsed, see {@link #parse()}.
   */
  private Matcher unsupported(String message) {
    if (firstUnsupported == null) {
      firstUnsupported = unsupportedError(message);
    }
    return TrueMatcher.INSTANCE;
  }

  /** Return the matcher for the top level expression. */
  private Matcher parse() {
    Matcher m = expr();
    if (!isAtEnd()) {
      throw error("unmatched closing parenthesis");
    }
    if (firstUnsupported != null) {
      throw firstUnsupported;
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
    // Number of reluctant quantifiers parsed before the last element of the sequence, so it
    // is known if the element a quantifier applies to has a reluctant quantifier.
    int reluctantBefore = reluctantCount;
    while (!isAtEnd() && peek() != ')' && peek() != '|') {
      char c = advance();
      if (c != '{' && c != '?' && c != '*' && c != '+') {
        reluctantBefore = reluctantCount;
      }
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
          matchers.add(repeat(pop(matchers), reluctantCount > reluctantBefore));
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
          matchers.add(meta(pop(matchers), reluctantCount > reluctantBefore));
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
   * applies to the whole code point like java, e.g. for an emoji. A lone surrogate is a
   * class, see {@link #codePoint(int)}.
   */
  private Matcher literal() {
    int cp = tokens.codePointAt(current - 1);
    current += Character.charCount(cp) - 1;
    return codePoint(cp);
  }

  /**
   * Create a matcher for a single code point. Like java, a lone surrogate only matches a
   * surrogate that is not part of a pair in the input, e.g. {@code \x{d83d}} does not match
   * the first char of an emoji. A sequence matches each char, so it would match half of a
   * pair. A class checks the whole code point at the position, so it is used instead.
   */
  private static Matcher codePoint(int cp) {
    return PatternUtils.isSurrogate(cp)
        ? new CharClassMatcher(AsciiSet.none(), AsciiSet.none(), false, CodePointRanges.of(cp))
        : new CharSeqMatcher(new String(Character.toChars(cp)));
  }

  private Matcher escape() {
    if (unsupportedEscape() != SUPPORTED_ESCAPE) {
      return TrueMatcher.INSTANCE;
    }
    char c = peek();
    switch (c) {
      case 'b':
        Matcher boundary = skip(unsupported("word boundary"));
        // Like java 9 and later, \b{g} is a grapheme cluster boundary. Other braces are a
        // repetition of the boundary, e.g. \b{2}.
        if (tokens.startsWith("{g", current)) {
          if (!tokens.startsWith("{g}", current)) {
            throw error("illegal/unsupported escape sequence");
          }
          current += 3;
        }
        return boundary;
      case 'B':
        return skip(unsupported("word boundary"));
      case 'A':
      case 'G':
      case 'Z':
      case 'z':
        return skip(unsupported("boundary matcher"));
      case 'R':
      case 'X':
        return skip(unsupported("escape sequence \\" + c));
      case 'k':
        advance();
        return namedBackReference();
      default:
        if (c != '0' && Constants.DIGIT.contains(c)) {
          Matcher m = unsupported("back references");
          while (!isAtEnd() && Constants.DIGIT.contains(peek())) {
            advance();
          }
          return m;
        }
        break;
    }
    CharClassMatcher m = namedCharClass();
    return m == null ? codePoint(escapedChar()) : m;
  }

  /** Returned by {@link #unsupportedEscape()} if the escape is not one it checks. */
  private static final int SUPPORTED_ESCAPE = -1;

  /** Returned by {@link #unsupportedEscape()} if the code point is not known. */
  private static final int UNKNOWN_CODE_POINT = -2;

  /**
   * Check the character after a backslash for escapes that are valid in any context, but
   * are not supported. The backslash has already been consumed. If it is not supported, then
   * it is consumed and the code point is returned, or {@link #UNKNOWN_CODE_POINT} if it is
   * not known, e.g. for {@code \N{name}}. Otherwise {@link #SUPPORTED_ESCAPE} is returned.
   * The code point is used to check a range in a class the same as java, e.g.
   * {@code [\cA-\x00]} is an error. Escapes that are only valid outside of a character
   * class, e.g. boundary matchers, are checked by {@link #escape()}. Like java, they are
   * illegal in a class and are rejected by {@link #escapedChar()}.
   */
  private int unsupportedEscape() {
    if (isAtEnd()) {
      throw error("dangling escape");
    }
    char c = peek();
    // Like java, it is an error if they are malformed
    if (c == 'c') {
      if (current + 1 >= tokens.length()) {
        throw error("illegal control escape sequence");
      }
      unsupported("control character");
      // Like java, it is the code point after the 'c' with bit 6 flipped, e.g. \cA is 0x01
      int cp = tokens.codePointAt(current + 1) ^ 64;
      current = tokens.offsetByCodePoints(current + 1, 1);
      return cp;
    } else if (c == 'N') {
      if (current + 1 >= tokens.length() || tokens.charAt(current + 1) != '{') {
        throw error("illegal character name escape sequence");
      }
      int end = tokens.indexOf('}', current);
      if (end < 0) {
        throw error("unclosed character name escape sequence");
      }
      unsupported("escape sequence \\N");
      current = end + 1;
      return UNKNOWN_CODE_POINT;
    }
    return SUPPORTED_ESCAPE;
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
        case '<':
          if (peek() == '=' || peek() == '!') {
            Matcher m = unsupported("lookbehind");
            advance();
            groupExpr();
            return m;
          }
          groupName();
          return groupExpr();
        case ':': // Non-capturing group
          return groupExpr();
        case '=':
          return new PositiveLookaheadMatcher(groupExpr());
        case '!':
          return new NegativeLookaheadMatcher(groupExpr());
        default:
          return inlineFlags(c);
      }
    } else {
      return groupExpr();
    }
  }

  /**
   * Parse the name of a capturing group up to and including the closing '>'. Like java, the
   * name must start with an ASCII letter followed by ASCII letters or digits, and each name
   * can only be used once.
   */
  private void groupName() {
    String name = readGroupName();
    // Checked before consuming the '>' so the error points to it, like java
    if (!groupNames.add(name)) {
      throw error("named capturing group <" + name + "> is already defined");
    }
    advance();
  }

  /** Read a group name up to the closing '>', which is not consumed. */
  private String readGroupName() {
    int start = current;
    if (isAtEnd() || !Constants.ALPHA.contains(peek())) {
      throw error("capturing group name does not start with a Latin letter");
    }
    while (!isAtEnd() && Constants.ALNUM.contains(peek())) {
      advance();
    }
    if (isAtEnd() || peek() != '>') {
      throw error("named capturing group is missing trailing '>'");
    }
    return tokens.substring(start, current);
  }

  /**
   * Check a back reference to a named group, {@code \k<name>}, after the 'k'. Like java, it
   * is an error if it is malformed or the group is not defined before it, otherwise it is
   * not supported.
   */
  private Matcher namedBackReference() {
    if (peek() != '<') {
      throw error("\\k is not followed by '<' for named capturing group");
    }
    advance();
    String name = readGroupName();
    if (!groupNames.contains(name)) {
      throw error("named capturing group <" + name + "> does not exist");
    }
    Matcher m = unsupported("back references");
    advance();
    return m;
  }

  /** Flags that java supports for an inline flag group, e.g. {@code (?i)} or {@code (?i:a)}. */
  private static final String INLINE_FLAGS = "idmsuxUc";

  /**
   * Flags that change how java parses the rest of the pattern. For {@code x}, whitespace and
   * comments are ignored, e.g. {@code (?x)a#(} is valid. For {@code U}, the POSIX property
   * names ignore the case, e.g. {@code (?U)\p{alpha}} is valid.
   */
  private static final String SYNTAX_FLAGS = "xU";

  /**
   * Check an inline flag group after the '(?' and the first char. Like java, it is an error
   * if it is not a valid flag group, otherwise it is not supported. Only a leading
   * {@code (?i)} for the whole pattern is supported, it is removed before parsing.
   */
  private Matcher inlineFlags(char first) {
    if (first == '>') {
      Matcher m = unsupported("atomic groups");
      groupExpr();
      return m;
    }
    char c = first;
    boolean negated = false;
    // Bit for each of the syntax flags that is set. Like java, a flag that is set and then
    // cleared, e.g. (?x-x), has no effect.
    int syntaxFlags = 0;
    while (INLINE_FLAGS.indexOf(c) >= 0 || (c == '-' && !negated)) {
      negated |= c == '-';
      int i = SYNTAX_FLAGS.indexOf(c);
      if (i >= 0) {
        syntaxFlags = negated ? syntaxFlags & ~(1 << i) : syntaxFlags | (1 << i);
      }
      if (isAtEnd()) {
        break;
      }
      c = advance();
    }
    boolean changesSyntax = syntaxFlags != 0;
    if (c == ')') {
      // Like java, a flag group without a body cannot be repeated with *, + or ?
      if (!isAtEnd() && (peek() == '*' || peek() == '+' || peek() == '?')) {
        throw error("dangling meta character '" + peek() + "'");
      }
      return unsupportedFlags(changesSyntax);
    } else if (c == ':') {
      Matcher m = unsupportedFlags(changesSyntax);
      groupExpr();
      return m;
    }
    throw error("unknown inline modifier");
  }

  /**
   * Record that the inline flags are not supported. If the flags change how java parses the
   * rest of the pattern, then it cannot be checked for errors the same as java, so the first
   * unsupported feature is thrown without parsing the rest.
   */
  private Matcher unsupportedFlags(boolean changesSyntax) {
    Matcher m = unsupported("inline flags");
    if (changesSyntax) {
      throw firstUnsupported;
    }
    return m;
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
        unsupported("horizontal whitespace class");
        return new CharClassMatcher(AsciiSet.none());
      case 'v':
      case 'V':
        unsupported("vertical whitespace class");
        return new CharClassMatcher(AsciiSet.none());
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
    private CodePointRanges nonAscii = CodePointRanges.NONE;
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

    /** Add a code point. */
    void addCodePoint(int cp) {
      if (cp < CodePointRanges.MIN) {
        add((char) cp);
      } else {
        addNonAscii(CodePointRanges.of(cp));
      }
    }

    /** Add a range of code points from s to e, inclusive. */
    void addRange(int s, int e) {
      if (s < CodePointRanges.MIN) {
        int asciiEnd = Math.min(e, CodePointRanges.MIN - 1);
        add(AsciiSet.fromPattern(new String(new char[] {(char) s, '-', (char) asciiEnd})));
      }
      if (e >= CodePointRanges.MIN) {
        addNonAscii(CodePointRanges.range(s, e));
      }
    }

    /** Add non-ASCII code points. */
    private void addNonAscii(CodePointRanges ranges) {
      nonAscii = nonAscii.union(ranges);
      empty = false;
    }

    /** Add a named or nested class to the class. */
    void add(CharClassMatcher m) {
      add(m.set(), m.foldedSet());
      nonAscii = nonAscii.union(m.nonAscii());
    }

    /** Intersect with a class. If nothing has been added yet, e.g. [&&a], then use it as is. */
    void intersection(CharClassMatcher m) {
      if (empty) {
        add(m);
      } else {
        set = set.intersection(m.set());
        folded = folded.intersection(m.foldedSet());
        nonAscii = nonAscii.intersection(m.nonAscii());
      }
    }

    /**
     * Create the matcher. Since Java 9, a negation applies to the whole class, including
     * nested classes and intersections, e.g. {@code [^a[b]]} matches neither 'a' nor 'b'.
     */
    CharClassMatcher build(boolean invert) {
      return invert
          ? new CharClassMatcher(set.invert(), folded.invert(), false, nonAscii.invert())
          : new CharClassMatcher(set, folded, false, nonAscii);
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

  /** Add a range of code points. Like java, it is an error if the end is before the start. */
  private void addRange(CharClassBuilder builder, int s, int e) {
    if (e < s) {
      throw error("illegal character range");
    }
    builder.addRange(s, e);
  }

  /**
   * Parse the rest of a character class up to and including the closing bracket. A leading
   * negation, if any, has already been consumed by the caller and is passed as
   * {@code invert}. A '^' at the current position is treated as a literal.
   */
  @SuppressWarnings("PMD")
  private CharClassMatcher charClass(boolean invert) {
    CharClassMatcher tmp;
    // Result of unsupportedEscape() for an escape
    int escaped;
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
          escaped = unsupportedEscape();
          if (escaped != SUPPORTED_ESCAPE) {
            // Not supported, but it is still an element of the class so that the class is
            // checked the same as java, e.g. [\cA&&] is valid and [\cA-\x00] is an error
            if (escaped == UNKNOWN_CODE_POINT) {
              builder.add(AsciiSet.none());
              rangeStart = -1;
            } else {
              builder.addCodePoint(escaped);
              rangeStart = escaped;
            }
            break;
          }
          tmp = namedCharClass();
          if (tmp == null) {
            rangeStart = escapedChar();
            builder.addCodePoint(rangeStart);
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
              escaped = unsupportedEscape();
              if (escaped == UNKNOWN_CODE_POINT) {
                // Not supported and the end is not known, so the range cannot be checked
                rangeStart = -1;
                break;
              } else if (escaped == SUPPORTED_ESCAPE && namedCharClass() != null) {
                throw error("illegal character range");
              }
              end = escaped == SUPPORTED_ESCAPE ? escapedChar() : escaped;
            } else if (isAtEnd()) {
              end = advance();
            } else {
              // Keep a surrogate pair together so the end is the code point
              end = tokens.codePointAt(current);
              current = tokens.offsetByCodePoints(current, 1);
            }
            addRange(builder, rangeStart, end);
            rangeStart = -1;
          } else {
            builder.add('-');
            rangeStart = c;
          }
          break;
        default:
          // Keep a surrogate pair together so it is a single code point
          rangeStart = tokens.codePointAt(current - 1);
          current = tokens.offsetByCodePoints(current - 1, 1);
          builder.addCodePoint(rangeStart);
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
      // Unicode and java.lang.Character classes, e.g. \\p{L} or \\p{javaLowerCase}, are valid
      // for java, but not supported
      if (!CharProperties.isValid(name)) {
        throw error("unknown character property name: " + name);
      }
      unsupported("character property \\p{" + name + "}");
      return new CharClassMatcher(AsciiSet.none());
    }
    return invert ? negated(set) : new CharClassMatcher(set);
  }

  /**
   * Create a class that matches characters not in the set. If the case is ignored, then
   * the case is folded before negation, e.g. {@code (?i)\\P{Lower}} doesn't match any letter.
   */
  private static CharClassMatcher negated(AsciiSet set) {
    AsciiSet folded = CharClassMatcher.withOtherCase(set).invert();
    return new CharClassMatcher(set.invert(), folded, false, CodePointRanges.ALL);
  }

  private Matcher repeat(Matcher matcher, boolean hasReluctant) {
    int[] bounds = repeatBounds();
    int min = bounds[0];
    boolean unbounded = bounds[1] < 0;
    int max = unbounded ? Integer.MAX_VALUE : bounds[1];

    boolean possessive = repeatModifier();
    ignoreFollowingRepeats();

    if (possessive) {
      return possessive(matcher, min, max, hasReluctant);
    } else if (unbounded) {
      // No upper bound, treat as min repetitions followed by zero or more. This is
      // consistent with how * and + are handled.
      Matcher zeroOrMore = new ZeroOrMoreMatcher(matcher, term());
      if (min == 0) {
        return zeroOrMore;
      }
      Matcher prefix = (min == 1) ? matcher : new RepeatMatcher(matcher, min, min);
      return SeqMatcher.create(prefix, zeroOrMore);
    }

    // The rest of the term is merged in by the optimizer so that it is checked after each
    // repetition
    return new RepeatMatcher(matcher, min, max);
  }

  /**
   * Create a matcher for a possessive repetition, e.g. a*+ or a{2,3}+. Like java, each
   * repetition keeps its first match and the rest of the pattern is only checked after all
   * of the repetitions. That is only supported if the first match of the repeated portion
   * will be the same as java, see {@link #firstMatchSameAsJava(Matcher)}. Reluctant
   * quantifiers are matched the same as greedy, so they are not supported in the repeated
   * portion, e.g. (a??)?+ matches the empty string with java.
   */
  private Matcher possessive(Matcher matcher, int min, int max, boolean hasReluctant) {
    if (hasReluctant) {
      unsupported("reluctant quantifier within a possessive repetition");
      return new RepeatMatcher(matcher, min, max);
    } else if (!firstMatchSameAsJava(matcher)) {
      unsupported("possessive repetition of a portion that can match different lengths");
      return new RepeatMatcher(matcher, min, max);
    }
    // A trailing .* would consume the rest of the string, but ZeroOrMoreMatcher returns the
    // start position if nothing follows, so use a possessive repetition, e.g. for (a.*)++.
    // The same for a class the optimizer may convert to '.', e.g. (a[\s\S]*)++.
    Matcher repeated = matcher.rewriteEnd(Parser::matchAnyToPossessive);
    return new RepeatMatcher(repeated, min, max, TrueMatcher.INSTANCE, true);
  }

  private static Matcher matchAnyToPossessive(Matcher matcher) {
    if (matcher instanceof ZeroOrMoreMatcher) {
      ZeroOrMoreMatcher m = matcher.as();
      if (canMatchAny(m.repeated()) && m.next() instanceof TrueMatcher) {
        return new RepeatMatcher(
            m.repeated(), 0, Integer.MAX_VALUE, TrueMatcher.INSTANCE, true);
      }
    }
    return matcher;
  }

  /**
   * Returns true if the matcher is {@code .} or a class that the optimizer could convert
   * to {@code .}, e.g. {@code [\s\S]}, or {@code [[^a]A]} once the case is ignored.
   */
  private static boolean canMatchAny(Matcher matcher) {
    if (matcher instanceof CharClassMatcher) {
      CharClassMatcher m = matcher.as();
      return m.nonAscii().isAll() && m.foldedSet().invert().isEmpty();
    }
    return matcher instanceof AnyMatcher;
  }

  /**
   * Returns true if the position returned by the matcher, with nothing after it, is the end
   * of the first match java would find. That is the case if it always matches the same
   * length, e.g. (ab|cd), or if it is a greedy repetition of a portion that always matches
   * the same length, e.g. a+ or a{1,3}. Otherwise, the first match can depend on the order
   * of alternatives or on backtracking, e.g. for (a|ab) java would use 'a'.
   */
  private static boolean firstMatchSameAsJava(Matcher matcher) {
    if (PatternUtils.isFixedLength(matcher)) {
      return true;
    } else if (matcher instanceof ZeroOrMoreMatcher) {
      ZeroOrMoreMatcher m = matcher.as();
      return m.next() instanceof TrueMatcher && PatternUtils.isFixedLength(m.repeated());
    } else if (matcher instanceof ZeroOrOneMatcher) {
      ZeroOrOneMatcher m = matcher.as();
      return m.next() instanceof TrueMatcher && PatternUtils.isFixedLength(m.repeated());
    } else if (matcher instanceof RepeatMatcher) {
      // A possessive repetition consumes the first match of each repetition. A greedy one
      // does as well, but java would backtrack into the repeated portion if needed to get
      // the min, so it must always match the same length.
      RepeatMatcher m = matcher.as();
      return m.next() instanceof TrueMatcher
          && (m.isPossessive()
              ? firstMatchSameAsJava(m.repeated())
              : PatternUtils.isFixedLength(m.repeated()));
    } else if (matcher instanceof SeqMatcher) {
      List<Matcher> ms = matcher.<SeqMatcher>as().matchers();
      for (int i = 0; i < ms.size() - 1; ++i) {
        if (!PatternUtils.isFixedLength(ms.get(i))) {
          return false;
        }
      }
      return firstMatchSameAsJava(ms.get(ms.size() - 1));
    }
    return false;
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
      // The modifier is ignored along with the repeat, so a reluctant one is not counted
      int saved = reluctantCount;
      repeatModifier();
      reluctantCount = saved;
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
      if (advance() == '+') {
        return true;
      }
      ++reluctantCount;
    }
    return false;
  }

  private Matcher meta(Matcher matcher, boolean hasReluctant) {
    int start = current - 1;
    // A quantifier can be followed by a single possessive (+) or reluctant (?) modifier.
    // Anything after that is an error, e.g. a+++ or a**.
    if (!isAtEnd() && (peek() == '+' || peek() == '?')) {
      advance();
    }
    String quantifier = tokens.substring(start, current);
    if (quantifier.endsWith("?") && quantifier.length() == 2) {
      ++reluctantCount;
    }
    // Must be checked before the rest of the term is parsed, e.g. a*{3} is the same as a*
    ignoreFollowingRepeats();
    switch (quantifier) {
      case "?":
      case "??":
        return new ZeroOrOneMatcher(matcher, term());
      case "?+":
        return possessive(matcher, 0, 1, hasReluctant);
      case "*":
      case "*?":
        return new ZeroOrMoreMatcher(matcher, term());
      case "*+":
        return possessive(matcher, 0, Integer.MAX_VALUE, hasReluctant);
      case "+":
      case "+?":
        return SeqMatcher.create(matcher, new ZeroOrMoreMatcher(matcher, term()));
      case "++":
        return possessive(matcher, 1, Integer.MAX_VALUE, hasReluctant);
      default:
        throw new IllegalStateException("unknown quantifier: " + quantifier);
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
