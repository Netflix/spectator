/*
 * Copyright 2014-2024 Netflix, Inc.
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
package com.netflix.spectator.atlas.impl;

import com.netflix.spectator.impl.matcher.PatternUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Parses an Atlas data or query expression.
 *
 * <b>Classes in this package are only intended for use internally within spectator. They may
 * change at any time and without notice.</b>
 */
public final class Parser {

  private Parser() {
  }

  /**
   * Parse an <a href="https://github.com/Netflix/atlas/wiki/Reference-data">Atlas data
   * expression</a>.
   */
  public static DataExpr parseDataExpr(String expr) {
    try {
      return (DataExpr) parse(expr);
    } catch (ClassCastException e) {
      throw new IllegalArgumentException("invalid data expression: " + expr, e);
    }
  }

  /**
   * Parse an <a href="https://github.com/Netflix/atlas/wiki/Reference-query">Atlas query
   * expression</a>.
   */
  public static Query parseQuery(String expr) {
    try {
      return (Query) parse(expr);
    } catch (ClassCastException e) {
      throw new IllegalArgumentException("invalid query expression: " + expr, e);
    }
  }

  @SuppressWarnings({"unchecked", "checkstyle:MethodLength", "PMD"})
  private static Object parse(String expr) {
    DataExpr.AggregateFunction af;
    Query q, q1, q2;
    String k, v;
    int depth = 0;
    List<String> tmp;
    List<String> vs = null;
    String[] parts = expr.split(",");
    Deque<Object> stack = new ArrayDeque<>(parts.length);
    for (String p : parts) {
      // Structural tokens (parentheses and `:operators`) are matched against the raw
      // token. Unescaping is deferred until a token is consumed as a literal value (see
      // the default case below) so that escaped special characters such as `(` or
      // `:in` are treated as data rather than as syntax. This mirrors the behavior of
      // the Atlas server parser (Interpreter.nextStep / popAndPushList).
      String token = p.trim();
      if (token.isEmpty()) {
        continue;
      }
      if (vs != null && (depth > 0 || !")".equals(token))) {
        if ("(".equals(token)) {
          ++depth;
        } else if (")".equals(token)) {
          --depth;
        }
        // Structure is determined from the raw token above, but the value stored in the
        // list is unescaped so that escaped special characters are treated as data.
        vs.add(unescape(token));
        continue;
      }
      switch (token) {
        case "(":
          vs = new ArrayList<>();
          break;
        case ")":
          if (vs == null) {
            throw new IllegalArgumentException("unmatched closing paren: " + expr);
          }
          stack.push(vs);
          vs = null;
          depth = 0;
          break;
        case ":true":
          stack.push(Query.TRUE);
          break;
        case ":false":
          stack.push(Query.FALSE);
          break;
        case ":and":
          q2 = (Query) stack.pop();
          q1 = (Query) stack.pop();
          stack.push(q1.and(q2));
          break;
        case ":or":
          q2 = (Query) stack.pop();
          q1 = (Query) stack.pop();
          stack.push(q1.or(q2));
          break;
        case ":not":
          q = (Query) stack.pop();
          stack.push(q.not());
          break;
        case ":has":
          k = (String) stack.pop();
          stack.push(new Query.Has(k));
          break;
        case ":eq":
          v = (String) stack.pop();
          k = (String) stack.pop();
          stack.push(new Query.Equal(k, v));
          break;
        case ":in":
          tmp = (List<String>) stack.pop();
          k = (String) stack.pop();
          pushIn(stack, k, tmp);
          break;
        case ":lt":
          v = (String) stack.pop();
          k = (String) stack.pop();
          stack.push(new Query.LessThan(k, v));
          break;
        case ":le":
          v = (String) stack.pop();
          k = (String) stack.pop();
          stack.push(new Query.LessThanEqual(k, v));
          break;
        case ":gt":
          v = (String) stack.pop();
          k = (String) stack.pop();
          stack.push(new Query.GreaterThan(k, v));
          break;
        case ":ge":
          v = (String) stack.pop();
          k = (String) stack.pop();
          stack.push(new Query.GreaterThanEqual(k, v));
          break;
        case ":re":
          v = (String) stack.pop();
          k = (String) stack.pop();
          pushRegex(stack, new Query.Regex(k, v));
          break;
        case ":reic":
          v = (String) stack.pop();
          k = (String) stack.pop();
          pushRegex(stack, new Query.Regex(k, v, true, ":reic"));
          break;
        case ":contains":
          v = (String) stack.pop();
          k = (String) stack.pop();
          pushRegex(stack, new Query.Regex(k, ".*" + PatternUtils.escape(v)));
          break;
        case ":starts":
          v = (String) stack.pop();
          k = (String) stack.pop();
          pushRegex(stack, new Query.Regex(k, PatternUtils.escape(v)));
          break;
        case ":ends":
          v = (String) stack.pop();
          k = (String) stack.pop();
          pushRegex(stack, new Query.Regex(k, ".*" + PatternUtils.escape(v) + "$"));
          break;
        case ":all":
          q = (Query) stack.pop();
          stack.push(new DataExpr.All(q));
          break;
        case ":sum":
          q = (Query) stack.pop();
          stack.push(new DataExpr.Sum(q));
          break;
        case ":min":
          q = (Query) stack.pop();
          stack.push(new DataExpr.Min(q));
          break;
        case ":max":
          q = (Query) stack.pop();
          stack.push(new DataExpr.Max(q));
          break;
        case ":count":
          q = (Query) stack.pop();
          stack.push(new DataExpr.Count(q));
          break;
        case ":by":
          tmp = (List<String>) stack.pop();
          af = (DataExpr.AggregateFunction) stack.pop();
          stack.push(new DataExpr.GroupBy(af, new LinkedHashSet<>(tmp)));
          break;
        case ":rollup-drop":
          tmp = (List<String>) stack.pop();
          af = (DataExpr.AggregateFunction) stack.pop();
          stack.push(new DataExpr.DropRollup(af, tmp));
          break;
        case ":rollup-keep":
          tmp = (List<String>) stack.pop();
          af = (DataExpr.AggregateFunction) stack.pop();
          stack.push(new DataExpr.KeepRollup(af, tmp));
          break;
        default:
          if (token.startsWith(":")) {
            throw new IllegalArgumentException("unknown word '" + token + "'");
          }
          stack.push(unescape(token));
          break;
      }
    }
    Object obj = stack.pop();
    if (!stack.isEmpty()) {
      throw new IllegalArgumentException("too many items remaining on stack: " + stack);
    }
    return obj;
  }

  private static void pushRegex(Deque<Object> stack, Query.Regex q) {
    if (q.alwaysMatches()) {
      stack.push(new Query.Has(q.key()));
    } else {
      stack.push(q);
    }
  }

  private static void pushIn(Deque<Object> stack, String k, List<String> values) {
    if (values.size() == 1)
      stack.push(new Query.Equal(k, values.get(0)));
    else
      stack.push(new Query.In(k, new HashSet<>(values)));
  }

  static boolean isSpecial(int codePoint) {
    // The comma is used as the token separator and the colon as the prefix for operators,
    // so both need to be escaped when they appear within a key or value. This matches the
    // set of special characters used by the Atlas server (Interpreter.isSpecial).
    return codePoint == ',' || codePoint == ':' || Character.isWhitespace(codePoint);
  }

  static void zeroPad(String str, StringBuilder builder) {
    final int width = 4;
    final int n = width - str.length();
    for (int i = 0; i < n; ++i) {
      builder.append('0');
    }
    builder.append(str);
  }

  private static void escapeCodePoint(int codePoint, StringBuilder builder) {
    builder.append("\\u");
    zeroPad(Integer.toHexString(codePoint), builder);
  }

  private static boolean isHexDigit(char c) {
    return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
  }

  /**
   * Returns true if the backslash at position {@code i} begins a {@code \\uXXXX} escape
   * sequence, i.e. it is followed by a {@code u} and four hex digits. Used so that a value
   * literally containing such a sequence can be escaped and round-trip through {@link #unescape}.
   */
  private static boolean beginsUnicodeEscape(String str, int i) {
    return str.length() - i > 5
        && str.charAt(i + 1) == 'u'
        && isHexDigit(str.charAt(i + 2))
        && isHexDigit(str.charAt(i + 3))
        && isHexDigit(str.charAt(i + 4))
        && isHexDigit(str.charAt(i + 5));
  }

  /**
   * Escape special characters in the input string to unicode escape sequences (uXXXX).
   */
  @SuppressWarnings("PMD")
  public static String escape(String str) {
    // A token consisting of a single parenthesis is structural in the stack language, so
    // when a parenthesis is used as a value it must be escaped even though it is not
    // treated as special in the middle of a larger string. This matches the handling in
    // the Atlas server (Interpreter.escape).
    if ("(".equals(str)) {
      return "\\u0028";
    } else if (")".equals(str)) {
      return "\\u0029";
    }
    final int length = str.length();
    StringBuilder builder = new StringBuilder(length);
    for (int i = 0; i < length;) {
      final int cp = str.codePointAt(i);
      final int len = Character.charCount(cp);
      // Escape special characters, and also a backslash that begins a unicode escape sequence
      // so that a value literally containing "\\uXXXX" is not decoded back into a different
      // character by unescape().
      if (isSpecial(cp) || (cp == '\\' && beginsUnicodeEscape(str, i)))
        escapeCodePoint(cp, builder);
      else
        builder.appendCodePoint(cp);
      i += len;
    }
    return builder.toString();
  }

  /**
   * Unescape unicode characters in the input string. Ignore any invalid or unrecognized
   * escape sequences.
   */
  @SuppressWarnings("PMD")
  public static String unescape(String str) {
    // Fast path: escape sequences always start with a backslash, so if there is none the
    // input is already unescaped and there is no need to allocate a StringBuilder. This is
    // the common case as most keys and values do not contain special characters.
    if (str.indexOf('\\') < 0) {
      return str;
    }
    final int length = str.length();
    StringBuilder builder = new StringBuilder(length);
    for (int i = 0; i < length; ++i) {
      final char c = str.charAt(i);
      if (c == '\\' && beginsUnicodeEscape(str, i)) {
        // beginsUnicodeEscape has verified there are four hex digits, so the parse cannot fail.
        int cp = Integer.parseInt(str.substring(i + 2, i + 6), 16);
        builder.appendCodePoint(cp);
        i += 5;
      } else {
        // Not a complete unicode escape (incomplete, non-hex, or some other escape such as
        // "\\d"); copy the character through unchanged.
        builder.append(c);
      }
    }
    return builder.toString();
  }
}
