/*
 * Copyright 2014-2018 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spectator.api.patterns;

import com.netflix.spectator.api.ManualClock;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

@RunWith(JUnit4.class)
public class CardinalityLimitersTest {

  @Test
  public void first2() {
    Function<String, String> f = CardinalityLimiters.first(2);
    Assert.assertEquals("a", f.apply("a"));
    Assert.assertEquals("b", f.apply("b"));
    Assert.assertEquals(CardinalityLimiters.OTHERS, f.apply("c"));
    Assert.assertEquals("a", f.apply("a"));
  }

  @Test
  public void firstToStringEmpty() {
    Function<String, String> f = CardinalityLimiters.first(2);
    Assert.assertEquals("FirstLimiter()", f.toString());
  }

  @Test
  public void firstToStringPartial() {
    Function<String, String> f = CardinalityLimiters.first(2);
    Assert.assertEquals("b", f.apply("b"));
    Assert.assertEquals("FirstLimiter(b)", f.toString());
  }

  @Test
  public void firstToStringFull() {
    Function<String, String> f = CardinalityLimiters.first(2);
    Assert.assertEquals("a", f.apply("a"));
    Assert.assertEquals("b", f.apply("b"));
    Assert.assertEquals("FirstLimiter(a,b)", f.toString());
  }

  private void updateN(Function<String, String> f, int n, String s) {
    for (int i = 0; i < n; ++i) {
      f.apply(s);
    }
  }

  private void advanceClock(ManualClock clock) {
    long t = clock.wallTime();
    clock.setWallTime(t + 10 * 60000 + 1);
  }

  @Test
  public void mostFrequentIsUsed() {
    ManualClock clock = new ManualClock(0L, 0L);
    Function<String, String> f = CardinalityLimiters.mostFrequent(2, clock);

    // Setup some basic stats
    updateN(f, 4, "a");
    updateN(f, 3, "b");
    updateN(f, 2, "c");
    updateN(f, 1, "d");

    // Refresh cutoff, should be 3 for the top 2
    advanceClock(clock);
    Assert.assertEquals("a", f.apply("a"));

    // If the values are close, then which are selected will be based on which come in
    // first. In this case "c" should beat out "b"
    Assert.assertEquals("c", f.apply("c"));
    Assert.assertEquals(CardinalityLimiters.OTHERS, f.apply("b"));

    // Until the cutoff is updated, "d" won't show up no matter how frequent
    Assert.assertEquals(CardinalityLimiters.OTHERS, f.apply("d"));
    updateN(f, 42, "d");
    Assert.assertEquals(CardinalityLimiters.OTHERS, f.apply("d"));

    // Now "d" is most frequent
    advanceClock(clock);
    Assert.assertEquals("d", f.apply("d"));
  }

  @Test
  public void mostFrequentAllUnique() {
    // Ensure we have a somewhat stable set and there isn't a memory leak if every value is
    // unique. For example, if a user tried to use a request id.
    ManualClock clock = new ManualClock(0L, 0L);
    Function<String, String> f = CardinalityLimiters.mostFrequent(2, clock);
    Set<String> values = new TreeSet<>();
    for (int i = 0; i < 10000; ++i) {
      values.add(f.apply("" + i));
      clock.setWallTime(i * 1000);
    }
    // The values less than equal 9616 should have been cleaned up based on the clock
    Assert.assertFalse(f.toString().contains("9616"));
    Assert.assertEquals(35, values.size());
  }

  @Test
  public void mostFrequentTransitionTime() {
    // Ensure we have a somewhat stable set and there isn't a memory leak if every value is
    // unique. For example, if a user tried to use a request id.
    ManualClock clock = new ManualClock(0L, 0L);
    Function<String, String> f = CardinalityLimiters.mostFrequent(2, clock);
    Set<String> values = new TreeSet<>();

    // Lots of activity on old asg
    long i = 0;
    for (; i < 1_000_000; ++i) {
      values.add(f.apply("app-a-v001"));
      values.add(f.apply("app-b-v001"));
      clock.setWallTime(i * 1000);
    }

    // Activity moved to new asg
    for (; i < 2_000_000; ++i) {
      values.add(f.apply("app-a-v002"));
      values.add(f.apply("app-b-v001"));
      clock.setWallTime(i * 1000);
    }

    Assert.assertTrue(values.contains("app-a-v002"));
  }
}
