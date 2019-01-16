/*
 * Copyright 2014-2019 Netflix, Inc.
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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

public class CardinalityLimitersTest {

  @Test
  public void first2() {
    Function<String, String> f = CardinalityLimiters.first(2);
    Assertions.assertEquals("a", f.apply("a"));
    Assertions.assertEquals("b", f.apply("b"));
    Assertions.assertEquals(CardinalityLimiters.OTHERS, f.apply("c"));
    Assertions.assertEquals("a", f.apply("a"));
  }

  @Test
  public void firstToStringEmpty() {
    Function<String, String> f = CardinalityLimiters.first(2);
    Assertions.assertEquals("FirstLimiter()", f.toString());
  }

  @Test
  public void firstToStringPartial() {
    Function<String, String> f = CardinalityLimiters.first(2);
    Assertions.assertEquals("b", f.apply("b"));
    Assertions.assertEquals("FirstLimiter(b)", f.toString());
  }

  @Test
  public void firstToStringFull() {
    Function<String, String> f = CardinalityLimiters.first(2);
    Assertions.assertEquals("a", f.apply("a"));
    Assertions.assertEquals("b", f.apply("b"));
    Assertions.assertEquals("FirstLimiter(a,b)", f.toString());
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
  public void mostFrequentUnderLimit() {
    int n = 27;
    ManualClock clock = new ManualClock(0L, 0L);
    Function<String, String> f = CardinalityLimiters.mostFrequent(n, clock);
    for (int t = 0; t < 1000; ++t) {
      for (int i = 0; i < n; ++i) {
        Assertions.assertEquals("" + i, f.apply("" + i));
      }
      clock.setWallTime(t * 1000);
    }
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
    Assertions.assertEquals("a", f.apply("a"));

    // If the values are close then bias towards the names that come first alphabetically
    Assertions.assertEquals(CardinalityLimiters.OTHERS, f.apply("c"));
    Assertions.assertEquals("b", f.apply("b"));

    // Until the cutoff is updated, "d" won't show up no matter how frequent
    Assertions.assertEquals(CardinalityLimiters.OTHERS, f.apply("d"));
    updateN(f, 42, "d");
    Assertions.assertEquals(CardinalityLimiters.OTHERS, f.apply("d"));

    // Now "d" is most frequent
    advanceClock(clock);
    Assertions.assertEquals("d", f.apply("d"));
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
    Assertions.assertFalse(f.toString().contains("9616"));
    Assertions.assertEquals(3, values.size());
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

    Assertions.assertTrue(values.contains("app-a-v002"));
  }

  @Test
  public void mostFrequentTemporaryChurn() {
    ManualClock clock = new ManualClock(0L, 0L);
    Function<String, String> f = CardinalityLimiters.mostFrequent(2, clock);
    Set<String> values = new TreeSet<>();
    for (int t = 0; t < 250; ++t) {
      if (t < 100) {
        values.add(f.apply("a"));
      } else if (t < 117) {
        // Simulates 17 minutes of high churn
        for (int i = 0; i < 200; ++i) {
          values.add(f.apply("" + i));
        }
      } else {
        // This should come through within 2h
        values.add(f.apply("b"));
      }
      clock.setWallTime(t * 60000);
    }
    Assertions.assertEquals(6, values.size());
    Assertions.assertEquals("b", f.apply("b"));
  }

  @Test
  public void mostFrequentClusterUniform() {
    // Simulate a cluster with independent limiters where high cardinality values are
    // coming in round-robin to each node of the cluster.
    int warmupEnd = 60;
    int finalEnd = 24 * 60;
    int cardinalityLimit = 25;
    int clusterSize = 18;
    int numValues = 2000;


    // Ensure we have a somewhat stable set and there isn't a memory leak if every value is
    // unique. For example, if a user tried to use a request id.
    ManualClock clock = new ManualClock(0L, 0L);
    List<Function<String, String>> limiters = new ArrayList<>();
    for (int i = 0; i < clusterSize; ++i) {
      limiters.add(CardinalityLimiters.mostFrequent(cardinalityLimit, clock));
    }

    Random r = new Random(42);
    Set<String> values = new TreeSet<>();

    Runnable singleIteration = () -> {
      for (int v = 0; v < numValues; ++v) {
        int n = r.nextInt(limiters.size());
        Function<String, String> f = limiters.get(n);
        values.add(f.apply("" + v));
      }
    };

    int t = 0;
    for (; t < warmupEnd; ++t) {
      singleIteration.run();
      clock.setWallTime(t * 60000);
    }

    // Should be proportional to the cluster size, not the number of input values
    Assertions.assertTrue(values.size() < 2 * clusterSize * cardinalityLimit);

    values.clear();
    for (; t < finalEnd; ++t) {
      singleIteration.run();
      clock.setWallTime(t * 60000);
    }

    // Should only have the others value
    Assertions.assertEquals(1, values.size());
  }

  @Test
  public void mostFrequentClusterBiased() {
    // Simulate a cluster with independent limiters where high cardinality values are
    // coming in round-robin to each node of the cluster. There is a small set of more
    // frequent values that should be preserved.
    int warmupEnd = 12 * 60;
    int finalEnd = 24 * 60;
    int cardinalityLimit = 25;
    int clusterSize = 18;
    int numFrequentValues = 10;
    int numValues = 2000;

    // Setup a separate limiter for each node of the cluster
    ManualClock clock = new ManualClock(0L, 0L);
    List<Function<String, String>> limiters = new ArrayList<>();
    for (int i = 0; i < clusterSize; ++i) {
      limiters.add(CardinalityLimiters.mostFrequent(cardinalityLimit, clock));
    }

    Random r = new Random(42);
    Set<String> values = new TreeSet<>();

    Runnable singleIteration = () -> {
      // Values with heavier use
      for (int v = 0; v < numFrequentValues; ++v) {
        for (int i = 0; i < 1 + v; ++i) {
          int n = r.nextInt(limiters.size());
          Function<String, String> f = limiters.get(n);
          values.add(f.apply("" + v));
        }
      }

      // Big tail of values with a lot of churn
      for (int v = 0; v < numValues; ++v) {
        int n = r.nextInt(limiters.size());
        Function<String, String> f = limiters.get(n);
        values.add(f.apply("" + v));
      }
    };

    // Warmup phase, there will be a bit of a burst here, but it should stabilize quickly
    // and start eliminating values with high amounts of churn
    int t = 0;
    for (; t < warmupEnd; ++t) {
      singleIteration.run();
      clock.setWallTime(t * 60000);
    }

    // Should be proportional to the cluster size, not the number of input values
    Assertions.assertTrue(values.size() < 2 * clusterSize * cardinalityLimit);

    // Stable phase, general trend is established and only higher frequency values should
    // be reported with agreement for the most part across nodes
    values.clear();
    for (; t < finalEnd; ++t) {
      singleIteration.run();
      clock.setWallTime(t * 60000);
    }

    // It should have started converging on the frequent set and dropping the values with
    // too much churn even though this restricts it below the specified limit.
    Assertions.assertTrue(values.size() < 2 * numFrequentValues);
  }
}
