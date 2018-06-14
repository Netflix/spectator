/*
 * Copyright 2014-2018 Netflix, Inc.
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
package com.netflix.spectator.api.patterns;

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Utils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Helper functions to help manage the cardinality of tag values. This should be used
 * anywhere you cannot guarantee that the tag values being used are strictly bounded.
 * There is support for two different modes, 1) selecting the first N values that are
 * seen, or 2) selecting the most frequent N values that are seen.
 *
 * <p>Sample usage:</p>
 *
 * <pre>
 * class WebServer {
 *
 *   // Limiter instance, should be shared for all uses of that
 *   // tag value
 *   private final Function&lt;String, String&gt; pathLimiter =
 *     CardinalityLimiters.mostFrequent(10);
 *
 *   private final Registry registry;
 *   private final Id baseId;
 *
 *   public WebServer(Registry registry) {
 *     this.registry = registry;
 *     this.baseId = registry.createId("server.requestCount");
 *   }
 *
 *   public Response handleRequest(Request req) {
 *     Response res = doSomething(req);
 *
 *     // Update metrics, use limiter to restrict the set of values
 *     // for the path and avoid an explosion
 *     String pathValue = pathLimiter.apply(req.getPath());
 *     Id id = baseId
 *       .withTag("path", pathValue)
 *       .withTag("status", res.getStatus());
 *     registry.counter(id).increment();
 *   }
 * }
 * </pre>
 */
public final class CardinalityLimiters {

  /** How often to refresh the frequencies. */
  private static final long REFRESH_INTERVAL = 10 * 60000;

  /** Bound on how large N can be for the limiter. */
  private static final int MAX_LIMIT = 100;

  /** Replacement value that is used if the number of values exceeds the limit. */
  public static final String OTHERS = "--others--";

  private CardinalityLimiters() {
  }

  /**
   * Restrict the cardinality of the input to the first {@code n} values that are seen.
   *
   * @param n
   *     Number of values to select.
   * @return
   *     The input value if it is within the bounds or is selected. Otherwise map to
   *     {@link #OTHERS}.
   */
  public static Function<String, String> first(int n) {
    return new FirstLimiter(Math.min(n, MAX_LIMIT));
  }

  /**
   * Restrict the cardinality of the input to the top {@code n} values based on the
   * frequency of the lookup. This limiter is useful when the frequency of the values
   * is non-uniform and the most common are the most important. If there are many
   * values with roughly the same frequency, then it will use a {@link #first(int)}
   * limiter to keep the number of values within bounds.
   *
   * <p>The limiter will adjust to changes in the frequency over time, but it must also
   * protect against high rates of churn in the values. Keep in mind that this can cause
   * a delay in new high frequency value being used instead of being grouped as part of
   * {@link #OTHERS}.</p>
   *
   * @param n
   *     Number of values to select.
   * @return
   *     The input value if it is within the bounds or is selected. Otherwise map to
   *     {@link #OTHERS}.
   */
  public static Function<String, String> mostFrequent(int n) {
    return mostFrequent(n, Clock.SYSTEM);
  }

  /**
   * Allows the clock to be specified for testing. See {@link #mostFrequent(int)} for
   * details on the usage.
   */
  static Function<String, String> mostFrequent(int n, Clock clock) {
    return new MostFrequentLimiter(Math.min(n, MAX_LIMIT), clock);
  }

  private static class FirstLimiter implements Function<String, String> {
    private final ReentrantLock lock = new ReentrantLock();
    private final ConcurrentHashMap<String, String> values = new ConcurrentHashMap<>();
    private final AtomicInteger remaining;

    FirstLimiter(int n) {
      remaining = new AtomicInteger(n);
    }

    private void add(String s) {
      if (remaining.get() > 0) {
        // Lock to keep hashmap consistent with counter for remaining
        lock.lock();
        try {
          if (remaining.get() > 0) {
            values.put(s, s);
            remaining.decrementAndGet();
          }
        } finally {
          lock.unlock();
        }
      }
    }

    @Override public String apply(String s) {
      if (remaining.get() <= 0) {
        return values.getOrDefault(s, OTHERS);
      } else {
        String v = values.get(s);
        if (v == null) {
          add(s);
          v = values.getOrDefault(s, OTHERS);
        }
        return v;
      }
    }

    @Override public String toString() {
      final String vs = values.keySet()
          .stream()
          .sorted()
          .collect(Collectors.joining(","));
      return "FirstLimiter(" + vs + ")";
    }
  }

  private static class MostFrequentLimiter implements Function<String, String> {
    private final ReentrantLock lock = new ReentrantLock();
    private final ConcurrentHashMap<String, AtomicLong> values = new ConcurrentHashMap<>();
    private final int n;
    private final Clock clock;

    private volatile Function<String, String> limiter;
    private volatile long limiterTimestamp;
    private volatile long cutoff;

    MostFrequentLimiter(int n, Clock clock) {
      this.n = n;
      this.clock = clock;
      this.limiter = first(n);
      this.limiterTimestamp = clock.wallTime();
      this.cutoff = 0L;
    }

    private synchronized void updateCutoff() {
      long now = clock.wallTime();
      if (now - limiterTimestamp > REFRESH_INTERVAL) {
        lock.lock();
        try {
          if (now - limiterTimestamp > REFRESH_INTERVAL) {
            limiterTimestamp = clock.wallTime();
            long min = values.values()
                .stream()
                .map(AtomicLong::get)
                .sorted((a, b) -> Long.compareUnsigned(b, a))
                .limit(n)
                .min(Long::compareUnsigned)
                .orElseGet(() -> 0L);
            limiter = first(n);

            // Remove less frequent items from the map to avoid a memory leak if unique
            // ids are used
            long dropCutoff = Math.max(min / 2, 1);
            values.entrySet().removeIf(e -> e.getValue().get() <= dropCutoff);

            // Reset the counts so new items will have a chance to catch up, use 1 instead of 0 so
            // older entries have a slight bias
            values.values().forEach(v -> v.set(1L));
            cutoff = 1L;
          }
        } finally {
          lock.unlock();
        }
      }
    }

    @Override public String apply(String s) {
      AtomicLong count = Utils.computeIfAbsent(values, s, k -> new AtomicLong(0));
      long num = count.incrementAndGet();
      if (num >= cutoff) {
        updateCutoff();
        return limiter.apply(s);
      } else {
        return OTHERS;
      }
    }

    @Override public String toString() {
      final String vs = values.entrySet()
          .stream()
          .filter(e -> e.getValue().get() >= cutoff)
          .map(e -> "(" + e.getKey() + "," + e.getValue() + ")")
          .sorted()
          .collect(Collectors.joining(","));
      return "MostFrequentLimiter(" + cutoff + "," + limiter + ",values=[" + vs + "])";
    }

  }
}
