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
