/*
 * Copyright 2014-2026 Netflix, Inc.
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
package com.netflix.spectator.api.histogram;

import com.netflix.spectator.api.AbstractRegistry;
import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.DistributionSummary;
import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Timer;

import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Registry with timers and distribution summaries that increment the count for negative
 * amounts, similar to the Atlas registry. Used to verify that the percentile meters drop
 * negative amounts before updating the underlying meter rather than relying on the
 * underlying meter to ignore them.
 */
final class NegativeCountingRegistry extends AbstractRegistry {

  private final DefaultRegistry delegate;

  NegativeCountingRegistry() {
    super(Clock.SYSTEM);
    this.delegate = new DefaultRegistry(Clock.SYSTEM);
  }

  @Override protected Counter newCounter(Id id) {
    return delegate.counter(id);
  }

  @Override protected DistributionSummary newDistributionSummary(Id id) {
    return new CountingSummary(id);
  }

  @Override protected Timer newTimer(Id id) {
    return new CountingTimer(id);
  }

  @Override protected Gauge newGauge(Id id) {
    return delegate.gauge(id);
  }

  @Override protected Gauge newMaxGauge(Id id) {
    return delegate.maxGauge(id);
  }

  private static final class CountingTimer implements Timer {
    private final Id id;
    private final AtomicLong count = new AtomicLong();
    private final AtomicLong totalTime = new AtomicLong();

    CountingTimer(Id id) {
      this.id = id;
    }

    @Override public Id id() {
      return id;
    }

    @Override public Iterable<Measurement> measure() {
      return Collections.emptyList();
    }

    @Override public boolean hasExpired() {
      return false;
    }

    @Override public void record(long amount, TimeUnit unit) {
      count.incrementAndGet();
      if (amount > 0L) {
        totalTime.addAndGet(unit.toNanos(amount));
      }
    }

    @Override public void record(long[] amounts, int n, TimeUnit unit) {
      // Like AtlasTimer, increments the count by the number of entries
      final int limit = Math.max(0, Math.min(amounts.length, n));
      count.addAndGet(limit);
      for (int i = 0; i < limit; ++i) {
        if (amounts[i] > 0L) {
          totalTime.addAndGet(unit.toNanos(amounts[i]));
        }
      }
    }

    @Override public long count() {
      return count.get();
    }

    @Override public long totalTime() {
      return totalTime.get();
    }
  }

  private static final class CountingSummary implements DistributionSummary {
    private final Id id;
    private final AtomicLong count = new AtomicLong();
    private final AtomicLong totalAmount = new AtomicLong();

    CountingSummary(Id id) {
      this.id = id;
    }

    @Override public Id id() {
      return id;
    }

    @Override public Iterable<Measurement> measure() {
      return Collections.emptyList();
    }

    @Override public boolean hasExpired() {
      return false;
    }

    @Override public void record(long amount) {
      count.incrementAndGet();
      if (amount > 0L) {
        totalAmount.addAndGet(amount);
      }
    }

    @Override public void record(long[] amounts, int n) {
      // Like AtlasDistributionSummary, increments the count by the number of entries
      final int limit = Math.max(0, Math.min(amounts.length, n));
      count.addAndGet(limit);
      for (int i = 0; i < limit; ++i) {
        if (amounts[i] > 0L) {
          totalAmount.addAndGet(amounts[i]);
        }
      }
    }

    @Override public long count() {
      return count.get();
    }

    @Override public long totalAmount() {
      return totalAmount.get();
    }
  }
}
