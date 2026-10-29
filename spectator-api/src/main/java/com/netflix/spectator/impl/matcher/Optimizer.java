/*
 * Copyright 2014-2026 Netflix, Inc.
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
import java.util.Arrays;
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

  /**
   * Return a new instance of the matcher that has been optimized. The matcher must be a
   * complete pattern that is checked from the start of the string, some of the rewrites are
   * only valid at the start. It should not be used to optimize a sub-matcher.
   */
  static Matcher optimize(Matcher matcher) {
    Matcher m = matcher;
    Matcher opt = optimizeSinglePass(m);
    for (int i = 0; !m.equals(opt) && i < MAX_ITERATIONS; ++i) {
      m = opt;
      opt = optimizeSinglePass(m);
    }
    // Flatten contains/anchored IndexOf chains and start-anchored literal alternations into single
    // matchers. Done as one terminal pass after the fixpoint so the new matcher types do not need
    // to participate in the iterative rewrite rules above. A single pass (rather than chained
    // rewrites) is required: the folded matchers delegate rewrite() to their original nested form,
    // so a second rewrite pass traversing into them would undo the fold.
    return opt.rewrite(Optimizer::fold);
  }

  /** Apply the available flattening folds; the recognized shapes are disjoint. */
  private static Matcher fold(Matcher matcher) {
    Matcher folded = foldIndexOfSeq(matcher);
    return (folded != matcher) ? folded : foldOrStartsWith(matcher);
  }

  /**
   * Fold a start-anchored alternation of literal prefixes ({@code ^(abc|def|ghi)}) into a single
   * {@link OrStartsWithMatcher} that checks each prefix in one monomorphic loop instead of
   * dispatching an interface call per branch. The original matcher is retained as the delegate.
   */
  static Matcher foldOrStartsWith(Matcher matcher) {
    if (matcher instanceof SeqMatcher) {
      List<Matcher> ms = matcher.<SeqMatcher>as().matchers();
      if (ms.size() == 2 && ms.get(0) instanceof StartMatcher && ms.get(1) instanceof OrMatcher) {
        List<Matcher> branches = ms.get(1).<OrMatcher>as().matchers();
        if (branches.size() < 2) {
          return matcher;
        }
        String[] prefixes = new String[branches.size()];
        boolean ignoreCase = false;
        for (int i = 0; i < branches.size(); ++i) {
          if (!(branches.get(i) instanceof CharSeqMatcher)) {
            return matcher;
          }
          CharSeqMatcher cs = branches.get(i).as();
          if (i == 0) {
            ignoreCase = cs.isIgnoreCase();
          } else if (cs.isIgnoreCase() != ignoreCase) {
            return matcher;
          }
          prefixes[i] = cs.pattern();
        }
        return new OrStartsWithMatcher(prefixes, ignoreCase, matcher);
      }
    }
    return matcher;
  }

  /**
   * Collect the segments of a contains chain into {@code segs}. The chain is a sequence of
   * {@link IndexOfMatcher} nodes, optionally terminated by an already-folded contains
   * {@link IndexOfSeqMatcher} (so the fold composes under the bottom-up rewrite). Returns 0 for a
   * plain contains terminal ({@code .*}), 1 for an end-anchored terminal ({@code $}), or -1 if the
   * tail is not a foldable contains chain or the {@code ignoreCase} flag is inconsistent.
   */
  private static int collectContainsChain(Matcher matcher, boolean ignoreCase, List<String> segs) {
    Matcher cur = matcher;
    while (cur instanceof IndexOfMatcher) {
      IndexOfMatcher io = cur.as();
      if (io.isIgnoreCase() != ignoreCase) {
        return -1;
      }
      segs.add(io.pattern());
      cur = io.next();
    }
    if (cur == TrueMatcher.INSTANCE) {
      return 0;
    } else if (cur == EndMatcher.INSTANCE) {
      return 1;
    } else if (cur instanceof IndexOfSeqMatcher) {
      IndexOfSeqMatcher seq = cur.as();
      if (seq.startAnchored() || seq.isIgnoreCase() != ignoreCase) {
        return -1;
      }
      segs.addAll(Arrays.asList(seq.segments()));
      return seq.endAnchored() ? 1 : 0;
    }
    return -1;
  }

  /**
   * Fold a contains chain ({@code .*A.*B.*}), an end-anchored chain ({@code .*A.*B$}), or a
   * start-anchored chain ({@code ^A.*B.*C$}) into a single {@link IndexOfSeqMatcher} that matches
   * greedily without backtracking. The original matcher is retained as the delegate so all
   * non-matching operations behave identically.
   */
  static Matcher foldIndexOfSeq(Matcher matcher) {
    if (matcher instanceof IndexOfMatcher) {
      IndexOfMatcher head = matcher.as();
      boolean ignoreCase = head.isIgnoreCase();
      List<String> segs = new ArrayList<>();
      int anchor = collectContainsChain(head, ignoreCase, segs);
      if (anchor >= 0 && segs.size() >= 2) {
        return new IndexOfSeqMatcher(
            segs.toArray(new String[0]), false, anchor == 1, ignoreCase, matcher);
      }
    } else if (matcher instanceof SeqMatcher) {
      List<Matcher> ms = matcher.<SeqMatcher>as().matchers();
      if (ms.size() == 2 && ms.get(0) instanceof StartsWithMatcher) {
        StartsWithMatcher prefix = ms.get(0).as();
        boolean ignoreCase = prefix.isIgnoreCase();
        List<String> segs = new ArrayList<>();
        segs.add(prefix.pattern());
        int anchor = collectContainsChain(ms.get(1), ignoreCase, segs);
        if (anchor >= 0 && segs.size() >= 2) {
          return new IndexOfSeqMatcher(
              segs.toArray(new String[0]), true, anchor == 1, ignoreCase, matcher);
        }
      }
    }
    return matcher;
  }

  private static Matcher optimizeSinglePass(Matcher matcher) {
    Matcher m = matcher
        .rewrite(Optimizer::mergeNext)
        .rewrite(Optimizer::removeTrueInSequence)
        .rewrite(Optimizer::sequenceWithFalseIsFalse)
        .rewrite(Optimizer::sequenceWithStuffAfterEndIsFalse)
        .rewrite(Optimizer::startAfterContentIsFalse)
        .rewrite(Optimizer::zeroOrMoreFalse)
        .rewrite(Optimizer::convertEmptyCharClassToFalse)
        .rewrite(Optimizer::convertSingleCharClassToSeq);
    // Only valid at the start of the pattern so it is not applied recursively
    m = removeStartFollowedByMatchAny(m);
    return m
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
        .rewrite(Optimizer::flattenRepeatedUnbounded)
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
   * and can be treated as false. Matchers that can match an empty string, such as
   * {@code a*} or {@code ^}, may still match after the end anchor and are left as is.
   */
  static Matcher sequenceWithStuffAfterEndIsFalse(Matcher matcher) {
    if (matcher instanceof SeqMatcher) {
      boolean end = false;
      for (Matcher m : matcher.<SeqMatcher>as().matchers()) {
        if (m instanceof EndMatcher) {
          end = true;
        } else if (end && (m.minLength() > 0 || lookaheadRequiresContent(m))) {
          return FalseMatcher.INSTANCE;
        }
      }
    }
    return matcher;
  }

  private static boolean lookaheadRequiresContent(Matcher matcher) {
    return matcher instanceof PositiveLookaheadMatcher
        && matcher.<PositiveLookaheadMatcher>as().matcher().minLength() > 0;
  }

  /**
   * If a start anchor follows content that consumes at least one character, then it will
   * never be able to match and can be treated as false. For example: {@code "a^b" => "$."}
   * or {@code "a$^" => "$."}.
   */
  static Matcher startAfterContentIsFalse(Matcher matcher) {
    if (matcher instanceof SeqMatcher) {
      long consumed = 0L;
      for (Matcher m : matcher.<SeqMatcher>as().matchers()) {
        if (consumed > 0L && requiresStart(m)) {
          return FalseMatcher.INSTANCE;
        }
        consumed += m.minLength();
      }
    } else if (matcher instanceof IndexOfMatcher) {
      IndexOfMatcher m = matcher.as();
      if (!m.pattern().isEmpty() && requiresStart(m.next())) {
        return FalseMatcher.INSTANCE;
      }
    }
    return matcher;
  }

  /**
   * Returns true if the matcher can only match at the start of the string. For a sequence,
   * that is the case if any of the elements can only match at the start.
   */
  private static boolean requiresStart(Matcher matcher) {
    if (matcher.isStartAnchored()) {
      return true;
    } else if (matcher instanceof SeqMatcher) {
      for (Matcher m : matcher.<SeqMatcher>as().matchers()) {
        if (requiresStart(m)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * If the match after a repeated pattern or index of is false, then treat the whole match as
   * false. For example: {@code ".*$." => "$."} or {@code ".*a$." => "$."}.
   */
  static Matcher zeroOrMoreFalse(Matcher matcher) {
    if (matcher instanceof IndexOfMatcher) {
      if (matcher.<IndexOfMatcher>as().next() instanceof FalseMatcher) {
        return FalseMatcher.INSTANCE;
      }
    } else if (matcher instanceof ZeroOrMoreMatcher) {
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
   * as it will not change the result ({@code "^.*" => ".*"}). This is only true at the start
   * of the pattern, for example {@code "b(^.*a)"} can never match, but {@code "b.*a"} can. So
   * it should be applied to the overall pattern rather than via rewrite. The rule is also
   * applied to nested matchers that are known to be checked at the start, such as alternatives
   * of an OR or the optional portion of {@code "^(^.*)?a"}.
   */
  static Matcher removeStartFollowedByMatchAny(Matcher matcher) {
    return removeStartFollowedByMatchAny(matcher, true);
  }

  /**
   * Remove start anchors that are followed by a repeated any match for the given position.
   *
   * @param matcher
   *     Matcher to rewrite.
   * @param atStart
   *     If true, then the matcher is checked at the start of the string. Otherwise, it is only
   *     known to be preceded by a repeated any match from the start of the string. For that case,
   *     {@code ".*(^.*a)" == ".*(.*a)"}, but a start anchor by itself cannot be removed.
   */
  private static Matcher removeStartFollowedByMatchAny(Matcher matcher, boolean atStart) {
    if (matcher instanceof OrMatcher) {
      List<Matcher> ms = new ArrayList<>();
      for (Matcher m : matcher.<OrMatcher>as().matchers()) {
        ms.add(removeStartFollowedByMatchAny(m, atStart));
      }
      return OrMatcher.create(ms);
    } else if (matcher instanceof StartMatcher) {
      // Start anchor by itself at the start of the pattern will always match
      return atStart ? TrueMatcher.INSTANCE : matcher;
    } else if (matcher instanceof ZeroOrOneMatcher) {
      // Optional portion is checked at the same position
      ZeroOrOneMatcher zm = matcher.as();
      Matcher repeated = removeStartFollowedByMatchAny(zm.repeated(), atStart);
      return new ZeroOrOneMatcher(repeated, zm.next());
    } else if (matcher instanceof ZeroOrMoreMatcher) {
      ZeroOrMoreMatcher zm = matcher.as();
      if (zm.repeated() instanceof AnyMatcher) {
        Matcher next = removeStartFollowedByMatchAny(zm.next(), false);
        return new ZeroOrMoreMatcher(zm.repeated(), next);
      }
      // Only the first repetition is at the start. For a repeated "^.*x", anything matched
      // by later repetitions could also be matched by the first, so the start anchor can
      // still be removed.
      Matcher repeated = removeLeadingStart(zm.repeated());
      return (repeated == null) ? matcher : new ZeroOrMoreMatcher(repeated, zm.next());
    } else if (matcher instanceof SeqMatcher) {
      Matcher stripped = removeLeadingStart(matcher);
      if (stripped != null) {
        return stripped;
      }
      List<Matcher> matchers = matcher.<SeqMatcher>as().matchers();
      List<Matcher> ms = new ArrayList<>(matchers);
      if (matchers.get(0) instanceof StartMatcher) {
        // Start anchor matches an empty string at the start, so the next matcher is also
        // checked at the start. The start anchor is kept so the sequence is still anchored.
        ms.set(1, removeStartFollowedByMatchAny(matchers.get(1), true));
      } else {
        ms.set(0, removeStartFollowedByMatchAny(matchers.get(0), atStart));
      }
      return SeqMatcher.create(ms);
    }
    return matcher;
  }

  /**
   * If the matcher is a start anchor followed by a repeated any match, then return the
   * matcher without the start anchor. Otherwise return null.
   */
  private static Matcher removeLeadingStart(Matcher matcher) {
    if (matcher instanceof SeqMatcher) {
      List<Matcher> matchers = matcher.<SeqMatcher>as().matchers();
      if (matchers.size() == 2
          && matchers.get(0) instanceof StartMatcher
          && startsWithMatchAny(matchers.get(1))) {
        return matchers.get(1);
      }
    }
    return null;
  }

  /** Returns true if the matcher starts with a repeated any match. */
  private static boolean startsWithMatchAny(Matcher matcher) {
    if (matcher instanceof ZeroOrMoreMatcher) {
      return matcher.<ZeroOrMoreMatcher>as().repeated() instanceof AnyMatcher;
    } else if (matcher instanceof IndexOfMatcher) {
      // Index of is equivalent to a repeated any match followed by a string
      return true;
    } else if (matcher instanceof OrMatcher) {
      for (Matcher m : matcher.<OrMatcher>as().matchers()) {
        if (!startsWithMatchAny(m)) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  /**
   * If a start anchor is preceded by a repeated any match, then the any match can be removed
   * as it must be empty for the start anchor to match ({@code ".*^" => "^"}).
   */
  static Matcher removeMatchAnyFollowedByStart(Matcher matcher) {
    if (matcher instanceof ZeroOrMoreMatcher) {
      ZeroOrMoreMatcher zm = matcher.as();
      if (zm.repeated() instanceof AnyMatcher && zm.next().isStartAnchored()) {
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
   * Repetition of an unbounded repetition can be simplified to a single unbounded
   * repetition. Since only the yes or no answer for a match is needed, this does not change
   * the result, but allows the trailing zero or more to backtrack. Examples:
   *
   * <pre>
   * (a{2,}){2}  => a{4,}
   * (a+){2,3}   => a{2,}
   * (a+)?b      => a*b
   * (a*)*b      => a*b
   * </pre>
   */
  static Matcher flattenRepeatedUnbounded(Matcher matcher) {
    if (matcher instanceof RepeatMatcher) {
      RepeatMatcher m = matcher.as();
      Matcher x = unboundedRepeated(m.repeated());
      if (x != null && m.min() > 0) {
        long min = (long) unboundedMin(m.repeated()) * m.min();
        return unbounded(x, (int) Math.min(min, Integer.MAX_VALUE), TrueMatcher.INSTANCE);
      }
    } else if (matcher instanceof ZeroOrOneMatcher) {
      ZeroOrOneMatcher m = matcher.as();
      Matcher x = unboundedRepeated(m.repeated());
      if (x != null && unboundedMin(m.repeated()) <= 1) {
        return new ZeroOrMoreMatcher(x, m.next());
      }
    } else if (matcher instanceof ZeroOrMoreMatcher) {
      ZeroOrMoreMatcher m = matcher.as();
      Matcher x = unboundedRepeated(m.repeated());
      if (x != null && unboundedMin(m.repeated()) <= 1) {
        return new ZeroOrMoreMatcher(x, m.next());
      }
    }
    return matcher;
  }

  /**
   * If the matcher is an unbounded repetition, {@code x{n,}}, with nothing following it,
   * then return the repeated matcher {@code x}. Otherwise return null.
   */
  private static Matcher unboundedRepeated(Matcher matcher) {
    if (matcher instanceof ZeroOrMoreMatcher) {
      ZeroOrMoreMatcher zm = matcher.as();
      return zm.next() instanceof TrueMatcher ? zm.repeated() : null;
    } else if (matcher instanceof SeqMatcher) {
      List<Matcher> ms = matcher.<SeqMatcher>as().matchers();
      if (ms.size() == 2) {
        Matcher x = unboundedRepeated(ms.get(1));
        if (x != null && ms.get(1) instanceof ZeroOrMoreMatcher && prefixCount(ms.get(0), x) > 0) {
          return x;
        }
      }
    }
    return null;
  }

  /** Returns the minimum number of repetitions for an unbounded repetition. */
  private static int unboundedMin(Matcher matcher) {
    if (matcher instanceof SeqMatcher) {
      List<Matcher> ms = matcher.<SeqMatcher>as().matchers();
      return prefixCount(ms.get(0), ms.get(1).<ZeroOrMoreMatcher>as().repeated());
    }
    return 0;
  }

  /**
   * Returns the number of times {@code x} is repeated by the prefix matcher, or -1 if it is
   * not a fixed repetition of {@code x}.
   */
  private static int prefixCount(Matcher prefix, Matcher x) {
    if (prefix.equals(x)) {
      return 1;
    } else if (prefix instanceof RepeatMatcher) {
      RepeatMatcher r = prefix.as();
      return r.min() == r.max() && r.repeated().equals(x) ? r.min() : -1;
    }
    return -1;
  }

  /** Create a matcher for an unbounded repetition, {@code x{min,}}. */
  private static Matcher unbounded(Matcher x, int min, Matcher next) {
    Matcher zeroOrMore = new ZeroOrMoreMatcher(x, next);
    if (min == 0) {
      return zeroOrMore;
    }
    Matcher prefix = (min == 1) ? x : new RepeatMatcher(x, min, min);
    return SeqMatcher.create(prefix, zeroOrMore);
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
