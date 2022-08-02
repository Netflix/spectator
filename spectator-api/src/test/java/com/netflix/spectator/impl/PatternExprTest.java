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
package com.netflix.spectator.impl;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

public class PatternExprTest {

  private List<PatternExpr> list(PatternExpr... exprs) {
    return Arrays.asList(exprs);
  }

  private PatternExpr sampleExpr() {
    return new PatternExpr.And(list(
        new PatternExpr.Not(
            new PatternExpr.And(list(
                new PatternExpr.Regex(PatternMatcher.compile("a")),
                new PatternExpr.Regex(PatternMatcher.compile("b"))
            ))
        ),
        new PatternExpr.Or(list(
            new PatternExpr.Regex(PatternMatcher.compile("c")),
            new PatternExpr.Regex(PatternMatcher.compile("d")),
            new PatternExpr.Regex(PatternMatcher.compile("e"))
        )),
        new PatternExpr.Or(list(
            new PatternExpr.Regex(PatternMatcher.compile("f"))
        )),
        new PatternExpr.And(list(
            new PatternExpr.Regex(PatternMatcher.compile("g"))
        ))
    ));
  }

  @Test
  public void infix() {
    PatternExpr.Encoder encoder = new PatternExpr.Encoder() {

      @Override public String regex(PatternMatcher matcher) {
        return "'" + matcher + "'";
      }

      @Override public String startAnd() {
        return "(";
      }

      @Override public String separatorAnd() {
        return " AND ";
      }

      @Override public String endAnd() {
        return ")";
      }

      @Override public String startOr() {
        return "(";
      }

      @Override public String separatorOr() {
        return " OR ";
      }

      @Override public String endOr() {
        return ")";
      }

      @Override public String startNot() {
        return "NOT ";
      }

      @Override public String endNot() {
        return "";
      }
    };

    String query = sampleExpr().toQueryString(encoder);
    Assertions.assertEquals("(NOT ('.*a' AND '.*b') AND ('.*c' OR '.*d' OR '.*e') AND '.*f' AND '.*g')", query);
  }

  @Test
  public void postfix() {
    PatternExpr.Encoder encoder = new PatternExpr.Encoder() {

      @Override public String regex(PatternMatcher matcher) {
        return matcher + ",:re";
      }

      @Override public String startAnd() {
        return "(,";
      }

      @Override public String separatorAnd() {
        return ",";
      }

      @Override public String endAnd() {
        return ",),:and";
      }

      @Override public String startOr() {
        return "(,";
      }

      @Override public String separatorOr() {
        return ",";
      }

      @Override public String endOr() {
        return ",),:or";
      }

      @Override public String startNot() {
        return "";
      }

      @Override public String endNot() {
        return ",:not";
      }
    };

    String query = sampleExpr().toQueryString(encoder);
    Assertions.assertEquals("(,(,.*a,:re,.*b,:re,),:and,:not,(,.*c,:re,.*d,:re,.*e,:re,),:or,.*f,:re,.*g,:re,),:and", query);
  }

  @Test
  public void functions() {
    PatternExpr.Encoder encoder = new PatternExpr.Encoder() {

      @Override public String regex(PatternMatcher matcher) {
        return "'" + matcher + "'";
      }

      @Override public String startAnd() {
        return "and(";
      }

      @Override public String separatorAnd() {
        return ",";
      }

      @Override public String endAnd() {
        return ")";
      }

      @Override public String startOr() {
        return "or(";
      }

      @Override public String separatorOr() {
        return ",";
      }

      @Override public String endOr() {
        return ")";
      }

      @Override public String startNot() {
        return "not(";
      }

      @Override public String endNot() {
        return ")";
      }
    };

    String query = sampleExpr().toQueryString(encoder);
    Assertions.assertEquals("and(not(and('.*a','.*b')),or('.*c','.*d','.*e'),'.*f','.*g')", query);
  }

  @Test
  public void equalsContractRegex() {
    EqualsVerifier.forClass(PatternExpr.Regex.class)
        .withNonnullFields("matcher")
        .verify();
  }

  @Test
  public void equalsContractAnd() {
    EqualsVerifier.forClass(PatternExpr.And.class)
        .withNonnullFields("exprs")
        .verify();
  }

  @Test
  public void equalsContractOr() {
    EqualsVerifier.forClass(PatternExpr.Or.class)
        .withNonnullFields("exprs")
        .verify();
  }

  @Test
  public void equalsContractNot() {
    EqualsVerifier.forClass(PatternExpr.Not.class)
        .withNonnullFields("expr")
        .verify();
  }

  @Test
  public void indexOfNegativeLookahead() {
    PatternExpr expr = PatternMatcher.compile("foo-(?!bar)").toPatternExpr(50);
    Assertions.assertEquals("(NOT '.*foo-bar' AND '.*foo-')", expr.toString());
  }

  @Test
  public void indexOfPositiveLookahead() {
    PatternExpr expr = PatternMatcher.compile("foo-(?=bar)").toPatternExpr(50);
    Assertions.assertEquals("('.*foo-bar' AND '.*foo-')", expr.toString());
  }

  @Test
  public void seqNegativeLookahead() {
    PatternExpr expr = PatternMatcher.compile("foo-(?!bar)(?!baz)").toPatternExpr(50);
    Assertions.assertEquals(
        "(NOT '.*foo-bar' AND NOT '.*foo-baz' AND '.*foo-')",
        expr.toString());
  }

  @Test
  public void seqPositiveLookahead() {
    PatternExpr expr = PatternMatcher.compile("foo-(?=bar)(?=baz)").toPatternExpr(50);
    Assertions.assertEquals(
        "('.*foo-bar' AND '.*foo-baz' AND '.*foo-')",
        expr.toString());
  }

  @Test
  public void repeatedNegativeLookahead() {
    PatternExpr expr = PatternMatcher.compile("(foo(?!bar)){4}").toPatternExpr(50);
    Assertions.assertNull(expr);
  }

  @Test
  public void repeatedPositiveLookahead() {
    PatternExpr expr = PatternMatcher.compile("(foo(?=bar)){4}").toPatternExpr(50);
    Assertions.assertNull(expr);
  }

  @Test
  public void zeroOrMoreRepeatedNegativeLookahead() {
    PatternExpr expr = PatternMatcher.compile("((?!bar)foo)*baz").toPatternExpr(50);
    Assertions.assertNull(expr);
  }

  @Test
  public void zeroOrMoreRepeatedPositiveLookahead() {
    PatternExpr expr = PatternMatcher.compile("((?=bar)foo)*baz").toPatternExpr(50);
    Assertions.assertNull(expr);
  }

  @Test
  public void zeroOrMoreNextNegativeLookahead() {
    PatternExpr expr = PatternMatcher.compile("(foo-)*(?!bar)").toPatternExpr(50);
    Assertions.assertEquals("(NOT '(.)*(foo-)*bar' AND '(.)*(foo-)*')", expr.toString());
  }

  @Test
  public void zeroOrMoreNextPositiveLookahead() {
    PatternExpr expr = PatternMatcher.compile("(foo-)*(?=bar)").toPatternExpr(50);
    Assertions.assertEquals("('(.)*(foo-)*bar' AND '(.)*(foo-)*')", expr.toString());
  }

  @Test
  public void zeroOrOneRepeatedNegativeLookahead() {
    PatternExpr expr = PatternMatcher.compile("((?!bar)foo)?baz").toPatternExpr(50);
    Assertions.assertNull(expr);
  }

  @Test
  public void zeroOrOneRepeatedPositiveLookahead() {
    PatternExpr expr = PatternMatcher.compile("((?=bar)foo)?baz").toPatternExpr(50);
    Assertions.assertNull(expr);
  }

  @Test
  public void zeroOrOneNextNegativeLookahead() {
    PatternExpr expr = PatternMatcher.compile("(foo-)?(?!bar)").toPatternExpr(50);
    Assertions.assertEquals("(NOT '(.)*(?:foo-)?bar' AND '(.)*(?:foo-)?')", expr.toString());
  }

  @Test
  public void zeroOrOneNextPositiveLookahead() {
    PatternExpr expr = PatternMatcher.compile("(foo-)?(?=bar)").toPatternExpr(50);
    Assertions.assertEquals("('(.)*(?:foo-)?bar' AND '(.)*(?:foo-)?')", expr.toString());
  }
}
