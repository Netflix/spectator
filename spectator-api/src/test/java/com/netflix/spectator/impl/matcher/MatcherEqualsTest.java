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

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class MatcherEqualsTest {

  @Test
  public void any() {
    EqualsVerifier.forClass(AnyMatcher.class).verify();
  }

  @Test
  public void charClass() {
    EqualsVerifier
        .forClass(CharClassMatcher.class)
        .withNonnullFields("set")
        .verify();
  }

  @Test
  public void charSeq() {
    EqualsVerifier
        .forClass(CharSeqMatcher.class)
        .withNonnullFields("pattern")
        .verify();
  }

  @Test
  public void end() {
    EqualsVerifier.forClass(EndMatcher.class).verify();
  }

  @Test
  public void falseMatcher() {
    EqualsVerifier.forClass(FalseMatcher.class).verify();
  }

  @Test
  public void ignoreCase() {
    EqualsVerifier
        .forClass(IgnoreCaseMatcher.class)
        .withNonnullFields("matcher")
        .verify();
  }

  @Test
  public void indexOf() {
    EqualsVerifier
        .forClass(IndexOfMatcher.class)
        .withNonnullFields("pattern", "next")
        .verify();
  }

  @Test
  public void negativeLookahead() {
    EqualsVerifier
        .forClass(NegativeLookaheadMatcher.class)
        .withNonnullFields("matcher")
        .verify();
  }

  @Test
  public void or() {
    EqualsVerifier.forClass(OrMatcher.class).verify();
  }

  @Test
  public void positiveLookahead() {
    EqualsVerifier
        .forClass(PositiveLookaheadMatcher.class)
        .withNonnullFields("matcher")
        .verify();
  }

  @Test
  public void repeat() {
    EqualsVerifier
        .forClass(RepeatMatcher.class)
        .withNonnullFields("repeated")
        .verify();
  }

  @Test
  public void seq() {
    EqualsVerifier.forClass(SeqMatcher.class).verify();
  }

  @Test
  public void start() {
    EqualsVerifier.forClass(StartMatcher.class).verify();
  }

  @Test
  public void startsWith() {
    EqualsVerifier
        .forClass(StartsWithMatcher.class)
        .withNonnullFields("pattern")
        .verify();
  }

  @Test
  public void trueMatcher() {
    EqualsVerifier.forClass(TrueMatcher.class).verify();
  }

  @Test
  public void zeroOrMore() {
    EqualsVerifier
        .forClass(ZeroOrMoreMatcher.class)
        .withNonnullFields("repeated", "next")
        .verify();
  }

  @Test
  public void zeroOrOne() {
    EqualsVerifier
        .forClass(ZeroOrOneMatcher.class)
        .withNonnullFields("repeated", "next")
        .verify();
  }
}
