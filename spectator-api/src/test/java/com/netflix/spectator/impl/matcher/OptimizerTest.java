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
  public void convertEmptyCharClassToFalse() {
    Matcher input = new CharClassMatcher(AsciiSet.none());
    Matcher expected = FalseMatcher.INSTANCE;
    Assertions.assertEquals(expected, Optimizer.convertEmptyCharClassToFalse(input));
  }

  @Test
  public void convertSingleCharClassToSeq() {
    Matcher input = new CharClassMatcher(AsciiSet.fromPattern("a"));
    Matcher expected = new CharSeqMatcher('a');
    Assertions.assertEquals(expected, Optimizer.convertSingleCharClassToSeq(input));
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
    PatternMatcher actual = PatternMatcher.compile("$^");
    PatternMatcher expected = FalseMatcher.INSTANCE;
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
    PatternMatcher expected = new IndexOfMatcher(
        "abc",
        new IndexOfMatcher("def", TrueMatcher.INSTANCE)
    );
    Assertions.assertEquals(expected, actual);
  }

  @Test
  public void optimizeIndexOfSeqAny() {
    PatternMatcher actual = PatternMatcher.compile("^.*abc.*def.*");
    PatternMatcher expected = new IndexOfMatcher(
        "abc",
        new IndexOfMatcher("def", TrueMatcher.INSTANCE)
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
}
