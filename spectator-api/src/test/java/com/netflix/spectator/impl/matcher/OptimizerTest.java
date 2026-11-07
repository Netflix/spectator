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

import com.netflix.spectator.impl.AsciiSet;
import com.netflix.spectator.impl.PatternMatcher;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class OptimizerTest {

  @Test
  public void removeTrueInSequence() {
    Matcher input = SeqMatcher.create(
        AnyMatcher.INSTANCE,
        TrueMatcher.INSTANCE,
        AnyMatcher.INSTANCE
    );
    Matcher expected = SeqMatcher.create(
        AnyMatcher.INSTANCE,
        AnyMatcher.INSTANCE
    );
    Assertions.assertEquals(expected, Optimizer.removeTrueInSequence(input));
  }

  @Test
  public void sequenceWithFalseIsFalse() {
    Matcher input = SeqMatcher.create(
        AnyMatcher.INSTANCE,
        FalseMatcher.INSTANCE,
        AnyMatcher.INSTANCE
    );
    Matcher expected = FalseMatcher.INSTANCE;
    Assertions.assertEquals(expected, Optimizer.sequenceWithFalseIsFalse(input));
  }

  @Test
  public void sequenceWithStuffAfterEndIsFalse() {
    Matcher input = SeqMatcher.create(
        AnyMatcher.INSTANCE,
        EndMatcher.INSTANCE,
        AnyMatcher.INSTANCE
    );
    Matcher expected = FalseMatcher.INSTANCE;
    Assertions.assertEquals(expected, Optimizer.sequenceWithStuffAfterEndIsFalse(input));
  }

  @Test
  public void sequenceWithEmptyMatchAfterEnd() {
    Matcher input = SeqMatcher.create(
        AnyMatcher.INSTANCE,
        EndMatcher.INSTANCE,
        new ZeroOrMoreMatcher(new CharSeqMatcher("a"), TrueMatcher.INSTANCE)
    );
    Assertions.assertEquals(input, Optimizer.sequenceWithStuffAfterEndIsFalse(input));
  }

  @Test
  public void sequenceWithStartAfterEnd() {
    Matcher input = SeqMatcher.create(
        EndMatcher.INSTANCE,
        StartMatcher.INSTANCE
    );
    Assertions.assertEquals(input, Optimizer.sequenceWithStuffAfterEndIsFalse(input));
  }

  @Test
  public void sequenceWithLookaheadAfterEnd() {
    Matcher input = SeqMatcher.create(
        EndMatcher.INSTANCE,
        new PositiveLookaheadMatcher(new CharSeqMatcher("a"))
    );
    Matcher expected = FalseMatcher.INSTANCE;
    Assertions.assertEquals(expected, Optimizer.sequenceWithStuffAfterEndIsFalse(input));
  }

  @Test
  public void startAfterContentIsFalse() {
    Matcher input = SeqMatcher.create(
        new CharSeqMatcher("a"),
        EndMatcher.INSTANCE,
        StartMatcher.INSTANCE
    );
    Matcher expected = FalseMatcher.INSTANCE;
    Assertions.assertEquals(expected, Optimizer.startAfterContentIsFalse(input));
  }

  @Test
  public void startAfterContentIsFalse_IndexOf() {
    Matcher input = new IndexOfMatcher("a", new StartsWithMatcher("b"));
    Matcher expected = FalseMatcher.INSTANCE;
    Assertions.assertEquals(expected, Optimizer.startAfterContentIsFalse(input));
  }

  @Test
  public void startAfterEmptyContent() {
    Matcher input = SeqMatcher.create(
        new ZeroOrMoreMatcher(new CharSeqMatcher("a"), TrueMatcher.INSTANCE),
        EndMatcher.INSTANCE,
        StartMatcher.INSTANCE
    );
    Assertions.assertEquals(input, Optimizer.startAfterContentIsFalse(input));
  }

  @Test
  public void zeroOrMoreFalse_Repeated() {
    Matcher input = new ZeroOrMoreMatcher(FalseMatcher.INSTANCE, AnyMatcher.INSTANCE);
    Matcher expected = AnyMatcher.INSTANCE;
    Assertions.assertEquals(expected, Optimizer.zeroOrMoreFalse(input));
  }

  @Test
  public void zeroOrMoreFalse_Next() {
    Matcher input = new ZeroOrMoreMatcher(AnyMatcher.INSTANCE, FalseMatcher.INSTANCE);
    Matcher expected = FalseMatcher.INSTANCE;
    Assertions.assertEquals(expected, Optimizer.zeroOrMoreFalse(input));
  }

  @Test
  public void zeroOrOneFalse_Repeated() {
    Matcher input = new ZeroOrOneMatcher(FalseMatcher.INSTANCE, AnyMatcher.INSTANCE);
    Matcher expected = AnyMatcher.INSTANCE;
    Assertions.assertEquals(expected, Optimizer.zeroOrMoreFalse(input));
  }

  @Test
  public void zeroOrOneFalse_Next() {
    Matcher input = new ZeroOrOneMatcher(AnyMatcher.INSTANCE, FalseMatcher.INSTANCE);
    Matcher expected = FalseMatcher.INSTANCE;
    Assertions.assertEquals(expected, Optimizer.zeroOrMoreFalse(input));
  }

  @Test
  public void repeatFalse_Repeated() {
    Matcher input = new RepeatMatcher(FalseMatcher.INSTANCE, 0, 2, AnyMatcher.INSTANCE, false);
    Matcher expected = AnyMatcher.INSTANCE;
    Assertions.assertEquals(expected, Optimizer.zeroOrMoreFalse(input));
  }

  @Test
  public void repeatFalse_RepeatedMin() {
    Matcher input = new RepeatMatcher(FalseMatcher.INSTANCE, 1, 2, AnyMatcher.INSTANCE, false);
    Matcher expected = FalseMatcher.INSTANCE;
    Assertions.assertEquals(expected, Optimizer.zeroOrMoreFalse(input));
  }

  @Test
  public void repeatFalse_Next() {
    Matcher input = new RepeatMatcher(AnyMatcher.INSTANCE, 0, 2, FalseMatcher.INSTANCE, false);
    Matcher expected = FalseMatcher.INSTANCE;
    Assertions.assertEquals(expected, Optimizer.zeroOrMoreFalse(input));
  }

  @Test
  public void repeatFalse_MaxZero() {
    Matcher input = new RepeatMatcher(new CharSeqMatcher("a"), 0, 0, AnyMatcher.INSTANCE, false);
    Matcher expected = AnyMatcher.INSTANCE;
    Assertions.assertEquals(expected, Optimizer.zeroOrMoreFalse(input));
  }

  @Test
  public void repeatFalse_Possessive() {
    Matcher input = new RepeatMatcher(FalseMatcher.INSTANCE, 1, 2, AnyMatcher.INSTANCE, true);
    Matcher expected = FalseMatcher.INSTANCE;
    Assertions.assertEquals(expected, Optimizer.zeroOrMoreFalse(input));
  }

  @Test
  public void repeatEmptyLastAlternative() {
    Matcher a = new CharSeqMatcher("a");
    Matcher input = new RepeatMatcher(
        OrMatcher.create(a, TrueMatcher.INSTANCE), 2, 3, AnyMatcher.INSTANCE, false);
    Matcher expected = new RepeatMatcher(a, 0, 3, AnyMatcher.INSTANCE, false);
    Assertions.assertEquals(expected, Optimizer.repeatEmptyLastAlternative(input));
  }

  @Test
  public void repeatEmptyLastAlternative_EmptyFirst() {
    Matcher input = new RepeatMatcher(
        OrMatcher.create(TrueMatcher.INSTANCE, new CharSeqMatcher("a")), 2, 3);
    Assertions.assertEquals(input, Optimizer.repeatEmptyLastAlternative(input));
  }

  @Test
  public void repeatEmptyLastAlternative_Possessive() {
    Matcher input = new RepeatMatcher(
        OrMatcher.create(new CharSeqMatcher("a"), TrueMatcher.INSTANCE), 2, 3,
        TrueMatcher.INSTANCE, true);
    Assertions.assertEquals(input, Optimizer.repeatEmptyLastAlternative(input));
  }

  @Test
  public void indexOfFalse_Next() {
    Matcher input = new IndexOfMatcher("a", FalseMatcher.INSTANCE);
    Matcher expected = FalseMatcher.INSTANCE;
    Assertions.assertEquals(expected, Optimizer.zeroOrMoreFalse(input));
  }

  @Test
  public void convertEmptyCharClassToFalse() {
    Matcher input = new CharClassMatcher(AsciiSet.none());
    Matcher expected = FalseMatcher.INSTANCE;
    Assertions.assertEquals(expected, Optimizer.convertEmptyCharClassToFalse(input));
  }

  @Test
  public void convertEmptyCharClassToFalseFoldedNotEmpty() {
    // For [a&&A], nothing matches unless the case is ignored
    Matcher input = new CharClassMatcher(AsciiSet.none(), AsciiSet.fromPattern("aA"), false);
    Assertions.assertEquals(input, Optimizer.convertEmptyCharClassToFalse(input));
  }

  @Test
  public void convertSingleCharClassToSeq() {
    Matcher input = new CharClassMatcher(AsciiSet.fromPattern("a"));
    Matcher expected = new CharSeqMatcher('a');
    Assertions.assertEquals(expected, Optimizer.convertSingleCharClassToSeq(input));
  }

  @Test
  public void convertMatchAllCharClassToAny() {
    AsciiSet all = AsciiSet.all();
    Matcher input = new CharClassMatcher(all, all, false, CodePointRanges.ALL);
    Assertions.assertEquals(AnyMatcher.INSTANCE, Optimizer.convertMatchAllCharClassToAny(input));
  }

  @Test
  public void convertMatchAllCharClassToAnyAsciiOnly() {
    // Does not match non-ASCII code points, so it is not the same as '.'
    AsciiSet all = AsciiSet.all();
    Matcher input = new CharClassMatcher(all, all, false, CodePointRanges.NONE);
    Assertions.assertEquals(input, Optimizer.convertMatchAllCharClassToAny(input));
  }

  @Test
  public void matchAllCharClassUsesIndexOf() {
    Assertions.assertEquals(
        new IndexOfMatcher("foo", TrueMatcher.INSTANCE),
        PatternMatcher.compile("[\\s\\S]*foo"));
  }

  @Test
  public void removeStartFollowedByMatchAny() {
    Matcher input = SeqMatcher.create(
        StartMatcher.INSTANCE,
        new ZeroOrMoreMatcher(AnyMatcher.INSTANCE, AnyMatcher.INSTANCE)
    );
    Matcher expected = new ZeroOrMoreMatcher(AnyMatcher.INSTANCE, AnyMatcher.INSTANCE);
    Assertions.assertEquals(expected, Optimizer.removeStartFollowedByMatchAny(input));
  }

  @Test
  public void removeStartFollowedByMatchAny_IndexOf() {
    Matcher input = SeqMatcher.create(
        StartMatcher.INSTANCE,
        new IndexOfMatcher("foo", TrueMatcher.INSTANCE)
    );
    Matcher expected = new IndexOfMatcher("foo", TrueMatcher.INSTANCE);
    Assertions.assertEquals(expected, Optimizer.removeStartFollowedByMatchAny(input));
  }

  @Test
  public void removeStartFollowedByMatchAny_Or() {
    Matcher input = OrMatcher.create(
        SeqMatcher.create(
            StartMatcher.INSTANCE,
            new IndexOfMatcher("foo", TrueMatcher.INSTANCE)),
        new CharSeqMatcher("bar")
    );
    Matcher expected = OrMatcher.create(
        new IndexOfMatcher("foo", TrueMatcher.INSTANCE),
        new CharSeqMatcher("bar")
    );
    Assertions.assertEquals(expected, Optimizer.removeStartFollowedByMatchAny(input));
  }

  @Test
  public void removeStartFollowedByMatchAny_StartOnly() {
    Matcher input = StartMatcher.INSTANCE;
    Matcher expected = TrueMatcher.INSTANCE;
    Assertions.assertEquals(expected, Optimizer.removeStartFollowedByMatchAny(input));
  }

  @Test
  public void removeStartFollowedByMatchAny_NotAtStart() {
    // b(^.*a) can never match, the start anchor must be preserved
    Matcher nested = SeqMatcher.create(
        StartMatcher.INSTANCE,
        new IndexOfMatcher("a", TrueMatcher.INSTANCE)
    );
    Matcher input = SeqMatcher.create(new CharSeqMatcher("b"), nested);
    Assertions.assertEquals(input, Optimizer.removeStartFollowedByMatchAny(input));
  }

  @Test
  public void removeStartFollowedByMatchAny_ZeroOrOneAfterStart() {
    // ^(^.*)?a, the optional portion is checked at the start
    Matcher matchAny = new ZeroOrMoreMatcher(AnyMatcher.INSTANCE, TrueMatcher.INSTANCE);
    Matcher input = SeqMatcher.create(
        StartMatcher.INSTANCE,
        new ZeroOrOneMatcher(
            SeqMatcher.create(StartMatcher.INSTANCE, matchAny),
            new CharSeqMatcher("a"))
    );
    Matcher expected = SeqMatcher.create(
        StartMatcher.INSTANCE,
        new ZeroOrOneMatcher(matchAny, new CharSeqMatcher("a"))
    );
    Assertions.assertEquals(expected, Optimizer.removeStartFollowedByMatchAny(input));
  }

  @Test
  public void removeStartFollowedByMatchAny_ZeroOrOneNotAtStart() {
    // b(^.*)?a, the optional portion can never match
    Matcher matchAny = new ZeroOrMoreMatcher(AnyMatcher.INSTANCE, TrueMatcher.INSTANCE);
    Matcher input = SeqMatcher.create(
        new CharSeqMatcher("b"),
        new ZeroOrOneMatcher(
            SeqMatcher.create(StartMatcher.INSTANCE, matchAny),
            new CharSeqMatcher("a"))
    );
    Assertions.assertEquals(input, Optimizer.removeStartFollowedByMatchAny(input));
  }

  @Test
  public void removeStartFollowedByMatchAny_ZeroOrMoreAfterStart() {
    // ^(^.*a)*b
    Matcher input = SeqMatcher.create(
        StartMatcher.INSTANCE,
        new ZeroOrMoreMatcher(
            SeqMatcher.create(
                StartMatcher.INSTANCE,
                new IndexOfMatcher("a", TrueMatcher.INSTANCE)),
            new CharSeqMatcher("b"))
    );
    Matcher expected = SeqMatcher.create(
        StartMatcher.INSTANCE,
        new ZeroOrMoreMatcher(
            new IndexOfMatcher("a", TrueMatcher.INSTANCE),
            new CharSeqMatcher("b"))
    );
    Assertions.assertEquals(expected, Optimizer.removeStartFollowedByMatchAny(input));
  }

  @Test
  public void removeStartFollowedByMatchAny_AfterMatchAny() {
    // .*(^.*a|^b), a start anchor followed by match any is redundant, but a start anchor
    // that is not followed by match any must be preserved
    Matcher input = new ZeroOrMoreMatcher(
        AnyMatcher.INSTANCE,
        OrMatcher.create(
            SeqMatcher.create(
                StartMatcher.INSTANCE,
                new IndexOfMatcher("a", TrueMatcher.INSTANCE)),
            SeqMatcher.create(StartMatcher.INSTANCE, new CharSeqMatcher("b")),
            StartMatcher.INSTANCE)
    );
    Matcher expected = new ZeroOrMoreMatcher(
        AnyMatcher.INSTANCE,
        OrMatcher.create(
            new IndexOfMatcher("a", TrueMatcher.INSTANCE),
            SeqMatcher.create(StartMatcher.INSTANCE, new CharSeqMatcher("b")),
            StartMatcher.INSTANCE)
    );
    Assertions.assertEquals(expected, Optimizer.removeStartFollowedByMatchAny(input));
  }

  @Test
  public void removeMatchAnyFollowedByStart_StartsWith() {
    Matcher input = new ZeroOrMoreMatcher(AnyMatcher.INSTANCE, new StartsWithMatcher("a"));
    Matcher expected = new StartsWithMatcher("a");
    Assertions.assertEquals(expected, Optimizer.removeMatchAnyFollowedByStart(input));
  }

  @Test
  public void removeMatchAnyFollowedByStart_StartOnly() {
    Matcher input = new ZeroOrMoreMatcher(AnyMatcher.INSTANCE, StartMatcher.INSTANCE);
    Matcher expected = StartMatcher.INSTANCE;
    Assertions.assertEquals(expected, Optimizer.removeMatchAnyFollowedByStart(input));
  }

  @Test
  public void removeMatchAnyFollowedByStart_Or() {
    Matcher anchored = OrMatcher.create(
        new StartsWithMatcher("a"),
        new StartsWithMatcher("b"));
    Matcher input = new ZeroOrMoreMatcher(AnyMatcher.INSTANCE, anchored);
    Assertions.assertEquals(anchored, Optimizer.removeMatchAnyFollowedByStart(input));
  }

  @Test
  public void removeMatchAnyFollowedByStart_OrPartial() {
    // Not all alternatives are anchored, so the match any cannot be removed
    Matcher input = new ZeroOrMoreMatcher(
        AnyMatcher.INSTANCE,
        OrMatcher.create(new StartsWithMatcher("a"), new CharSeqMatcher("b")));
    Assertions.assertEquals(input, Optimizer.removeMatchAnyFollowedByStart(input));
  }

  @Test
  public void removeMatchAnyFollowedByStart() {
    Matcher input = new ZeroOrMoreMatcher(AnyMatcher.INSTANCE, SeqMatcher.create(
        StartMatcher.INSTANCE,
        AnyMatcher.INSTANCE
    ));
    Matcher expected = SeqMatcher.create(StartMatcher.INSTANCE, AnyMatcher.INSTANCE);
    Assertions.assertEquals(expected, Optimizer.removeMatchAnyFollowedByStart(input));
  }

  @Test
  public void removeMatchAnyFollowedByIndexOf() {
    Matcher input = new ZeroOrMoreMatcher(
        AnyMatcher.INSTANCE,
        new IndexOfMatcher("foo", TrueMatcher.INSTANCE));
    Matcher expected = new IndexOfMatcher("foo", TrueMatcher.INSTANCE);
    Assertions.assertEquals(expected, Optimizer.removeMatchAnyFollowedByIndexOf(input));
  }

  @Test
  public void removeTrailingMatchAny() {
    Matcher input = new ZeroOrMoreMatcher(AnyMatcher.INSTANCE, EndMatcher.INSTANCE);
    Matcher expected = TrueMatcher.INSTANCE;
    Assertions.assertEquals(expected, Optimizer.removeTrailingMatchAny(input));
  }

  @Test
  public void removeSequentialMatchAny() {
    Matcher input = new ZeroOrMoreMatcher(
        AnyMatcher.INSTANCE,
        new ZeroOrMoreMatcher(AnyMatcher.INSTANCE, AnyMatcher.INSTANCE)
    );
    Matcher expected = new ZeroOrMoreMatcher(AnyMatcher.INSTANCE, AnyMatcher.INSTANCE);
    Assertions.assertEquals(expected, Optimizer.removeSequentialMatchAny(input));
  }

  private static CharClassMatcher cls(String pattern) {
    return new CharClassMatcher(AsciiSet.fromPattern(pattern));
  }

  @Test
  public void removeRedundantRepeatedChars_First() {
    // [a-z]*\\w*x => \\w*x
    Matcher input = new ZeroOrMoreMatcher(cls("a-z"),
        new ZeroOrMoreMatcher(cls("a-zA-Z0-9_"), new CharSeqMatcher("x")));
    Matcher expected = new ZeroOrMoreMatcher(cls("a-zA-Z0-9_"), new CharSeqMatcher("x"));
    Assertions.assertEquals(expected, Optimizer.removeRedundantRepeatedChars(input));
  }

  @Test
  public void removeRedundantRepeatedChars_Second() {
    // \\w*[a-z]*x => \\w*x
    Matcher input = new ZeroOrMoreMatcher(cls("a-zA-Z0-9_"),
        new ZeroOrMoreMatcher(cls("a-z"), new CharSeqMatcher("x")));
    Matcher expected = new ZeroOrMoreMatcher(cls("a-zA-Z0-9_"), new CharSeqMatcher("x"));
    Assertions.assertEquals(expected, Optimizer.removeRedundantRepeatedChars(input));
  }

  @Test
  public void removeRedundantRepeatedChars_FixedBetween() {
    // [a-z]*\\w\\w*x => \\w\\w*x
    Matcher next = SeqMatcher.create(cls("a-zA-Z0-9_"),
        new ZeroOrMoreMatcher(cls("a-zA-Z0-9_"), new CharSeqMatcher("x")));
    Matcher input = new ZeroOrMoreMatcher(cls("a-z"), next);
    Assertions.assertEquals(next, Optimizer.removeRedundantRepeatedChars(input));
  }

  @Test
  public void removeRedundantRepeatedChars_FixedBetweenSecond() {
    // \\w*\\w[a-z]*x => \\w*\\wx
    Matcher input = new ZeroOrMoreMatcher(cls("a-zA-Z0-9_"), SeqMatcher.create(
        cls("a-zA-Z0-9_"), new ZeroOrMoreMatcher(cls("a-z"), new CharSeqMatcher("x"))));
    Matcher expected = new ZeroOrMoreMatcher(cls("a-zA-Z0-9_"),
        SeqMatcher.create(cls("a-zA-Z0-9_"), new CharSeqMatcher("x")));
    Assertions.assertEquals(expected, Optimizer.removeRedundantRepeatedChars(input));
  }

  @Test
  public void removeRedundantRepeatedChars_FixedDifferent() {
    // [a-z]*-\\w*x, the fixed char is not in the larger set
    Matcher input = new ZeroOrMoreMatcher(cls("a-z"), SeqMatcher.create(
        new CharSeqMatcher("-"), new ZeroOrMoreMatcher(cls("a-zA-Z0-9_"), new CharSeqMatcher("x"))));
    Assertions.assertSame(input, Optimizer.removeRedundantRepeatedChars(input));
  }

  @Test
  public void removeRedundantRepeatedChars_Overlap() {
    // [a-c]*[b-d]*x, neither set contains the other
    Matcher input = new ZeroOrMoreMatcher(cls("a-c"),
        new ZeroOrMoreMatcher(cls("b-d"), new CharSeqMatcher("x")));
    Assertions.assertSame(input, Optimizer.removeRedundantRepeatedChars(input));
  }

  @Test
  public void removeRedundantRepeatedChars_NonAscii() {
    // [^a]*[b-z]*x, the first also matches non-ASCII chars
    Matcher negated = new CharClassMatcher(
        AsciiSet.fromPattern("a").invert(), AsciiSet.fromPattern("aA").invert(), false,
        CodePointRanges.ALL);
    Matcher input = new ZeroOrMoreMatcher(cls("b-z"),
        new ZeroOrMoreMatcher(negated, new CharSeqMatcher("x")));
    Matcher expected = new ZeroOrMoreMatcher(negated, new CharSeqMatcher("x"));
    Assertions.assertEquals(expected, Optimizer.removeRedundantRepeatedChars(input));
    Matcher reversed = new ZeroOrMoreMatcher(negated,
        new ZeroOrMoreMatcher(cls("b-z"), new CharSeqMatcher("x")));
    Assertions.assertEquals(expected, Optimizer.removeRedundantRepeatedChars(reversed));
  }

  @Test
  public void removeRedundantRepeatedChars_Alternatives() {
    // (?:a|b)*[a-c]*x => [a-c]*x
    Matcher input = new ZeroOrMoreMatcher(
        OrMatcher.create(new CharSeqMatcher("a"), new CharSeqMatcher("b")),
        new ZeroOrMoreMatcher(cls("a-c"), new CharSeqMatcher("x")));
    Matcher expected = new ZeroOrMoreMatcher(cls("a-c"), new CharSeqMatcher("x"));
    Assertions.assertEquals(expected, Optimizer.removeRedundantRepeatedChars(input));
  }

  @Test
  public void removeRedundantRepeatedChars_IgnoreCase() {
    // (?i)[a-z]*[A-Z]*x, the sets are the same once the case is ignored
    Matcher input = new ZeroOrMoreMatcher(new CharClassMatcher(AsciiSet.fromPattern("a-z"), true),
        new ZeroOrMoreMatcher(new CharClassMatcher(AsciiSet.fromPattern("A-Z"), true),
            new CharSeqMatcher("x")));
    Matcher expected = new ZeroOrMoreMatcher(
        new CharClassMatcher(AsciiSet.fromPattern("A-Z"), true), new CharSeqMatcher("x"));
    Assertions.assertEquals(expected, Optimizer.removeRedundantRepeatedChars(input));
  }

  @Test
  public void removeRedundantRepeatedChars_IndexOf() {
    // .*aa*x => .*ax
    Matcher input = new IndexOfMatcher("a",
        new ZeroOrMoreMatcher(new CharSeqMatcher("a"), new CharSeqMatcher("x")));
    Matcher expected = new IndexOfMatcher("a", new CharSeqMatcher("x"));
    Assertions.assertEquals(expected, Optimizer.removeRedundantRepeatedChars(input));
  }

  @Test
  public void removeRedundantRepeatedChars_IndexOfDifferentChars() {
    // .*baa*x, the a* cannot be shifted before the b
    Matcher input = new IndexOfMatcher("ba",
        new ZeroOrMoreMatcher(new CharSeqMatcher("a"), new CharSeqMatcher("x")));
    Assertions.assertSame(input, Optimizer.removeRedundantRepeatedChars(input));
  }

  @Test
  public void flattenNestedSequences() {
    Matcher input = SeqMatcher.create(
        SeqMatcher.create(AnyMatcher.INSTANCE),
        AnyMatcher.INSTANCE,
        SeqMatcher.create(AnyMatcher.INSTANCE, SeqMatcher.create(AnyMatcher.INSTANCE))
    );
    Matcher expected = SeqMatcher.create(
        AnyMatcher.INSTANCE,
        AnyMatcher.INSTANCE,
        AnyMatcher.INSTANCE,
        AnyMatcher.INSTANCE
    );
    Assertions.assertEquals(expected, Optimizer.flattenNestedSequences(input));
  }

  @Test
  public void flattenNestedOr() {
    Matcher input = OrMatcher.create(
        OrMatcher.create(AnyMatcher.INSTANCE, AnyMatcher.INSTANCE),
        AnyMatcher.INSTANCE,
        OrMatcher.create(AnyMatcher.INSTANCE, OrMatcher.create(AnyMatcher.INSTANCE))
    );
    Matcher expected = OrMatcher.create(
        AnyMatcher.INSTANCE,
        AnyMatcher.INSTANCE,
        AnyMatcher.INSTANCE,
        AnyMatcher.INSTANCE,
        AnyMatcher.INSTANCE
    );
    Assertions.assertEquals(expected, Optimizer.flattenNestedOr(input));
  }

  @Test
  public void dedupOr() {
    Matcher input = OrMatcher.create(
        new CharSeqMatcher("a"),
        new CharSeqMatcher("b"),
        new CharSeqMatcher("a")
    );
    Matcher expected = OrMatcher.create(
        new CharSeqMatcher("a"),
        new CharSeqMatcher("b")
    );
    Assertions.assertEquals(expected, Optimizer.dedupOr(input));
  }

  @Test
  public void removeFalseBranchesFromOr() {
    Matcher input = OrMatcher.create(
        new CharSeqMatcher("a"),
        FalseMatcher.INSTANCE,
        new CharSeqMatcher("b")
    );
    Matcher expected = OrMatcher.create(
        new CharSeqMatcher("a"),
        new CharSeqMatcher("b")
    );
    Assertions.assertEquals(expected, Optimizer.removeFalseBranchesFromOr(input));
  }

  @Test
  public void extractPrefixFromOr() {
    Matcher a = new CharSeqMatcher("a");
    Matcher b = new CharSeqMatcher("b");
    Matcher input = OrMatcher.create(
        new ZeroOrMoreMatcher(a, AnyMatcher.INSTANCE),
        new ZeroOrMoreMatcher(a, a),
        new ZeroOrMoreMatcher(a, b)
    );
    Matcher expected = SeqMatcher.create(
      new ZeroOrMoreMatcher(a, TrueMatcher.INSTANCE),
      OrMatcher.create(AnyMatcher.INSTANCE, a, b)
    );
    Assertions.assertEquals(expected, Optimizer.extractPrefixFromOr(input));
  }

  @Test
  public void inlineMatchAnyPrecedingOr() {
    Matcher a = new CharSeqMatcher("a");
    Matcher b = new CharSeqMatcher("b");
    Matcher input = new ZeroOrMoreMatcher(
        AnyMatcher.INSTANCE,
        OrMatcher.create(a, b)
    );
    Matcher expected = OrMatcher.create(
        new ZeroOrMoreMatcher(AnyMatcher.INSTANCE, a),
        new ZeroOrMoreMatcher(AnyMatcher.INSTANCE, b)
    );
    Assertions.assertEquals(expected, Optimizer.inlineMatchAnyPrecedingOr(input));
  }

  @Test
  public void startsWithCharSeq() {
    Matcher input = SeqMatcher.create(
        StartMatcher.INSTANCE,
        new CharSeqMatcher("ab"),
        AnyMatcher.INSTANCE
    );
    Matcher expected = SeqMatcher.create(
        new StartsWithMatcher("ab"),
        AnyMatcher.INSTANCE
    );
    Assertions.assertEquals(expected, Optimizer.startsWithCharSeq(input));
  }

  @Test
  public void combineCharSeqAfterStartsWith() {
    Matcher input = SeqMatcher.create(
        new StartsWithMatcher("a"),
        new CharSeqMatcher("b"),
        AnyMatcher.INSTANCE
    );
    Matcher expected = SeqMatcher.create(
        new StartsWithMatcher("ab"),
        AnyMatcher.INSTANCE
    );
    Assertions.assertEquals(expected, Optimizer.combineCharSeqAfterStartsWith(input));
  }

  @Test
  public void combineCharSeqAfterIndexOf() {
    Matcher input = new IndexOfMatcher("ab", new CharSeqMatcher("cd"));
    Matcher expected = new IndexOfMatcher("abcd", TrueMatcher.INSTANCE);
    Assertions.assertEquals(expected, Optimizer.combineCharSeqAfterIndexOf(input));
  }

  @Test
  public void combineAdjacentCharSeqs() {
    Matcher input = SeqMatcher.create(
        new CharSeqMatcher("a"),
        new CharSeqMatcher("b"),
        AnyMatcher.INSTANCE,
        new CharSeqMatcher("c"),
        new CharSeqMatcher("d")
    );
    Matcher expected = SeqMatcher.create(
        new CharSeqMatcher("ab"),
        AnyMatcher.INSTANCE,
        new CharSeqMatcher("cd")
    );
    Assertions.assertEquals(expected, Optimizer.combineAdjacentCharSeqs(input));
  }

  @Test
  public void zeroOrMoreMergeNext() {
    Matcher input = SeqMatcher.create(
        AnyMatcher.INSTANCE,
        new ZeroOrMoreMatcher(AnyMatcher.INSTANCE, AnyMatcher.INSTANCE),
        AnyMatcher.INSTANCE
    );
    Matcher expected = SeqMatcher.create(
        AnyMatcher.INSTANCE,
        new ZeroOrMoreMatcher(
            AnyMatcher.INSTANCE,
            SeqMatcher.create(AnyMatcher.INSTANCE, AnyMatcher.INSTANCE))
    );
    Assertions.assertEquals(expected, Optimizer.mergeNext(input));
  }

  @Test
  public void orMergeNext() {
    Matcher input = SeqMatcher.create(
        AnyMatcher.INSTANCE,
        OrMatcher.create(AnyMatcher.INSTANCE, AnyMatcher.INSTANCE),
        AnyMatcher.INSTANCE
    );
    Matcher expected = SeqMatcher.create(
        AnyMatcher.INSTANCE,
        (new OrMatcher(AnyMatcher.INSTANCE, AnyMatcher.INSTANCE)).mergeNext(AnyMatcher.INSTANCE)
    );
    Assertions.assertEquals(expected, Optimizer.mergeNext(input));
  }

  @Test
  public void flattenRepeatedUnbounded() {
    // (a{2,}){3} => a{6,}
    Matcher a = new CharSeqMatcher("a");
    Matcher input = new RepeatMatcher(
        SeqMatcher.create(
            new RepeatMatcher(a, 2, 2),
            new ZeroOrMoreMatcher(a, TrueMatcher.INSTANCE)),
        3, 3);
    Matcher expected = SeqMatcher.create(
        new RepeatMatcher(a, 6, 6),
        new ZeroOrMoreMatcher(a, TrueMatcher.INSTANCE));
    Assertions.assertEquals(expected, Optimizer.flattenRepeatedUnbounded(input));
  }

  @Test
  public void flattenRepeatedUnbounded_OneOrMore() {
    // (a+){2} => a{2,}
    Matcher a = new CharSeqMatcher("a");
    Matcher input = new RepeatMatcher(
        SeqMatcher.create(a, new ZeroOrMoreMatcher(a, TrueMatcher.INSTANCE)), 2, 2);
    Matcher expected = SeqMatcher.create(
        new RepeatMatcher(a, 2, 2),
        new ZeroOrMoreMatcher(a, TrueMatcher.INSTANCE));
    Assertions.assertEquals(expected, Optimizer.flattenRepeatedUnbounded(input));
  }

  @Test
  public void flattenRepeatedUnbounded_Optional() {
    // (a+)?b => a*b
    Matcher a = new CharSeqMatcher("a");
    Matcher b = new CharSeqMatcher("b");
    Matcher input = new ZeroOrOneMatcher(
        SeqMatcher.create(a, new ZeroOrMoreMatcher(a, TrueMatcher.INSTANCE)), b);
    Matcher expected = new ZeroOrMoreMatcher(a, b);
    Assertions.assertEquals(expected, Optimizer.flattenRepeatedUnbounded(input));
  }

  @Test
  public void flattenRepeatedUnbounded_OptionalMinTwo() {
    // (a{2,})?b cannot be simplified
    Matcher a = new CharSeqMatcher("a");
    Matcher input = new ZeroOrOneMatcher(
        SeqMatcher.create(
            new RepeatMatcher(a, 2, 2),
            new ZeroOrMoreMatcher(a, TrueMatcher.INSTANCE)),
        new CharSeqMatcher("b"));
    Assertions.assertEquals(input, Optimizer.flattenRepeatedUnbounded(input));
  }

  @Test
  public void removeRepeatedStart() {
    Matcher input = new ZeroOrMoreMatcher(StartMatcher.INSTANCE, AnyMatcher.INSTANCE);
    Matcher expected = AnyMatcher.INSTANCE;
    Assertions.assertEquals(expected, Optimizer.removeRepeatedStart(input));
  }

  @Test
  public void combineAdjacentStart() {
    Matcher input = SeqMatcher.create(
        StartMatcher.INSTANCE,
        StartMatcher.INSTANCE,
        StartMatcher.INSTANCE,
        StartMatcher.INSTANCE,
        AnyMatcher.INSTANCE
    );
    Matcher expected = SeqMatcher.create(StartMatcher.INSTANCE, AnyMatcher.INSTANCE);
    Assertions.assertEquals(expected, Optimizer.combineAdjacentStart(input));
  }

  @Test
  public void convertRepeatedAnyCharSeqToIndexOf() {
    Matcher input = new ZeroOrMoreMatcher(AnyMatcher.INSTANCE, SeqMatcher.create(
        new CharSeqMatcher("abc"),
        new ZeroOrMoreMatcher(AnyMatcher.INSTANCE, SeqMatcher.create(
            new CharSeqMatcher("def"),
            new ZeroOrMoreMatcher(AnyMatcher.INSTANCE, TrueMatcher.INSTANCE)
        ))
    ));
    Matcher expected = SeqMatcher.create(
        new IndexOfMatcher(
            "abc",
            new ZeroOrMoreMatcher(AnyMatcher.INSTANCE, SeqMatcher.create(
                new CharSeqMatcher("def"),
                new ZeroOrMoreMatcher(AnyMatcher.INSTANCE, TrueMatcher.INSTANCE)
            ))
        )
    );
    Assertions.assertEquals(expected, Optimizer.convertRepeatedAnyCharSeqToIndexOf(input));
  }

  @Test
  public void optimizeStartsWith() {
    PatternMatcher actual = PatternMatcher.compile("^foo");
    PatternMatcher expected = new StartsWithMatcher("foo");
    Assertions.assertEquals(expected, actual);
  }

  @Test
  public void optimizeEndStart() {
    // Matches an empty string, so it cannot be simplified to false
    PatternMatcher actual = PatternMatcher.compile("$^");
    PatternMatcher expected = new ZeroOrMoreMatcher(
        AnyMatcher.INSTANCE,
        SeqMatcher.create(EndMatcher.INSTANCE, StartMatcher.INSTANCE));
    Assertions.assertEquals(expected, actual);
  }

  @Test
  public void optimizeEndsWith() {
    PatternMatcher actual = PatternMatcher.compile(".*foo$");
    PatternMatcher expected = new IndexOfMatcher("foo", EndMatcher.INSTANCE);
    Assertions.assertEquals(expected, actual);
  }

  @Test
  public void optimizeEndsWithPattern() {
    PatternMatcher actual = PatternMatcher.compile(".*foo.bar$");
    PatternMatcher expected = new IndexOfMatcher(
        "foo",
        SeqMatcher.create(
            AnyMatcher.INSTANCE,
            new CharSeqMatcher("bar"),
            EndMatcher.INSTANCE
        )
    );
    Assertions.assertEquals(expected, actual);
  }

  @Test
  public void optimizeIndexOfSeq() {
    PatternMatcher actual = PatternMatcher.compile("^.*abc.*def");
    PatternMatcher expected = new IndexOfSeqMatcher(
        new String[] {"abc", "def"}, false, false, false,
        new IndexOfMatcher("abc", new IndexOfMatcher("def", TrueMatcher.INSTANCE))
    );
    Assertions.assertEquals(expected, actual);
  }

  @Test
  public void optimizeIndexOfSeqAny() {
    PatternMatcher actual = PatternMatcher.compile("^.*abc.*def.*");
    PatternMatcher expected = new IndexOfSeqMatcher(
        new String[] {"abc", "def"}, false, false, false,
        new IndexOfMatcher("abc", new IndexOfMatcher("def", TrueMatcher.INSTANCE))
    );
    Assertions.assertEquals(expected, actual);
  }

  @Test
  public void optimizeDuplicateOr() {
    PatternMatcher actual = PatternMatcher.compile("^(abc|a(bc)|((a)(b))c)");
    PatternMatcher expected = new StartsWithMatcher("abc");
    Assertions.assertEquals(expected, actual);
  }

  @Test
  public void optimizeOptionValue() {
    PatternMatcher actual = PatternMatcher.compile("^a?a");
    PatternMatcher expected = SeqMatcher.create(
        StartMatcher.INSTANCE,
        new ZeroOrOneMatcher(new CharSeqMatcher("a"), new CharSeqMatcher("a"))
    );
    Assertions.assertEquals(expected, actual);
  }

  @Test
  public void optimizeOrSimple() {
    PatternMatcher actual = PatternMatcher.compile("^abc|def|ghi");
    PatternMatcher expected = OrMatcher.create(
        new StartsWithMatcher("abc"),
        new IndexOfMatcher("def", TrueMatcher.INSTANCE),
        new IndexOfMatcher("ghi", TrueMatcher.INSTANCE)
    );
    Assertions.assertEquals(expected, actual);
  }

  @Test
  public void optimizeOrIndexOf() {
    PatternMatcher actual = PatternMatcher.compile("^.*abc.*|.*def.*|.*ghi*.");
    PatternMatcher expected = OrMatcher.create(
        new IndexOfMatcher("abc", TrueMatcher.INSTANCE),
        new IndexOfMatcher("def", TrueMatcher.INSTANCE),
        new IndexOfMatcher(
            "gh",
            new ZeroOrMoreMatcher(new CharSeqMatcher("i"), AnyMatcher.INSTANCE)
        )
    );
    Assertions.assertEquals(expected, actual);
  }

  @Test
  public void optimizeOrPrefix() {
    PatternMatcher actual = PatternMatcher.compile("^(abc123|abc456)");
    PatternMatcher expected = SeqMatcher.create(
        new StartsWithMatcher("abc"),
        new OrMatcher(new CharSeqMatcher("123"), new CharSeqMatcher("456"))
    );
    Assertions.assertEquals(expected, actual);
  }

  @Test
  public void optimizeOrPrefixPattern() {
    PatternMatcher actual = PatternMatcher.compile("^abc.*foo$|^abc.*bar$|^abc.*baz$");
    PatternMatcher expected = SeqMatcher.create(
        new StartsWithMatcher("abc"),
        new OrMatcher(
            new IndexOfMatcher("foo", EndMatcher.INSTANCE),
            new IndexOfMatcher("bar", EndMatcher.INSTANCE),
            new IndexOfMatcher("baz", EndMatcher.INSTANCE)
        )
    );
    Assertions.assertEquals(expected, actual);
  }

  @Test
  public void optimizeOrFalse() {
    PatternMatcher actual = PatternMatcher.compile("abc|$foo|$bar");
    PatternMatcher expected = new IndexOfMatcher("abc", TrueMatcher.INSTANCE);
    Assertions.assertEquals(expected, actual);
  }

  @Test
  public void optimizeOrFalseEmpty() {
    PatternMatcher actual = PatternMatcher.compile("$foo|$bar");
    PatternMatcher expected = FalseMatcher.INSTANCE;
    Assertions.assertEquals(expected, actual);
  }

  @Test
  public void optimizeNegativeLookaheadOr() {
    PatternMatcher actual = PatternMatcher.compile("^^abc.def(?!.*(1000|1500))");
    PatternMatcher expected = SeqMatcher.create(
        new StartsWithMatcher("abc"),
        AnyMatcher.INSTANCE,
        new CharSeqMatcher("def"),
        new NegativeLookaheadMatcher(new IndexOfMatcher(
            "1",
            OrMatcher.create(new CharSeqMatcher("000"), new CharSeqMatcher("500"))
        ))
    );
    Assertions.assertEquals(expected, actual);
  }

  private static Matcher ignoreCaseMatcher(String pattern) {
    PatternMatcher m = PatternMatcher.compile(pattern).ignoreCase();
    Assertions.assertTrue(m instanceof IgnoreCaseMatcher, pattern);
    return ((IgnoreCaseMatcher) m).matcher();
  }

  @Test
  public void ignoreCaseKeepsFolds() {
    // The rewrite for ignoring the case goes through the delegate of the folded matchers,
    // so they need to be folded again
    Assertions.assertTrue(ignoreCaseMatcher("a.*b.*c") instanceof IndexOfSeqMatcher);
    Assertions.assertTrue(ignoreCaseMatcher("^(abc|def|ghi)") instanceof OrStartsWithMatcher);
  }

  @Test
  public void ignoreCaseOptimizesFoldedClass() {
    // The class is not empty, but the folded set is, so it can never match
    Assertions.assertEquals(FalseMatcher.INSTANCE, ignoreCaseMatcher("^[^\\x00-`b-\\x7f\\x{80}-\\x{10ffff}]$"));
    Assertions.assertTrue(PatternMatcher.compile("^[^\\x00-`b-\\x7f\\x{80}-\\x{10ffff}]$").ignoreCase().neverMatches());
  }

  @Test
  public void ignoreCaseKeptWhenMerging() {
    Assertions.assertEquals(
        new StartsWithMatcher("abcd", true),
        ignoreCaseMatcher("^ab[c]d"));
    Assertions.assertEquals(
        new IndexOfMatcher("abcd", TrueMatcher.INSTANCE, true),
        Optimizer.combineCharSeqAfterIndexOf(
            new IndexOfMatcher("ab", new CharSeqMatcher("cd", true), true)));

    // Not merged if the case is different
    Matcher input = new IndexOfMatcher("ab", new CharSeqMatcher("cd"), true);
    Assertions.assertEquals(input, Optimizer.combineCharSeqAfterIndexOf(input));
  }

  @Test
  public void combineAdjacentCharSeqsDifferentCase() {
    Matcher input = SeqMatcher.create(
        new CharSeqMatcher("ab", true),
        new CharSeqMatcher("cd"),
        new CharSeqMatcher("ef")
    );
    Matcher expected = SeqMatcher.create(
        new CharSeqMatcher("ab", true),
        new CharSeqMatcher("cdef")
    );
    Assertions.assertEquals(expected, Optimizer.combineAdjacentCharSeqs(input));
  }
}
