/*
 * Copyright 2014-2018 Netflix, Inc.
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

  private UnsupportedOperationException unsupported(String message) {
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
          matchers.add(escape());
          break;
        case '^':
          matchers.add(StartMatcher.INSTANCE);
          break;
        case '$':
          matchers.add(EndMatcher.INSTANCE);
          break;
        case '[':
          matchers.add(new CharClassMatcher(charClass()));
          break;
        case '(':
          matchers.add(group());
          break;
        case '{':
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
    if (c == 'Q') {
      return quotation();
    } else if (c == 'c') {
      throw unsupported("control character");
    } else if (Constants.DIGIT.contains(c) || c == 'k') {
      throw unsupported("back references");
    } else {
      AsciiSet set = namedCharClass();
      if (set == null) {
        advance();
        return new CharSeqMatcher(String.valueOf(c));
      } else {
        return new CharClassMatcher(set);
      }
    }
  }

  private Matcher quotation() {
    int start = current + 1;
    int end = tokens.indexOf("\\E", start);
    if (end == -1) {
      throw error("unclosed quotation");
    }
    current = end + 2;
    return new CharSeqMatcher(tokens.substring(start, end));
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

  @SuppressWarnings("PMD.MissingBreakInSwitch")
  private AsciiSet namedCharClass() {
    boolean invert = false;
    char c = advance();
    switch (c) {
      case 'd': return Constants.DIGIT;
      case 'D': return Constants.DIGIT.invert();
      case 's': return Constants.SPACE;
      case 'S': return Constants.SPACE.invert();
      case 'w': return Constants.WORD_CHARS;
      case 'W': return Constants.WORD_CHARS.invert();
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

  private AsciiSet update(AsciiSet s1, AsciiSet s2, boolean invert) {
    return invert ? s1.diff(s2) : s1.union(s2);
  }

  @SuppressWarnings("PMD")
  private AsciiSet charClass() {
    AsciiSet tmp;
    AsciiSet set = AsciiSet.none();
    boolean rangeStart = false;
    boolean invert = peek() == '^';
    if (invert) {
      advance();
      set = AsciiSet.all();
    }
    if (peek() == ']' || peek() == '-') {
      char c = advance();
      set = update(set, AsciiSet.fromPattern(Character.toString(c)), invert);
    }
    while (!isAtEnd() && peek() != ']') {
      char c = advance();
      switch (c) {
        case '[':
          set = set.union(charClass());
          rangeStart = false;
          break;
        case '&':
          if (peek() == '&') {
            advance();
            if (peek() == '[') {
              advance();
              set = set.intersection(charClass());
            } else if (peek() != ']') {
              set = set.intersection(charClass());
              --current;
            }
          } else {
            set = update(set, AsciiSet.fromPattern(Character.toString(c)), invert);
          }
          rangeStart = false;
          break;
        case '\\':
          tmp = namedCharClass();
          if (tmp == null) {
            c = advance();
            rangeStart = true;
            set = update(set, AsciiSet.fromPattern(Character.toString(c)), invert);
          } else {
            rangeStart = false;
            set = update(set, tmp, invert);
          }
          break;
        case '-':
          if (rangeStart && peek() != ']') {
            if (peek() == '\\') {
              advance();
              tmp = namedCharClass();
              if (tmp == null) {
                String range = tokens.subSequence(current - 3, current - 1).toString() + peek();
                if (range.endsWith("\\")) {
                  advance();
                }
                set = update(set, AsciiSet.fromPattern(range), invert);
              } else {
                set = update(set, AsciiSet.fromPattern("-"), invert);
                set = update(set, tmp, invert);
              }
            } else {
              String range = tokens.subSequence(current - 2, current + 1).toString();
              set = update(set, AsciiSet.fromPattern(range), invert);
            }
          } else {
            set = update(set, AsciiSet.fromPattern("-"), invert);
          }
          rangeStart = false;
          break;
        default:
          rangeStart = true;
          set = update(set, AsciiSet.fromPattern(Character.toString(c)), invert);
          break;
      }
    }
    if (advance() != ']') {
      throw error("unclosed character class");
    }
    return set;
  }

  private AsciiSet newNamedCharSet(String name, boolean invert) {
    AsciiSet set = Constants.NAMED_CHAR_CLASSES.get(name);
    if (set == null) {
      throw error("unknown character property name: " + name);
    }
    return invert ? set.invert() : set;
  }

  private Matcher repeat(Matcher matcher) {
    int start = current;
    advance(c -> c != '}');
    String[] numbers = tokens.subSequence(start, current - 1).toString().split(",");
    int min = Integer.parseInt(numbers[0]);
    int max = (numbers.length > 1) ? Integer.parseInt(numbers[1]) : min;
    return new RepeatMatcher(matcher, min, max);
  }

  private Matcher meta(Matcher matcher) {
    int start = current - 1;
    advance(c -> META.contains((char) c));
    --current;
    String quantifier = tokens.subSequence(start, current).toString();
    switch (quantifier) {
      case "?":
        // Makes repeat reluctant
        if (matcher instanceof RepeatMatcher) {
          return matcher;
        }
      case "??":
      case "?+":
        return OrMatcher.create(matcher, TrueMatcher.INSTANCE);
      case "*":
      case "*?":
        return new ZeroOrMoreMatcher(matcher, term());
      case "*+":
        return new RepeatMatcher(matcher, 0, Integer.MAX_VALUE);
      case "+":
      case "+?":
        return SeqMatcher.create(matcher, new ZeroOrMoreMatcher(matcher, term()));
      case "++":
        return SeqMatcher.create(matcher, new RepeatMatcher(matcher, 1, Integer.MAX_VALUE));
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
