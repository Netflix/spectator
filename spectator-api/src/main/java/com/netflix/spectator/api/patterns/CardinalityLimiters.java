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
package com.netflix.spectator.api.patterns;

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Utils;
import com.netflix.spectator.impl.PatternMatcher;

import java.io.Serializable;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Predicate;
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

  /** Replacement value that is used if the values are rolled up. */
  public static final String AUTO_ROLLUP = "--auto-rollup--";

  /**
   * Order in descending order based on the count and then alphabetically based on the
   * key if there is a tie.
   */
  private static final Comparator<Map.Entry<String, AtomicLong>> FREQUENT_ENTRY_COMPARATOR =
      (a, b) -> {
        int countCmp = Long.compareUnsigned(b.getValue().get(), a.getValue().get());
        return countCmp != 0
            ? countCmp
            : a.getKey().compareTo(b.getKey());
      };

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

  /**
   * Rollup the values if the cardinality exceeds {@code n}. This limiter will leave the
   * values alone as long as the cardinality stays within the limit. After that all values
   * will get mapped to {@link #AUTO_ROLLUP}.
   *
   * @param n
   *     Maximum number of distinct values allowed for the lifetime of the limiter.
   * @return
   *     The input value if it is within the bounds or is selected. Otherwise map to
   *     {@link #AUTO_ROLLUP}.
   */
  public static Function<String, String> rollup(int n) {
    return new RollupLimiter(n);
  }

  /**
   * Restrict the cardinality independently for values which appear to be IP literals,
   * which are likely high-cardinality and low value, and Registered names (eg: Eureka
   * VIPs or DNS names), which are low-to-mid cardinality and high value. Values should
   * be RFC3986 3.2.2 hosts, behavior with non-host strings is not guaranteed.
   *
   * @param registeredNameLimiter
   *     The limiter applied to values which appear to be registered names.
   * @param ipLimiter
   *     The limiter applied to values which appear to be IP literals.
   *
   * @return
   *     The result according to the the matched limiter.
   */
  public static Function<String, String> registeredNameOrIp(Function<String, String> registeredNameLimiter,
                                                            Function<String, String> ipLimiter) {
    return new RegisteredNameOrIpLimiter(registeredNameLimiter, ipLimiter);
  }

  private static class FirstLimiter implements Function<String, String>, Serializable {

    private static final long serialVersionUID = 1L;

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

  private static class MostFrequentLimiter implements Function<String, String>, Serializable {

    private static final long serialVersionUID = 1L;

    // With a 10m refresh interval this is ~2h for it to return to normal if there
    // is a temporary window with lots of churn
    private static final int MAX_UPDATES = 12;

    private final ReentrantLock lock = new ReentrantLock();
    private final ConcurrentHashMap<String, AtomicLong> values = new ConcurrentHashMap<>();
    private final int n;
    private final Clock clock;

    private volatile Function<String, String> limiter;
    private volatile long limiterTimestamp;
    private volatile long cutoff;

    private int updatesWithHighChurn;

    MostFrequentLimiter(int n, Clock clock) {
      this.n = n;
      this.clock = clock;
      this.limiter = first(n);
      this.limiterTimestamp = clock.wallTime();
      this.cutoff = 0L;
      this.updatesWithHighChurn = 0;
    }

    private void updateCutoff() {
      long now = clock.wallTime();
      if (now - limiterTimestamp > REFRESH_INTERVAL && values.size() > n) {
        lock.lock();
        try {
          if (now - limiterTimestamp > REFRESH_INTERVAL) {
            limiterTimestamp = clock.wallTime();
            List<Map.Entry<String, AtomicLong>> sorted = values.entrySet()
                .stream()
                .sorted(FREQUENT_ENTRY_COMPARATOR)
                .collect(Collectors.toList());

            final long maxCount = sorted.get(0).getValue().get();

            Map.Entry<String, AtomicLong> min = sorted.get(Math.min(n - 1, sorted.size() - 1));
            final String minKey = min.getKey();
            final long minCount = min.getValue().get();
            final long delta = Math.max(minCount / 2L, 1L);

            final int numCloseToMin = (int) sorted.stream()
                .map(e -> e.getValue().get())
                .filter(v -> Math.abs(v - minCount) <= delta)
                .count();


            // Check for high churn
            long previousCutoff = cutoff;
            if (numCloseToMin > n) {
              if (maxCount - minCount <= maxCount / 2L) {
                // Max is close to min indicating more likelihood for churn with all values
                cutoff = Math.max(previousCutoff, maxCount + delta);
                updatesWithHighChurn = MAX_UPDATES;
              } else {
                // Try to cutoff the noisy tail without impacting higher frequency values
                cutoff = Math.max(previousCutoff, minCount + delta);
                updatesWithHighChurn += updatesWithHighChurn >= MAX_UPDATES ? 0 : 1;
              }
              sorted.stream().skip(10L * n).forEach(e -> values.remove(e.getKey()));

              // Update the limiter and ensure highest frequency values are preserved
              Function<String, String> newLimiter = first(n);
              sorted.stream().limit(n).forEach(e -> newLimiter.apply(e.getKey()));
              limiter = newLimiter;
            } else {
              cutoff = minCount - minCount / 10L;
              values
                  .entrySet()
                  .removeIf(e -> e.getValue().get() <= minCount && e.getKey().compareTo(minKey) > 0);

              // Decay the counts so new items will have a chance to catch up
              values.values().forEach(v -> v.set(v.get() - v.get() / 10L));

              // Replace the fallback limiter instance so new values will be allowed
              updatesWithHighChurn -= updatesWithHighChurn > 0 ? 1 : 0;
              if (updatesWithHighChurn == 0) {
                limiter = first(n);
              }
            }
          }
        } finally {
          lock.unlock();
        }
      }
    }

    @Override public String apply(String s) {
      AtomicLong count = Utils.computeIfAbsent(values, s, k -> new AtomicLong(0L));
      long num = count.incrementAndGet();
      if (num >= cutoff) {
        updateCutoff();
        // cutoff may have been updated, double check it would still make the cut
        String v = limiter.apply(s);
        return num >= cutoff ? v : OTHERS;
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

  private static class RollupLimiter implements Function<String, String>, Serializable {

    private static final long serialVersionUID = 1L;

    private final int n;
    private final Set<String> values;
    private final AtomicInteger count;

    private volatile boolean rollup;

    RollupLimiter(int n) {
      this.n = n;
      this.values = ConcurrentHashMap.newKeySet();
      this.count = new AtomicInteger();
      this.rollup = false;
    }

    @Override public String apply(String s) {
      if (rollup) {
        return AUTO_ROLLUP;
      }

      if (values.add(s) && count.incrementAndGet() > n) {
        rollup = true;
        values.clear();
        return AUTO_ROLLUP;
      } else {
        return s;
      }
    }

    @Override public String toString() {
      final String state = rollup ? "true" : values.size()  + " of " + n;
      return "RollupLimiter(" + state + ")";
    }
  }

  private static class RegisteredNameOrIpLimiter implements Function<String, String>, Serializable {

    private static final long serialVersionUID = 1L;

    //From RFC 3986 3.2.2, we can quickly identify IP literals (other than IPv4) from the first and last characters.
    private static final Predicate<String> IS_IP_LITERAL = s -> s.startsWith("[") && s.endsWith("]");

    //Approximating the logic from RFC 3986 3.2.2 without strictly enforcing the octect range
    private static final Predicate<String> IS_IPV4_ADDRESS = PatternMatcher.compile(
            "^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")::matches;
    private final Function<String, String> registeredNameLimiter;

    private final Function<String, String> ipLimiter;

    RegisteredNameOrIpLimiter(Function<String, String> registeredNameLimiter, Function<String, String> ipLimiter) {
      this.registeredNameLimiter = registeredNameLimiter;
      this.ipLimiter = ipLimiter;
    }
    @Override public String apply(String input) {
      if (IS_IP_LITERAL.test(input) || IS_IPV4_ADDRESS.test(input)) {
        return ipLimiter.apply(input);
      } else {
        return registeredNameLimiter.apply(input);
      }
    }

    @Override public String toString() {
      return "RegisteredNameOrIpLimiter(registeredNameLimiter=" + registeredNameLimiter.toString()
              + ", ipLimiter=" + ipLimiter.toString() + ")";
    }
  }
}
