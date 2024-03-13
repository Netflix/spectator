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
        vs.add(token);
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
          stack.push(token);
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
}
