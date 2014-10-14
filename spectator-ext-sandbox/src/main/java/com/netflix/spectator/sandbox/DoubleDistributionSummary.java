/**
 * Copyright 2014 Netflix, Inc.
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
package com.netflix.spectator.sandbox;


import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Meter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Experiment for supporting a distribution summary type that accepts floating point values instead
 * of just long values.
 */
public class DoubleDistributionSummary implements Meter {

  private static final long ZERO = Double.doubleToLongBits(0.0);

  private final Clock clock;
  private final Id id;

  private final long resetFreq;
  private final AtomicLong lastResetTime;

  private final AtomicLong count;
  private final AtomicLong totalAmount;
  private final AtomicLong totalOfSquares;
  private final AtomicLong max;

  private final Id countId;
  private final Id totalAmountId;
  private final Id totalOfSquaresId;
  private final Id maxId;

  /** Create a new instance. */
  DoubleDistributionSummary(Clock clock, Id id, long resetFreq) {
    this.clock = clock;
    this.id = id;
    this.resetFreq = resetFreq;
    lastResetTime = new AtomicLong(clock.wallTime());
    count = new AtomicLong(0L);
    totalAmount = new AtomicLong(ZERO);
    totalOfSquares = new AtomicLong(ZERO);
    max = new AtomicLong(ZERO);
    countId = id.withTag("statistic", "count");
    totalAmountId = id.withTag("statistic", "totalAmount");
    totalOfSquaresId = id.withTag("statistic", "totalOfSquares");
    maxId = id.withTag("statistic", "max");
  }

  private void add(AtomicLong num, double amount) {
    long v;
    double d;
    long next;
    do {
      v = num.get();
      d = Double.longBitsToDouble(v);
      next = Double.doubleToLongBits(d + amount);
    } while (!num.compareAndSet(v, next));
  }

  private void max(AtomicLong num, double amount) {
    long n = Double.doubleToLongBits(amount);
    long v;
    double d;
    do {
      v = num.get();
      d = Double.longBitsToDouble(v);
    } while (amount > d && !num.compareAndSet(v, n));
  }

  private double toRateLong(AtomicLong num, long deltaMillis, boolean reset) {
    final long v = reset ? num.getAndSet(0L) : num.get();
    final double delta = deltaMillis / 1000.0;
    return v / delta;
  }

  private double toRateDouble(AtomicLong num, long deltaMillis, boolean reset) {
    final long v = reset ? num.getAndSet(ZERO) : num.get();
    final double delta = deltaMillis / 1000.0;
    return Double.longBitsToDouble(v) / delta;
  }

  private double toDouble(AtomicLong num, boolean reset) {
    final long v = reset ? num.getAndSet(ZERO) : num.get();
    return Double.longBitsToDouble(v);
  }

  @Override public Id id() {
    return id;
  }

  @Override public boolean hasExpired() {
    return false;
  }

  @Override public Iterable<Measurement> measure() {
    final long now = clock.wallTime();
    final long prev = lastResetTime.get();
    final long delta = now - prev;
    final boolean reset = delta > resetFreq;

    if (reset) {
      lastResetTime.set(now);
    }

    final List<Measurement> ms = new ArrayList<>(3);
    if (delta > 1000L) {
      ms.add(new Measurement(countId, now, toRateLong(count, delta, reset)));
      ms.add(new Measurement(totalAmountId, now, toRateDouble(totalAmount, delta, reset)));
      ms.add(new Measurement(totalOfSquaresId, now, toRateDouble(totalOfSquares, delta, reset)));
      ms.add(new Measurement(maxId, now, toDouble(max, reset)));
    }
    return ms;
  }

  /**
   * Updates the statistics kept by the summary with the specified amount.
   *
   * @param amount
   *     Amount for an event being measured. For example, if the size in bytes of responses
   *     from a server. If the amount is less than 0 the value will be dropped.
   */
  public void record(double amount) {
    if (amount >= 0.0) {
      add(totalAmount, amount);
      add(totalOfSquares, amount * amount);
      max(max, amount);
      count.incrementAndGet();
    }
  }

  /** The number of times that record has been called since this timer was created. */
  public long count() {
    return count.get();
  }

  /** The total amount of all recorded events since this summary was created. */
  public double totalAmount() {
    return Double.longBitsToDouble(totalAmount.get());
  }
}
