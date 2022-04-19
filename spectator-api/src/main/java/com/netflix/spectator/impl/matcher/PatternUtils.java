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

import com.netflix.spectator.impl.PatternMatcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Helper functions for working with patterns.
 *
 * <p><b>This class is an internal implementation detail only intended for use within spectator.
 * It is subject to change without notice.</b></p>
 */
public final class PatternUtils {

  private PatternUtils() {
  }

  /**
   * Compile a pattern string and return a matcher that can be used to check if string values
   * match the pattern. Pattern matchers are can be reused many times and are thread safe.
   */
  public static PatternMatcher compile(String pattern) {
    String p = pattern;
    boolean ignoreCase = false;
    if (p.startsWith("(?i)")) {
      ignoreCase = true;
      p = pattern.substring(4);
    }
    if (p.length() > 0) {
      p = "^.*(" + p + ").*$";
    }
    Parser parser = new Parser(PatternUtils.expandEscapedChars(p));
    Matcher m = Optimizer.optimize(parser.parse());
    return ignoreCase ? m.ignoreCase() : m;
  }

  private static String context(String str, int pos) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < pos; ++i) {
      builder.append(' ');
    }
    builder.append('^');
    return str + "\n" + builder.toString();
  }

  /**
   * Create an IllegalArgumentException with a message including context based
   * on the position.
   */
  static IllegalArgumentException error(String message, String str, int pos) {
    return new IllegalArgumentException(message + "\n" + context(str, pos));
  }

  /**
   * Create an UnsupportedOperationException with a message including context based
   * on the position.
   */
  static UnsupportedOperationException unsupported(String message, String str, int pos) {
    return new UnsupportedOperationException(message + "\n" + context(str, pos));
  }

  @SuppressWarnings("PMD.PreserveStackTrace")
  private static char parse(String num, int radix, String mode, String str, int pos) {
    try {
      return (char) Integer.parseInt(num, radix);
    } catch (NumberFormatException e) {
      throw error("invalid " + mode + " escape sequence", str, pos);
    }
  }

  /**
   * Expand escaped characters. Escapes that are needed for structural elements of the
   * pattern will not be expanded.
   */
  @SuppressWarnings({"PMD.NcssCount", "PMD.AvoidReassigningLoopVariables"})
  static String expandEscapedChars(String str) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < str.length(); ++i) {
      char c = str.charAt(i);
      if (c == '\\') {
        ++i;
        if (i >= str.length()) {
          throw error("dangling escape", str, i);
        }
        c = str.charAt(i);
        switch (c) {
          case 't': builder.append('\t'); break;
          case 'n': builder.append('\n'); break;
          case 'r': builder.append('\r'); break;
          case 'f': builder.append('\f'); break;
          case 'a': builder.append('\u0007'); break;
          case 'e': builder.append('\u001B'); break;
          case '0':
            int numDigits = 0;
            for (int j = i + 1; j < Math.min(i + 4, str.length()); ++j) {
              c = str.charAt(j);
              if (c >= '0' && c <= '7') {
                ++numDigits;
              } else {
                break;
              }
            }
            if (numDigits < 1 || numDigits > 3) {
              throw error("invalid octal escape sequence", str, i);
            }
            c = parse(str.substring(i + 1, i + numDigits + 1), 8, "octal", str, i);
            builder.append(c);
            i += numDigits;
            break;
          case 'x':
            if (i + 3 > str.length()) {
              throw error("invalid hexadecimal escape sequence", str, i);
            }
            c = parse(str.substring(i + 1, i + 3), 16, "hexadecimal", str, i);
            builder.append(c);
            i += 2;
            break;
          case 'u':
            if (i + 5 > str.length()) {
              throw error("invalid unicode escape sequence", str, i);
            }
            c = parse(str.substring(i + 1, i + 5), 16, "unicode", str, i);
            builder.append(c);
            i += 4;
            break;
          default:
            builder.append('\\').append(c);
            break;
        }
      } else {
        builder.append(c);
      }
    }
    return builder.toString();
  }

  /**
   * Escape a string so it will be interpreted as a literal character sequence when processed
   * as a regular expression.
   */
  @SuppressWarnings("PMD.NcssCount")
  static String escape(String str) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < str.length(); ++i) {
      char c = str.charAt(i);
      switch (c) {
        case '\t': builder.append("\\t"); break;
        case '\n': builder.append("\\n"); break;
        case '\r': builder.append("\\r"); break;
        case '\f': builder.append("\\f"); break;
        case '\\': builder.append("\\\\"); break;
        case '^':  builder.append("\\^"); break;
        case '$':  builder.append("\\$"); break;
        case '.':  builder.append("\\."); break;
        case '?':  builder.append("\\?"); break;
        case '*':  builder.append("\\*"); break;
        case '+':  builder.append("\\+"); break;
        case '[':  builder.append("\\["); break;
        case ']':  builder.append("\\]"); break;
        case '(':  builder.append("\\("); break;
        case ')':  builder.append("\\)"); break;
        case '{':  builder.append("\\{"); break;
        case '}':  builder.append("\\}"); break;
        default:
          if (c <= ' ' || c > '~') {
            builder.append(String.format("\\u%04x", (int) c));
          } else {
            builder.append(c);
          }
          break;
      }
    }
    return builder.toString();
  }

  /** Convert to a matchers that ignores the case. */
  static Matcher ignoreCase(Matcher matcher) {
    if (matcher instanceof CharClassMatcher) {
      CharClassMatcher m = matcher.as();
      return new CharClassMatcher(m.set(), true);
    } else if (matcher instanceof CharSeqMatcher) {
      CharSeqMatcher m = matcher.as();
      return new CharSeqMatcher(m.pattern(), true);
    } else if (matcher instanceof IndexOfMatcher) {
      IndexOfMatcher m = matcher.as();
      return new IndexOfMatcher(m.pattern(), m.next(), true);
    } else if (matcher instanceof StartsWithMatcher) {
      StartsWithMatcher m = matcher.as();
      return new StartsWithMatcher(m.pattern(), true);
    } else {
      return matcher;
    }
  }

  /**
   * Expand OR clauses in the provided matcher up to the max limit. If expanding would exceed
   * the max, then null will be returned.
   */
  static List<Matcher> expandOrClauses(Matcher matcher, int max) {
    if (matcher instanceof IndexOfMatcher) {
      IndexOfMatcher m = matcher.as();
      return map(expandOrClauses(m.next(), max), n -> new IndexOfMatcher(m.pattern(), n), max);
    } else if (matcher instanceof ZeroOrMoreMatcher) {
      ZeroOrMoreMatcher m = matcher.as();
      return map(expandOrClauses(m.next(), max), n -> new ZeroOrMoreMatcher(m.repeated(), n), max);
    } else if (matcher instanceof ZeroOrOneMatcher) {
      ZeroOrOneMatcher m = matcher.as();
      List<Matcher> rs = expandOrClauses(m.repeated(), max);
      List<Matcher> ns = expandOrClauses(m.next(), max);
      if (rs == null || ns == null || ns.size() * (rs.size() + 1) > max) {
        return null;
      } else if (rs.size() == 1 && ns.size() == 1) {
        return Collections.singletonList(matcher);
      } else {
        List<Matcher> results = new ArrayList<>(ns);
        for (Matcher r : rs) {
          for (Matcher n : ns) {
            results.add(new ZeroOrOneMatcher(r, n));
          }
        }
        return results;
      }
    } else if (matcher instanceof PositiveLookaheadMatcher) {
      PositiveLookaheadMatcher m = matcher.as();
      return map(expandOrClauses(m.matcher(), max), PositiveLookaheadMatcher::new, max);
    } else if (matcher instanceof SeqMatcher) {
      return expandSeq(matcher.as(), max);
    } else if (matcher instanceof OrMatcher) {
      return expandOr(matcher.as(), max);
    } else {
      return Collections.singletonList(matcher);
    }
  }

  private static List<Matcher> map(List<Matcher> ms, Function<Matcher, Matcher> f, int max) {
    if (ms == null || ms.size() > max) {
      return null;
    }
    List<Matcher> results = new ArrayList<>(ms.size());
    for (Matcher m : ms) {
      results.add(f.apply(m));
    }
    return results;
  }

  private static List<Matcher> expandSeq(SeqMatcher seqMatcher, int max) {
    List<List<Matcher>> results = new ArrayList<>();
    for (Matcher matcher : seqMatcher.matchers()) {
      if (results.isEmpty()) {
        List<Matcher> rs = expandOrClauses(matcher, max);
        if (rs == null)
          return null;
        for (Matcher m : rs) {
          List<Matcher> tmp = new ArrayList<>();
          tmp.add(m);
          results.add(tmp);
        }
      } else {
        List<Matcher> rs = expandOrClauses(matcher, max);
        if (rs == null || results.size() * rs.size() > max)
          return null;
        List<List<Matcher>> tmp = new ArrayList<>(results.size() * rs.size());
        for (List<Matcher> ms : results) {
          for (Matcher r : rs) {
            List<Matcher> seq = new ArrayList<>(ms);
            seq.add(r);
            tmp.add(seq);
          }
        }
        results = tmp;
      }
    }

    List<Matcher> tmp = new ArrayList<>(results.size());
    for (List<Matcher> ms : results) {
      tmp.add(SeqMatcher.create(ms));
    }
    return tmp;
  }

  private static List<Matcher> expandOr(OrMatcher matcher, int max) {
    List<Matcher> ms = matcher.matchers();
    if (ms.size() > max) {
      return null;
    }
    List<Matcher> results = new ArrayList<>();
    for (Matcher m : ms) {
      List<Matcher> tmp = expandOrClauses(m, max);
      if (tmp == null || results.size() + tmp.size() > max) {
        return null;
      }
      results.addAll(tmp);
    }
    return results;
  }

  /** Convert a matcher to a SQL pattern or return null if not possible. */
  static String toSqlPattern(Matcher matcher) {
    StringBuilder builder = new StringBuilder();
    if (toSqlPattern(builder, matcher)) {
      if (!endsWithWildcard(builder) && !matcher.isEndAnchored()) {
        builder.append('%');
      }
      return builder.toString();
    } else {
      return null;
    }
  }

  private static boolean endsWithWildcard(StringBuilder builder) {
    int n = builder.length();
    return n > 0 && builder.charAt(n - 1) == '%';
  }

  private static String sqlEscape(String str) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < str.length(); ++i) {
      char c = str.charAt(i);
      switch (c) {
        case '_':  builder.append("\\_");  break;
        case '%':  builder.append("\\%");  break;
        case '\\': builder.append("\\\\"); break;
        default:   builder.append(c);      break;
      }
    }
    return builder.toString();
  }

  private static boolean toSqlPattern(StringBuilder builder, Matcher matcher) {
    if (matcher instanceof TrueMatcher) {
      builder.append("%");
      return true;
    } else if (matcher instanceof AnyMatcher) {
      builder.append('_');
      return true;
    } else if (matcher instanceof StartMatcher || matcher instanceof EndMatcher) {
      return true;
    } else if (matcher instanceof ZeroOrMoreMatcher) {
      ZeroOrMoreMatcher m = matcher.as();
      if (m.repeated() == AnyMatcher.INSTANCE) {
        builder.append('%');
        return toSqlPattern(builder, m.next());
      }
    } else if (matcher instanceof SeqMatcher) {
      SeqMatcher sm = matcher.as();
      for (Matcher m : sm.matchers()) {
        if (!toSqlPattern(builder, m)) {
          return false;
        }
      }
      return true;
    } else if (matcher instanceof CharSeqMatcher) {
      CharSeqMatcher m = matcher.as();
      builder.append(sqlEscape(m.pattern()));
      return true;
    } else if (matcher instanceof IndexOfMatcher) {
      IndexOfMatcher m = matcher.as();
      builder.append('%').append(sqlEscape(m.pattern()));
      return toSqlPattern(builder, m.next());
    } else if (matcher instanceof StartsWithMatcher) {
      StartsWithMatcher m = matcher.as();
      builder.append(sqlEscape(m.pattern()));
      return true;
    }

    return false;
  }

  /** Returns the first matcher from a sequence. */
  static Matcher head(Matcher matcher) {
    if (matcher instanceof SeqMatcher) {
      List<Matcher> ms = matcher.<SeqMatcher>as().matchers();
      return ms.get(0);
    } else {
      return matcher;
    }
  }

  /**
   * Returns all but the first matcher from a sequence or True if there is only a single
   * matcher in the sequence.
   */
  static Matcher tail(Matcher matcher) {
    if (matcher instanceof SeqMatcher) {
      List<Matcher> ms = matcher.<SeqMatcher>as().matchers();
      return SeqMatcher.create(ms.subList(1, ms.size()));
    } else {
      return TrueMatcher.INSTANCE;
    }
  }

  /**
   * Get the prefix matcher. This is similar to {@link #head(Matcher)} except that it can
   * reach into character sequences as well as higher level sequences.
   */
  static Matcher getPrefix(Matcher matcher) {
    if (matcher instanceof SeqMatcher) {
      List<Matcher> ms = matcher.<SeqMatcher>as().matchers();
      return ms.get(0);
    } else if (matcher instanceof ZeroOrMoreMatcher) {
      ZeroOrMoreMatcher zm = matcher.as();
      return new ZeroOrMoreMatcher(zm.repeated(), TrueMatcher.INSTANCE);
    } else if (matcher instanceof CharSeqMatcher) {
      String pattern = matcher.<CharSeqMatcher>as().pattern();
      return pattern.isEmpty() ? null : new CharSeqMatcher(pattern.charAt(0));
    } else {
      return matcher;
    }
  }

  /**
   * Get the suffix matcher. This is similar to {@link #tail(Matcher)} except that it intended
   * to be used with {@link #getPrefix(Matcher)}
   */
  static Matcher getSuffix(Matcher matcher) {
    if (matcher instanceof SeqMatcher) {
      List<Matcher> ms = matcher.<SeqMatcher>as().matchers();
      return SeqMatcher.create(ms.subList(1, ms.size()));
    } else if (matcher instanceof ZeroOrMoreMatcher) {
      ZeroOrMoreMatcher zm = matcher.as();
      return zm.next();
    } else if (matcher instanceof CharSeqMatcher) {
      String pattern = matcher.<CharSeqMatcher>as().pattern();
      return pattern.length() <= 1 ? TrueMatcher.INSTANCE : new CharSeqMatcher(pattern.substring(1));
    } else {
      return TrueMatcher.INSTANCE;
    }
  }
}
