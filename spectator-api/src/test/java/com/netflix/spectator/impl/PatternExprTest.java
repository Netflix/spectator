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
    // Position is not fixed, so the lookahead is rewritten rather than split into a NOT
    PatternExpr expr = PatternMatcher.compile("foo-(?!bar)").toPatternExpr(50);
    Assertions.assertEquals(
        "'.*foo-($|[^b]|(b($|[^a]|(a($|[^r])))))'",
        expr.toString());
    Assertions.assertTrue(expr.matches("foo-bar foo-baz"));
    Assertions.assertTrue(expr.matches("foo-"));
    Assertions.assertTrue(expr.matches("foo-ba"));
    Assertions.assertFalse(expr.matches("foo-bar"));
  }

  @Test
  public void indexOfNegativeLookaheadIgnoreCase() {
    PatternExpr expr = PatternMatcher.compile("(?i)foo-(?!bar)").toPatternExpr(50);
    Assertions.assertEquals(
        "'(?i).*foo-($|[^Bb]|([Bb]($|[^Aa]|([Aa]($|[^Rr])))))'",
        expr.toString());
    Assertions.assertTrue(expr.matches("FOO-BAZ"));
    Assertions.assertFalse(expr.matches("FOO-BAR"));
    Assertions.assertFalse(expr.matches("foo-Bar"));
  }

  @Test
  public void indexOfNegativeLookaheadThenIgnoreCase() {
    // The case is folded before negation, so [^b] matches neither 'b' nor 'B'
    PatternExpr expr = PatternMatcher.compile("foo-(?!bar)").toPatternExpr(50).ignoreCase();
    Assertions.assertEquals(
        "'(?i).*foo-($|[^Bb]|(b($|[^Aa]|(a($|[^Rr])))))'",
        expr.toString());
    Assertions.assertTrue(expr.matches("FOO-BAZ"));
    Assertions.assertFalse(expr.matches("FOO-BAR"));
    Assertions.assertFalse(expr.matches("foo-bar"));
  }

  @Test
  public void indexOfNegativeLookaheadCharClass() {
    PatternExpr expr = PatternMatcher.compile("foo-(?![a-c].)").toPatternExpr(50);
    Assertions.assertEquals("'.*foo-($|[^a-c]|([a-c]$))'", expr.toString());
    Assertions.assertTrue(expr.matches("foo-a"));
    Assertions.assertTrue(expr.matches("foo-d"));
    Assertions.assertFalse(expr.matches("foo-ab"));
  }

  @Test
  public void negativeLookaheadInvertedSetNotNegatedClass() {
    // The inverted set would be written as [0-9] which would not match non-ascii
    // characters for engines like RE2, so it cannot be inlined
    PatternExpr expr = PatternMatcher.compile("foo-(?![\\x00-/:-\\x7f])").toPatternExpr(50);
    Assertions.assertNull(expr);
  }

  @Test
  public void negativeLookaheadInvertedSetKeepsFold() {
    // The inverted set, [a{-\x7f], is written as a negated class so that 'a' would not
    // match if the case is ignored later, the same as the lookahead
    PatternExpr expr = PatternMatcher.compile("foo-(?![\\x00-\\x60b-z])").toPatternExpr(50);
    Assertions.assertEquals("'.*foo-($|[^-\\x00-`b-z])'", expr.toString());
    Assertions.assertTrue(expr.matches("foo-"));
    Assertions.assertTrue(expr.matches("foo-a"));
    Assertions.assertTrue(expr.matches("foo-{"));
    Assertions.assertFalse(expr.matches("foo-b"));
    Assertions.assertFalse(expr.matches("foo-A"));
    Assertions.assertFalse(expr.ignoreCase().matches("foo-a"));
  }

  @Test
  public void negativeLookaheadDashInRange() {
    // The dash is in the middle of the range, so the set should not be split around it
    // which would make the negated class longer than the inverted set.
    PatternExpr expr = PatternMatcher.compile("foo-(?![$-x{-}])").toPatternExpr(50);
    Assertions.assertNotNull(expr);
    Assertions.assertEquals("'.*foo-($|[^-\\$-x\\{-\\}])'", expr.toString());
    Assertions.assertTrue(expr.matches("foo-"));
    Assertions.assertTrue(expr.matches("foo-y"));
    Assertions.assertFalse(expr.matches("foo--"));
    Assertions.assertFalse(expr.matches("foo-a"));
  }

  @Test
  public void negativeLookaheadAllAscii() {
    // Inverted set is empty, but a non-ascii character would not match the lookahead
    PatternExpr expr = PatternMatcher.compile("foo-(?![\\x00-\\x7f])").toPatternExpr(50);
    Assertions.assertNull(expr);
  }

  @Test
  public void negativeLookaheadTooLongToInline() {
    // Each character adds a level of nesting, so long lookaheads are not inlined
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < 10_000; ++i) {
      builder.append((char) ('a' + i % 26));
    }
    String lookahead = builder.toString();
    Assertions.assertNull(PatternMatcher.compile("foo-(?!" + lookahead + ")").toPatternExpr(50));

    // Fixed position can still be split into a separate clause
    PatternExpr expr = PatternMatcher.compile("^foo-(?!" + lookahead + ")").toPatternExpr(50);
    Assertions.assertEquals("(NOT '^foo-" + lookahead + "' AND '^foo-')", expr.toString());
  }

  @Test
  public void negativeLookaheadFollowedByContent() {
    // Position is not fixed and something follows the lookahead
    PatternExpr expr = PatternMatcher.compile("foo-(?!bar)x").toPatternExpr(50);
    Assertions.assertNull(expr);
  }

  @Test
  public void negativeLookaheadFixedPosition() {
    PatternExpr expr = PatternMatcher.compile("^[a-z]{3}(?!bar)x").toPatternExpr(50);
    Assertions.assertEquals(
        "(NOT '(^(?:[a-z]){3}bar)' AND '(^(?:[a-z]){3}x)')",
        expr.toString());
  }

  @Test
  public void positiveLookaheadFollowedByContent() {
    // Position is not fixed, the lookahead and the content after could match at different
    // positions, e.g. "ba" would match both '.*b.' and '.*a'
    PatternExpr expr = PatternMatcher.compile("(?=b.)a").toPatternExpr(50);
    Assertions.assertNull(expr);
  }

  @Test
  public void positiveLookaheadFixedPosition() {
    PatternExpr expr = PatternMatcher.compile("^(?=b.)a").toPatternExpr(50);
    Assertions.assertEquals("('(^b.)' AND '^a')", expr.toString());
  }

  @Test
  public void indexOfPositiveLookahead() {
    PatternExpr expr = PatternMatcher.compile("foo-(?=bar)").toPatternExpr(50);
    Assertions.assertEquals("('.*foo-bar' AND '.*foo-')", expr.toString());
  }

  @Test
  public void seqNegativeLookahead() {
    // Position is not fixed and the first lookahead is followed by another
    Assertions.assertNull(PatternMatcher.compile("foo-(?!bar)(?!baz)").toPatternExpr(50));

    PatternExpr expr = PatternMatcher.compile("^foo-(?!bar)(?!baz)").toPatternExpr(50);
    Assertions.assertEquals(
        "(NOT '^foo-bar' AND NOT '^foo-baz' AND '^foo-')",
        expr.toString());
  }

  @Test
  public void seqPositiveLookahead() {
    // Position is not fixed and the first lookahead is followed by another
    Assertions.assertNull(PatternMatcher.compile("foo-(?=bar)(?=baz)").toPatternExpr(50));

    PatternExpr expr = PatternMatcher.compile("^foo-(?=bar)(?=baz)").toPatternExpr(50);
    Assertions.assertEquals(
        "('^foo-bar' AND '^foo-baz' AND '^foo-')",
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
  public void orWithBranchThatCannotBeSimplified() {
    // One branch of the expanded OR has a nested lookahead that cannot be simplified, so
    // the overall expression cannot be either
    PatternExpr expr = PatternMatcher.compile("(?=a)((?=.)a|b)?").toPatternExpr(50);
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
    Assertions.assertEquals(
        "'(.)*(foo-)*($|[^b]|(b($|[^a]|(a($|[^r])))))'",
        expr.toString());
    // Matches at the end of any string
    Assertions.assertTrue(expr.matches("bar"));
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
    Assertions.assertEquals(
        "'(.)*(?:foo-)?($|[^b]|(b($|[^a]|(a($|[^r])))))'",
        expr.toString());
    Assertions.assertTrue(expr.matches("bar"));
  }

  @Test
  public void zeroOrOneNextPositiveLookahead() {
    PatternExpr expr = PatternMatcher.compile("(foo-)?(?=bar)").toPatternExpr(50);
    Assertions.assertEquals("('(.)*(?:foo-)?bar' AND '(.)*(?:foo-)?')", expr.toString());
  }
}
