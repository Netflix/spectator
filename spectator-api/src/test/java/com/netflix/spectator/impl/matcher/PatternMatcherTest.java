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

import com.netflix.spectator.impl.PatternMatcher;
import org.junit.jupiter.api.Assertions;

import java.util.regex.Pattern;

public class PatternMatcherTest extends AbstractPatternMatcherTest {

  @Override
  protected void testRE(String regex, String value) {
    Pattern pattern = Pattern.compile("^.*(" + regex + ")", Pattern.DOTALL);
    PatternMatcher matcher = PatternMatcher.compile(regex);
    if (pattern.matcher(value).find()) {
      Assertions.assertTrue(matcher.matches(value), regex + " should match " + value);
    } else {
      Assertions.assertFalse(matcher.matches(value), regex + " shouldn't match " + value);
    }

    // Check pattern can be recreated from toString
    PatternMatcher actual = PatternMatcher.compile(matcher.toString());
    Assertions.assertEquals(matcher, actual);

    // Check we can serialize and deserialize the matchers
    MatcherSerializationTest.checkSerde(matcher);
  }
}
