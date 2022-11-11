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
package com.netflix.spectator.api;

import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ExpiringRegistry extends AbstractRegistry {

  public ExpiringRegistry(Clock clock) {
    super(clock);
  }

  @Override protected Counter newCounter(Id id) {
    return new Counter() {
      private final long creationTime = clock().wallTime();
      private double count = 0;

      @Override public void add(double amount) {
        count += amount;
      }

      @Override public double actualCount() {
        return count;
      }

      @Override public Id id() {
        return id;
      }

      @Override public Iterable<Measurement> measure() {
        return Collections.emptyList();
      }

      @Override public boolean hasExpired() {
        return clock().wallTime() > creationTime;
      }

      @Override public BatchUpdater batchUpdater(int batchSize) {
        return new CounterUpdater();
      }
    };
  }

  private static class CounterUpdater implements Counter.BatchUpdater, Consumer<Supplier<Counter>> {

    private Supplier<Counter> counterSupplier;
    private double sum;

    @Override public void add(double amount) {
      sum += amount;
    }

    @Override public void flush() {
      counterSupplier.get().add(sum);
      sum = 0.0;
    }

    @Override public void close() throws Exception {
      flush();
    }

    @Override public void accept(Supplier<Counter> counterSupplier) {
      this.counterSupplier = counterSupplier;
    }
  }

  @Override protected DistributionSummary newDistributionSummary(Id id) {
    return new DistributionSummary() {
      private final long creationTime = clock().wallTime();
      private long count = 0;

      @Override public void record(long amount) {
        ++count;
      }

      @Override public long count() {
        return count;
      }

      @Override public long totalAmount() {
        return 0;
      }

      @Override public Id id() {
        return id;
      }

      @Override public Iterable<Measurement> measure() {
        return Collections.emptyList();
      }

      @Override public boolean hasExpired() {
        return clock().wallTime() > creationTime;
      }

      @Override public BatchUpdater batchUpdater(int batchSize) {
        return new DistributionSummaryUpdater();
      }
    };
  }

  private static class DistributionSummaryUpdater
      implements DistributionSummary.BatchUpdater, Consumer<Supplier<DistributionSummary>> {

    private Supplier<DistributionSummary> distSummarySupplier;

    @Override public void record(long amount) {
      distSummarySupplier.get().record(amount);
    }

    @Override public void flush() {
    }

    @Override public void close() throws Exception {
    }

    @Override public void accept(Supplier<DistributionSummary> distSummarySupplier) {
      this.distSummarySupplier = distSummarySupplier;
    }
  }

  @Override protected Timer newTimer(Id id) {
    return new AbstractTimer(clock()) {
      private final long creationTime = clock().wallTime();
      private long count = 0;

      @Override public void record(long amount, TimeUnit unit) {
        ++count;
      }

      @Override public long count() {
        return count;
      }

      @Override public long totalTime() {
        return 0;
      }

      @Override public Id id() {
        return id;
      }

      @Override public Iterable<Measurement> measure() {
        return Collections.emptyList();
      }

      @Override public boolean hasExpired() {
        return clock().wallTime() > creationTime;
      }

      @Override public BatchUpdater batchUpdater(int batchSize) {
        return new TimerUpdater();
      }
    };
  }

  private static class TimerUpdater implements Timer.BatchUpdater, Consumer<Supplier<Timer>> {

    private Supplier<Timer> timerSupplier;

    @Override public void record(long amount, TimeUnit unit) {
      timerSupplier.get().record(amount, unit);
    }

    @Override public void flush() {
    }

    @Override public void close() throws Exception {
    }

    @Override public void accept(Supplier<Timer> timerSupplier) {
      this.timerSupplier = timerSupplier;
    }
  }

  @Override protected Gauge newGauge(Id id) {
    return new Gauge() {
      private final long creationTime = clock().wallTime();
      private double value = 0.0;

      @Override public void set(double v) {
        value = v;
      }

      @Override public double value() {
        return value;
      }

      @Override public Id id() {
        return id;
      }

      @Override public Iterable<Measurement> measure() {
        return Collections.emptyList();
      }

      @Override public boolean hasExpired() {
        return clock().wallTime() > creationTime;
      }
    };
  }

  @Override protected Gauge newMaxGauge(Id id) {
    return newGauge(id);
  }

  @Override public void removeExpiredMeters() {
    super.removeExpiredMeters();
  }
}
