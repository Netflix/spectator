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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Helper functions for performing rewrites on the pattern to help improve the performance
 * of matching.
 */
final class Optimizer {

  // Safety check to avoid an endless loop if there is a bug that the optimizer does not
  // converge on an optimal solution.
  private static final int MAX_ITERATIONS = 1000;

  private Optimizer() {
  }

  /** Return a new instance of the matcher that has been optimized. */
  static Matcher optimize(Matcher matcher) {
    Matcher m = matcher;
    Matcher opt = optimizeSinglePass(m);
    for (int i = 0; !m.equals(opt) && i < MAX_ITERATIONS; ++i) {
      m = opt;
      opt = optimizeSinglePass(m);
    }
    return opt;
  }

  private static Matcher optimizeSinglePass(Matcher matcher) {
    return matcher
        .rewrite(Optimizer::mergeNext)
        .rewrite(Optimizer::removeTrueInSequence)
        .rewrite(Optimizer::sequenceWithFalseIsFalse)
        .rewrite(Optimizer::sequenceWithStuffAfterEndIsFalse)
        .rewrite(Optimizer::zeroOrMoreFalse)
        .rewrite(Optimizer::convertEmptyCharClassToFalse)
        .rewrite(Optimizer::convertSingleCharClassToSeq)
        .rewrite(Optimizer::removeStartFollowedByMatchAny)
        .rewrite(Optimizer::removeMatchAnyFollowedByStart)
        .rewrite(Optimizer::removeMatchAnyFollowedByIndexOf)
        .rewrite(Optimizer::removeSequentialMatchAny)
        .rewrite(Optimizer::flattenNestedSequences)
        .rewrite(Optimizer::flattenNestedOr)
        .rewrite(Optimizer::dedupOr)
        .rewrite(Optimizer::removeFalseBranchesFromOr)
        .rewrite(Optimizer::extractPrefixFromOr)
        .rewrite(Optimizer::inlineMatchAnyPrecedingOr)
        .rewrite(Optimizer::startsWithCharSeq)
        .rewrite(Optimizer::combineCharSeqAfterStartsWith)
        .rewrite(Optimizer::combineCharSeqAfterIndexOf)
        .rewrite(Optimizer::combineAdjacentCharSeqs)
        .rewrite(Optimizer::removeRepeatedStart)
        .rewrite(Optimizer::combineAdjacentStart)
        .rewrite(Optimizer::convertRepeatedAnyCharSeqToIndexOf)
        .rewriteEnd(Optimizer::removeTrailingMatchAny);
  }

  /**
   * For greedy matchers go ahead and include the next portion of the match so it can be
   * checked as we go along. Since there is no support for capturing the value we can stop
   * as soon as it is detected there would be a match rather than finding the largest possible
   * match.
   */
  static Matcher mergeNext(Matcher matcher) {
    if (matcher instanceof SeqMatcher) {
      List<Matcher> matchers = matcher.<SeqMatcher>as().matchers();
      List<Matcher> ms = new ArrayList<>();
      for (int i = 0; i < matchers.size(); ++i) {
        Matcher m = matchers.get(i);
        if (m instanceof OrMatcher) {
          // Don't merge sequential OR clauses
          if (i + 1 < matchers.size() && matchers.get(i + 1) instanceof OrMatcher) {
            ms.add(m);
          } else {
            List<Matcher> after = matchers.subList(i + 1, matchers.size());
            ms.add(m.<GreedyMatcher>as().mergeNext(SeqMatcher.create(after)));
            break;
          }
        } else if (m instanceof GreedyMatcher) {
          List<Matcher> after = matchers.subList(i + 1, matchers.size());
          ms.add(m.<GreedyMatcher>as().mergeNext(SeqMatcher.create(after)));
          break;
        } else {
          ms.add(m);
        }
      }
      return SeqMatcher.create(ms);
    }
    return matcher;
  }

  /**
   * The true matcher is sometimes used as a placeholder while parsing. For sequences it isn't
   * needed and it is faster to leave them out.
   */
  static Matcher removeTrueInSequence(Matcher matcher) {
    if (matcher instanceof SeqMatcher) {
      List<Matcher> matchers = matcher.<SeqMatcher>as().matchers();
      List<Matcher> ms = new ArrayList<>();
      for (Matcher m : matchers) {
        if (!(m instanceof TrueMatcher)) {
          ms.add(m);
        }
      }
      return SeqMatcher.create(ms);
    }
    return matcher;
  }

  /**
   * If a sequence contains an explicit false matcher then the whole sequence will never match
   * and can be treated as false.
   */
  static Matcher sequenceWithFalseIsFalse(Matcher matcher) {
    if (matcher instanceof SeqMatcher) {
      for (Matcher m : matcher.<SeqMatcher>as().matchers()) {
        if (m instanceof FalseMatcher) {
          return FalseMatcher.INSTANCE;
        }
      }
    }
    return matcher;
  }

  /**
   * If a sequence contains content after an end anchor then it will never be able to match
   * and can be treated as false.
   */
  static Matcher sequenceWithStuffAfterEndIsFalse(Matcher matcher) {
    if (matcher instanceof SeqMatcher) {
      boolean end = false;
      for (Matcher m : matcher.<SeqMatcher>as().matchers()) {
        if (m instanceof EndMatcher) {
          end = true;
        } else if (end && !m.alwaysMatches()) {
          return FalseMatcher.INSTANCE;
        }
      }
    }
    return matcher;
  }

  /**
   * If the match after a repeated pattern is false, then treat the whole match as false.
   * For example: {@code ".*$." => "$."}.
   */
  static Matcher zeroOrMoreFalse(Matcher matcher) {
    if (matcher instanceof ZeroOrMoreMatcher) {
      ZeroOrMoreMatcher zm = matcher.as();
      if (zm.repeated() instanceof FalseMatcher || zm.next() instanceof FalseMatcher) {
        return zm.next();
      }
    } else if (matcher instanceof ZeroOrOneMatcher) {
      ZeroOrOneMatcher zm = matcher.as();
      if (zm.repeated() instanceof FalseMatcher || zm.next() instanceof FalseMatcher) {
        return zm.next();
      }
    }
    return matcher;
  }

  /**
   * If a character class is empty, then it will not match anything and can be treated
   * as false.
   */
  static Matcher convertEmptyCharClassToFalse(Matcher matcher) {
    if (matcher instanceof CharClassMatcher) {
      return matcher.<CharClassMatcher>as().set().isEmpty()
          ? FalseMatcher.INSTANCE
          : matcher;
    }
    return matcher;
  }

  /**
   * If a character class has a single value, then just match that value ({@code "[a]" => "a"}).
   * This allows other optimizations to merge the value into adjacent matchers to get a larger
   * prefix or indexOf check.
   */
  static Matcher convertSingleCharClassToSeq(Matcher matcher) {
    if (matcher instanceof CharClassMatcher) {
      Optional<Character> opt = matcher.<CharClassMatcher>as().set().character();
      if (opt.isPresent()) {
        return new CharSeqMatcher(opt.get());
      }
    }
    return matcher;
  }

  /**
   * If a start anchor is followed by a repeated any match, then the start anchor can be removed
   * as it will not change the result ({@code "^.*" => ".*"}).
   */
  static Matcher removeStartFollowedByMatchAny(Matcher matcher) {
    if (matcher instanceof SeqMatcher) {
      List<Matcher> matchers = matcher.<SeqMatcher>as().matchers();
      if (matchers.size() == 2
          && matchers.get(0) instanceof StartMatcher
          && matchers.get(1) instanceof ZeroOrMoreMatcher) {
        ZeroOrMoreMatcher zm = matchers.get(1).as();
        if (zm.repeated() instanceof AnyMatcher) {
          return zm;
        }
      }
    }
    return matcher;
  }

  /**
   * If a start anchor is preceded by a repeated any match, then the any match can be removed
   * as it must be empty for the start anchor to match ({@code ".*^" => "^"}).
   */
  static Matcher removeMatchAnyFollowedByStart(Matcher matcher) {
    if (matcher instanceof ZeroOrMoreMatcher) {
      ZeroOrMoreMatcher zm = matcher.as();
      if (zm.repeated() instanceof AnyMatcher
          && zm.next() instanceof SeqMatcher
          && zm.next().<SeqMatcher>as().matchers().get(0).isStartAnchored()) {
        return zm.next();
      }
    }
    return matcher;
  }

  /**
   * Adjacent any matches can be consolidated, e.g., ({@code ".*(.*foo)" => ".*foo"}).
   */
  static Matcher removeMatchAnyFollowedByIndexOf(Matcher matcher) {
    if (matcher instanceof ZeroOrMoreMatcher) {
      ZeroOrMoreMatcher zm = matcher.as();
      if (zm.repeated() instanceof AnyMatcher
          && PatternUtils.getPrefix(zm.next()) instanceof IndexOfMatcher) {
        return zm.next();
      }
    }
    return matcher;
  }

  /**
   * Remove match any pattern at the end, e.g., ({@code "foo.*$" => "foo"}).
   */
  static Matcher removeTrailingMatchAny(Matcher matcher) {
    if (matcher instanceof ZeroOrMoreMatcher) {
      ZeroOrMoreMatcher zm = matcher.as();
      boolean atEnd = zm.next() instanceof TrueMatcher || zm.next() instanceof EndMatcher;
      if (atEnd && zm.repeated() instanceof AnyMatcher) {
        return TrueMatcher.INSTANCE;
      }
    }
    return matcher;
  }

  /**
   * Adjacent any matches can be consolidated, e.g., ({@code ".*.*" => ".*"}).
   */
  static Matcher removeSequentialMatchAny(Matcher matcher) {
    if (matcher instanceof ZeroOrMoreMatcher) {
      ZeroOrMoreMatcher zm1 = matcher.as();
      if (zm1.repeated() instanceof AnyMatcher && zm1.next() instanceof ZeroOrMoreMatcher) {
        ZeroOrMoreMatcher zm2 = zm1.next().as();
        if (zm2.repeated() instanceof AnyMatcher) {
          return zm2;
        }
      }
    }
    return matcher;
  }

  /**
   * Since we do not need to capture the contents, nested sequences can be simplified to
   * a just a simple sequence. For example, {@code "a(b.*c)d" => "ab.*cd"}.
   */
  static Matcher flattenNestedSequences(Matcher matcher) {
    if (matcher instanceof SeqMatcher) {
      List<Matcher> matchers = matcher.<SeqMatcher>as().matchers();
      List<Matcher> ms = new ArrayList<>();
      for (Matcher m : matchers) {
        if (m instanceof SeqMatcher) {
          ms.addAll(m.<SeqMatcher>as().matchers());
        } else {
          ms.add(m);
        }
      }
      return SeqMatcher.create(ms);
    }
    return matcher;
  }

  /**
   * Nested OR clauses can be simplified to a just a simple set of options. For example,
   * {@code "(a|b|(c|d|(e|f))|g)" => "a|b|c|d|e|f|g"}.
   */
  static Matcher flattenNestedOr(Matcher matcher) {
    if (matcher instanceof OrMatcher) {
      List<Matcher> matchers = matcher.<OrMatcher>as().matchers();
      List<Matcher> ms = new ArrayList<>();
      for (Matcher m : matchers) {
        if (m instanceof OrMatcher) {
          ms.addAll(m.<OrMatcher>as().matchers());
        } else {
          ms.add(m);
        }
      }
      return OrMatcher.create(ms);
    }
    return matcher;
  }

  /**
   * Remove duplicate branches in an OR clause. For example: {@code "a|b|a" => "a|b"}.
   */
  static Matcher dedupOr(Matcher matcher) {
    if (matcher instanceof OrMatcher) {
      List<Matcher> ms = new ArrayList<>(
          new LinkedHashSet<>(matcher.<OrMatcher>as().matchers()));
      return OrMatcher.create(ms);
    }
    return matcher;
  }

  /**
   * Remove branches that are false from the OR clause. For example: {@code "a|$b|c" => "a|c"}.
   */
  static Matcher removeFalseBranchesFromOr(Matcher matcher) {
    if (matcher instanceof OrMatcher) {
      List<Matcher> ms = matcher.<OrMatcher>as()
          .matchers()
          .stream()
          .filter(m -> !(m instanceof FalseMatcher))
          .collect(Collectors.toList());
      return OrMatcher.create(ms);
    }
    return matcher;
  }

  /**
   * Extract common prefix from OR clause. This is beneficial because it reduces the amount
   * that needs to be checked for each branch. For example: {@code "ab|ac" => "a(b|c)"}.
   */
  static Matcher extractPrefixFromOr(Matcher matcher) {
    if (matcher instanceof OrMatcher) {
      // Get the prefix for the first condition
      List<Matcher> matchers = matcher.<OrMatcher>as().matchers();
      if (matchers.isEmpty()) {
        return matcher;
      }
      Matcher prefix = PatternUtils.getPrefix(matchers.get(0));
      if (prefix.alwaysMatches()) {
        return matcher;
      }
      List<Matcher> ms = new ArrayList<>();
      ms.add(PatternUtils.getSuffix(matchers.get(0)));

      // Verify all OR conditions have the same prefix
      for (Matcher m : matchers.subList(1, matchers.size())) {
        Matcher p = PatternUtils.getPrefix(m);
        if (!prefix.equals(p)) {
          return matcher;
        }
        ms.add(PatternUtils.getSuffix(m));
      }

      return SeqMatcher.create(prefix, OrMatcher.create(ms));
    }
    return matcher;
  }

  /**
   * If the matcher preceding an OR clause is a repeated any match, move into each branch
   * of the OR clause. This allows for other optimizations such as conversion to an indexOf
   * to take effect for each branch.
   */
  static Matcher inlineMatchAnyPrecedingOr(Matcher matcher) {
    if (matcher instanceof ZeroOrMoreMatcher) {
      ZeroOrMoreMatcher zm = matcher.as();
      if (zm.repeated() instanceof AnyMatcher && zm.next() instanceof OrMatcher) {
        List<Matcher> matchers = zm.next().<OrMatcher>as().matchers();
        List<Matcher> ms = new ArrayList<>();
        for (Matcher m : matchers) {
          ms.add(new ZeroOrMoreMatcher(AnyMatcher.INSTANCE, m));
        }
        return OrMatcher.create(ms);
      }
    }
    return matcher;
  }

  /**
   * If the matcher has a start anchored character sequence, then replace it with a
   * StartsWithMatcher. In a tight loop this is much faster than a running with a sequence
   * of two matchers.
   */
  static Matcher startsWithCharSeq(Matcher matcher) {
    if (matcher instanceof SeqMatcher) {
      List<Matcher> matchers = matcher.<SeqMatcher>as().matchers();
      if (matchers.size() >= 2
          && matchers.get(0) instanceof StartMatcher
          && matchers.get(1) instanceof CharSeqMatcher) {
        List<Matcher> ms = new ArrayList<>();
        ms.add(new StartsWithMatcher(matchers.get(1).<CharSeqMatcher>as().pattern()));
        ms.addAll(matchers.subList(2, matchers.size()));
        return SeqMatcher.create(ms);
      }
    }
    return matcher;
  }

  /**
   * If a char sequence is adjacent to a starts with matcher, then append the sequence to
   * the prefix pattern of the starts with matcher.
   */
  static Matcher combineCharSeqAfterStartsWith(Matcher matcher) {
    if (matcher instanceof SeqMatcher) {
      List<Matcher> matchers = matcher.<SeqMatcher>as().matchers();
      if (matchers.size() >= 2
          && matchers.get(0) instanceof StartsWithMatcher
          && matchers.get(1) instanceof CharSeqMatcher) {
        List<Matcher> ms = new ArrayList<>();
        String prefix = matchers.get(0).<StartsWithMatcher>as().pattern()
            + matchers.get(1).<CharSeqMatcher>as().pattern();
        ms.add(new StartsWithMatcher(prefix));
        ms.addAll(matchers.subList(2, matchers.size()));
        return SeqMatcher.create(ms);
      } else {
        return matcher;
      }
    }
    return matcher;
  }

  /**
   * If a char sequence is adjacent to an index of matcher, then append the sequence to
   * the pattern of the index of matcher.
   */
  static Matcher combineCharSeqAfterIndexOf(Matcher matcher) {
    if (matcher instanceof IndexOfMatcher) {
      IndexOfMatcher m = matcher.as();
      Matcher next = PatternUtils.head(m.next());
      if (next instanceof CharSeqMatcher) {
        String pattern = m.pattern() + next.<CharSeqMatcher>as().pattern();
        return new IndexOfMatcher(pattern, PatternUtils.tail(m.next()));
      }
    }
    return matcher;
  }

  /**
   * If a char sequence is adjacent to another char sequence, then concatenate the sequences.
   */
  static Matcher combineAdjacentCharSeqs(Matcher matcher) {
    if (matcher instanceof SeqMatcher) {
      List<Matcher> matchers = matcher.<SeqMatcher>as().matchers();
      List<Matcher> ms = new ArrayList<>();
      CharSeqMatcher cs1 = null;
      for (Matcher m : matchers) {
        if (m instanceof CharSeqMatcher) {
          if (cs1 == null) {
            cs1 = m.as();
          } else {
            CharSeqMatcher cs2 = m.as();
            cs1 = new CharSeqMatcher(cs1.pattern() + cs2.pattern());
          }
        } else {
          if (cs1 != null) {
            ms.add(cs1);
            cs1 = null;
          }
          ms.add(m);
        }
      }
      if (cs1 != null) {
        ms.add(cs1);
      }
      return SeqMatcher.create(ms);
    }
    return matcher;
  }

  /**
   * If a char sequence is preceded by a repeated any match, then replace with an
   * IndexOfMatcher. The index of operation seems to be optimized by the JDK and is
   * much faster. Example: {@code ".*foo" => indexOf("foo")}.
   */
  static Matcher convertRepeatedAnyCharSeqToIndexOf(Matcher matcher) {
    if (matcher instanceof ZeroOrMoreMatcher) {
      ZeroOrMoreMatcher zm1 = matcher.as();
      Matcher prefix = PatternUtils.getPrefix(zm1.next());
      if (zm1.repeated() instanceof AnyMatcher && prefix instanceof CharSeqMatcher) {
        String pattern = prefix.<CharSeqMatcher>as().pattern();
        Matcher suffix = PatternUtils.getSuffix(zm1.next());
        return new IndexOfMatcher(pattern, suffix);
      }
    }
    return matcher;
  }

  /**
   * Zero or more start anchors is the same as not being anchored by the start.
   */
  static Matcher removeRepeatedStart(Matcher matcher) {
    if (matcher instanceof ZeroOrMoreMatcher) {
      ZeroOrMoreMatcher zm = matcher.as();
      if (zm.repeated() instanceof StartMatcher) {
        return zm.next();
      }
    }
    return matcher;
  }

  /**
   * Multiple adjacent start anchors can be reduced to a single start anchor.
   */
  static Matcher combineAdjacentStart(Matcher matcher) {
    if (matcher instanceof SeqMatcher) {
      List<Matcher> matchers = matcher.<SeqMatcher>as().matchers();
      if (!matchers.isEmpty() && matchers.get(0) instanceof StartMatcher) {
        List<Matcher> ms = new ArrayList<>();
        ms.add(StartMatcher.INSTANCE);
        int pos = 0;
        for (Matcher m : matchers) {
          if (m instanceof StartMatcher) {
            ++pos;
          } else {
            break;
          }
        }
        ms.addAll(matchers.subList(pos, matchers.size()));
        return SeqMatcher.create(ms);
      }
    }
    return matcher;
  }
}
