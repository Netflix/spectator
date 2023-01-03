/*
 * Copyright 2014-2023 Netflix, Inc.
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
package com.netflix.spectator.jvm;

import java.beans.Introspector;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.function.DoubleBinaryOperator;

/**
 * Helper for handling basic expressions uses as part of the mapping config.
 */
final class MappingExpr {

  private MappingExpr() {
  }

  /**
   * On Java 8, the {@code String.replace} call uses a regex internally which results
   * in a considerable overhead. This does a simple replace without using regex.
   */
  static String replace(String str, String target, String replacement) {
    int pos = str.indexOf(target);
    if (pos == -1) {
      return str;
    }

    StringBuilder builder = new StringBuilder();
    String tmp = str;
    while (pos >= 0) {
      builder.append(tmp.substring(0, pos)).append(replacement);
      tmp = tmp.substring(pos + target.length());
      pos = tmp.indexOf(target);
    }
    builder.append(tmp);

    return builder.toString();
  }

  /**
   * Substitute named variables in the pattern string with the corresponding
   * values in the variables map.
   *
   * @param pattern
   *     Pattern string with placeholders, name surounded by curly braces, e.g.:
   *     {@code {variable name}}.
   * @param vars
   *     Map of variable substitutions that are available.
   * @return
   *     String with values substituted in. If no matching key is found for a
   *     placeholder, then it will not be modified and left in place.
   */
  static String substitute(String pattern, Map<String, String> vars) {
    String value = pattern;
    for (Map.Entry<String, String> entry : vars.entrySet()) {
      String raw = entry.getValue();
      String v = Introspector.decapitalize(raw);
      value = replace(value, "{raw:" + entry.getKey() + "}", raw);
      value = replace(value, "{" + entry.getKey() + "}", v);
    }
    return value;
  }

  /**
   * Evaluate a simple stack expression for the value.
   *
   * @param expr
   *     Basic stack expression that supports placeholders, numeric constants,
   *     and basic binary operations (:add, :sub, :mul, :div).
   * @param vars
   *     Map of variable substitutions that are available.
   * @return
   *     Double value for the expression. If the expression cannot be evaluated
   *     properly, then null will be returned.
   */
  @SuppressWarnings("PMD")
  static Double eval(String expr, Map<String, ? extends Number> vars) {
    Deque<Double> stack = new ArrayDeque<>();
    String[] parts = expr.split("[,\\s]+");
    for (String part : parts) {
      switch (part) {
        case ":add":        binaryOp(stack, (a, b) -> a + b); break;
        case ":sub":        binaryOp(stack, (a, b) -> a - b); break;
        case ":mul":        binaryOp(stack, (a, b) -> a * b); break;
        case ":div":        binaryOp(stack, (a, b) -> a / b); break;
        case ":if-changed": ifChanged(stack);                 break;
        default:
          if (part.startsWith("{") && part.endsWith("}")) {
            Number v = vars.get(part.substring(1, part.length() - 1));
            if (v == null) v = Double.NaN;
            stack.addFirst(v.doubleValue());
          } else {
            stack.addFirst(Double.parseDouble(part));
          }
          break;
      }
    }
    return stack.removeFirst();
  }

  private static void binaryOp(Deque<Double> stack, DoubleBinaryOperator op) {
    double b = stack.removeFirst();
    double a = stack.removeFirst();
    stack.addFirst(op.applyAsDouble(a, b));
  }

  /**
   * Helper to zero out a value if there is not a change. For a stack with {@code num v1 v2},
   * if {@code v1 == v2}, then push 0.0 otherwise push {@code num}.
   *
   * For some values placed in JMX they are not regularly updated in all circumstances and
   * reporting the same value for each polling iteration gives the false impression of activity
   * when there is none. A common example is timers with the metrics library where the reservoir
   * is not rescaled during a fetch.
   *
   * https://github.com/dropwizard/metrics/issues/1030
   *
   * This operator can be used in conjunction with the previous variables to zero out the
   * misleading snapshots based on the count. For example:
   *
   * <pre>
   *   {50thPercentile},{Count},{previous:Count},:if-changed
   * </pre>
   */
  private static void ifChanged(Deque<Double> stack) {
    double v2 = stack.removeFirst();
    double v1 = stack.removeFirst();
    double num = stack.removeFirst();
    stack.addFirst((Double.compare(v1, v2) == 0) ? 0.0 : num);
  }
}
