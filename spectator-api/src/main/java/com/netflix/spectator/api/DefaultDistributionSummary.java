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
package com.netflix.spectator.api;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Distribution summary implementation for the default registry. */
final class DefaultDistributionSummary implements DistributionSummary {

  private final Clock clock;
  private final Id id;
  private final AtomicLong count;
  private final AtomicLong totalAmount;

  private final Id countId;
  private final Id totalAmountId;

  /** Create a new instance. */
  DefaultDistributionSummary(Clock clock, Id id) {
    this.clock = clock;
    this.id = id;
    count = new AtomicLong(0L);
    totalAmount = new AtomicLong(0L);
    countId = id.withTag("statistic", "count");
    totalAmountId = id.withTag("statistic", "totalAmount");
  }

  @Override public Id id() {
    return id;
  }

  @Override public boolean hasExpired() {
    return false;
  }

  @Override public void record(long amount) {
    if (amount >= 0) {
      totalAmount.addAndGet(amount);
      count.incrementAndGet();
    }
  }

  @Override public Iterable<Measurement> measure() {
    final long now = clock.wallTime();
    final List<Measurement> ms = new ArrayList<>(2);
    ms.add(new Measurement(countId, now, count.get()));
    ms.add(new Measurement(totalAmountId, now, totalAmount.get()));
    return ms;
  }

  @Override public long count() {
    return count.get();
  }

  @Override public long totalAmount() {
    return totalAmount.get();
  }
}
