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
package com.netflix.spectator.impl.matcher;

import com.netflix.spectator.impl.AsciiSet;
import com.netflix.spectator.impl.PatternMatcher;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;

public class MatcherSerializationTest {

  static void checkSerde(PatternMatcher matcher) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try (ObjectOutputStream out = new ObjectOutputStream(baos)) {
        out.writeObject(matcher);
      }

      ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
      try (ObjectInputStream in = new ObjectInputStream(bais)) {
        PatternMatcher deserialized = (PatternMatcher) in.readObject();
        Assertions.assertEquals(matcher, deserialized);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void any() {
    checkSerde(AnyMatcher.INSTANCE);
  }

  @Test
  public void charClass() {
    checkSerde(new CharClassMatcher(AsciiSet.fromPattern("abc")));
  }

  @Test
  public void charClassNegated() {
    // The folded set is different from the set with the other case added
    AsciiSet set = AsciiSet.fromPattern("a").invert();
    AsciiSet foldedSet = AsciiSet.fromPattern("aA").invert();
    checkSerde(new CharClassMatcher(set, foldedSet, false));
    checkSerde(PatternMatcher.compile("[^a]b").ignoreCase());
  }

  @Test
  public void charClassWithoutFoldedSet() throws Exception {
    // Instances serialized by an older version will not have the folded set
    CharClassMatcher matcher = new CharClassMatcher(AsciiSet.fromPattern("a"), true);
    Field field = CharClassMatcher.class.getDeclaredField("foldedSet");
    field.setAccessible(true);
    field.set(matcher, null);
    CharClassMatcher deserialized = roundTrip(matcher);
    Assertions.assertEquals(new CharClassMatcher(AsciiSet.fromPattern("a"), true), deserialized);
    Assertions.assertEquals(AsciiSet.fromPattern("aA"), deserialized.foldedSet());
  }

  @Test
  public void charClassNonAscii() throws Exception {
    checkSerde(PatternMatcher.compile("^[a\u00E9\uD83D\uDE00-\uD83D\uDE4F]+x"));
    checkSerde(PatternMatcher.compile("^[^\u00E9]+x"));

    // The lookup flag is derived, so it is recomputed after deserialization
    CharClassMatcher matcher = new CharClassMatcher(
        AsciiSet.none(), AsciiSet.none(), false, CodePointRanges.ALL);
    CharClassMatcher deserialized = roundTrip(matcher);
    Assertions.assertTrue(deserialized.hasCharLookup());
    Assertions.assertEquals(1, deserialized.matches("\u00E9", 0, 1));
  }

  @Test
  public void charClassWithoutNonAscii() throws Exception {
    // Instances serialized by an older version will not have the non-ASCII code points
    CharClassMatcher matcher = new CharClassMatcher(AsciiSet.fromPattern("a"), true);
    Field field = CharClassMatcher.class.getDeclaredField("nonAscii");
    field.setAccessible(true);
    field.set(matcher, null);
    CharClassMatcher deserialized = roundTrip(matcher);
    Assertions.assertEquals(new CharClassMatcher(AsciiSet.fromPattern("a"), true), deserialized);
    Assertions.assertEquals(CodePointRanges.NONE, deserialized.nonAscii());
  }

  private static CharClassMatcher roundTrip(CharClassMatcher matcher) throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(baos)) {
      out.writeObject(matcher);
    }
    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
    try (ObjectInputStream in = new ObjectInputStream(bais)) {
      return (CharClassMatcher) in.readObject();
    }
  }

  @Test
  public void minLengthRecomputed() throws Exception {
    // Instances serialized by an older version will not have the min length. A rewrite reuses
    // matchers that do not change, so it is recomputed when the instance is read.
    Matcher a = new CharSeqMatcher("abc");
    Matcher[] matchers = {
        SeqMatcher.create(a, a),
        OrMatcher.create(a, new CharSeqMatcher("de")),
        new ZeroOrMoreMatcher(a, a),
        new ZeroOrOneMatcher(a, a),
        new RepeatMatcher(a, 2, 3, a, false)
    };
    for (Matcher m : matchers) {
      int expected = m.minLength();
      Field field = m.getClass().getDeclaredField("minLength");
      field.setAccessible(true);
      field.set(m, 0);
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try (ObjectOutputStream out = new ObjectOutputStream(baos)) {
        out.writeObject(m);
      }
      try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
        Matcher deserialized = (Matcher) in.readObject();
        Assertions.assertEquals(expected, deserialized.minLength(), m.toString());
      }
    }
  }

  @Test
  public void charSeq() {
    checkSerde(new CharSeqMatcher("abc"));
  }

  @Test
  public void end() {
    checkSerde(EndMatcher.INSTANCE);
  }

  @Test
  public void falseMatcher() {
    checkSerde(FalseMatcher.INSTANCE);
  }

  @Test
  public void ignoreCase() {
    checkSerde(new IgnoreCaseMatcher(EndMatcher.INSTANCE));
  }

  @Test
  public void indexOf() {
    checkSerde(new IndexOfMatcher("abc", EndMatcher.INSTANCE));
  }

  @Test
  public void negativeLookahead() {
    checkSerde(new NegativeLookaheadMatcher(AnyMatcher.INSTANCE));
  }

  @Test
  public void or() {
    checkSerde(OrMatcher.create(AnyMatcher.INSTANCE, EndMatcher.INSTANCE));
  }

  @Test
  public void positiveLookahead() {
    checkSerde(new PositiveLookaheadMatcher(AnyMatcher.INSTANCE));
  }

  @Test
  public void repeat() {
    checkSerde(new RepeatMatcher(AnyMatcher.INSTANCE, 0, 3));
  }

  @Test
  public void seq() {
    checkSerde(SeqMatcher.create(AnyMatcher.INSTANCE, AnyMatcher.INSTANCE));
  }

  @Test
  public void start() {
    checkSerde(StartMatcher.INSTANCE);
  }

  @Test
  public void startsWith() {
    checkSerde(new StartsWithMatcher("abc"));
  }

  @Test
  public void trueMatcher() {
    checkSerde(TrueMatcher.INSTANCE);
  }

  @Test
  public void zeroOrMore() {
    checkSerde(new ZeroOrMoreMatcher(AnyMatcher.INSTANCE, EndMatcher.INSTANCE));
  }
}
