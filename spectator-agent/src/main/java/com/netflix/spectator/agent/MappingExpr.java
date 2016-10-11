/*
 * Copyright 2014-2016 Netflix, Inc.
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
package com.netflix.spectator.agent;

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
      String v = Introspector.decapitalize(entry.getValue());
      value = value.replace("{" + entry.getKey() + "}", v);
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
        case ":add": binaryOp(stack, (a, b) -> a + b); break;
        case ":sub": binaryOp(stack, (a, b) -> a - b); break;
        case ":mul": binaryOp(stack, (a, b) -> a * b); break;
        case ":div": binaryOp(stack, (a, b) -> a / b); break;
        default:
          if (part.startsWith("{") && part.endsWith("}")) {
            Number v = vars.get(part.substring(1, part.length() - 1));
            if (v == null) return null;
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
}
