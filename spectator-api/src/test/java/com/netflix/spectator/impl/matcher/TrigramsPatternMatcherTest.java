/*
 * Copyright 2014-2023 Netflix, Inc.
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

import com.netflix.spectator.impl.PatternMatcher;
import org.junit.jupiter.api.Assertions;

import java.util.SortedSet;

public class TrigramsPatternMatcherTest extends AbstractPatternMatcherTest {

  @Override
  protected void testRE(String regex, String value) {
    PatternMatcher matcher = PatternMatcher.compile(regex);
    SortedSet<String> trigrams = matcher.trigrams();

    // Trigrams should be more lenient than the actual pattern, so anything that is matched
    // by the pattern must be a possible match with the trigrams.
    if (matcher.matches(value)) {
      Assertions.assertTrue(couldMatch(trigrams, value));
    }
  }

  private boolean couldMatch(SortedSet<String> trigrams, String value) {
    return trigrams.stream().allMatch(value::contains);
  }
}
