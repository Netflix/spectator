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
package com.netflix.spectator.servo;

import com.netflix.servo.monitor.MaxGauge;
import com.netflix.servo.monitor.Monitor;
import com.netflix.servo.monitor.StepCounter;
import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Timer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Timer implementation for the servo registry. */
class ServoTimer implements Timer, ServoMeter {

  private final Clock clock;
  private final Id id;

  // Local count so that we have more flexibility on servo counter impl without changing the
  // value returned by the {@link #count()} method.
  private final AtomicLong count;
  private final AtomicLong totalTime;

  private final StepCounter servoCount;
  private final StepCounter servoTotal;
  private final StepCounter servoTotalOfSquares;
  private final MaxGauge servoMax;

  /** Create a new instance. */
  ServoTimer(ServoRegistry r, Id id) {
    this.clock = r.clock();
    this.id = id;
    count = new AtomicLong(0L);
    totalTime = new AtomicLong(0L);

    servoCount = new StepCounter(r.toMonitorConfig(id.withTag(Statistic.count)));
    servoTotal = new StepCounter(r.toMonitorConfig(id.withTag(Statistic.totalTime)));
    servoTotalOfSquares = new StepCounter(
        r.toMonitorConfig(id.withTag(Statistic.totalOfSquares)));
    servoMax = new MaxGauge(r.toMonitorConfig(id.withTag(Statistic.max)));
  }

  @Override public void addMonitors(List<Monitor<?>> monitors) {
    final double cnvSeconds = 1.0 / TimeUnit.SECONDS.toNanos(1L);
    final double cnvSquares = cnvSeconds * cnvSeconds;
    monitors.add(servoCount);
    monitors.add(new FactorMonitor<>(servoTotal, cnvSeconds));
    monitors.add(new FactorMonitor<>(servoTotalOfSquares, cnvSquares));
    monitors.add(new FactorMonitor<>(servoMax, cnvSeconds));
  }

  @Override public Id id() {
    return id;
  }

  @Override public boolean hasExpired() {
    return false;
  }

  @Override public void record(long amount, TimeUnit unit) {
    if (amount >= 0) {
      final long nanos = unit.toNanos(amount);
      totalTime.addAndGet(nanos);
      count.incrementAndGet();
      servoTotal.increment(nanos);
      servoTotalOfSquares.increment(nanos * nanos);
      servoCount.increment();
      servoMax.update(nanos);
    }
  }

  @Override public Iterable<Measurement> measure() {
    final long now = clock.wallTime();
    final List<Measurement> ms = new ArrayList<>(2);
    ms.add(new Measurement(id.withTag(Statistic.count), now, count.get()));
    ms.add(new Measurement(id.withTag(Statistic.totalTime), now, totalTime.get()));
    return ms;
  }

  @Override public <T> T record(Callable<T> f) throws Exception {
    final long s = clock.monotonicTime();
    try {
      return f.call();
    } finally {
      final long e = clock.monotonicTime();
      record(e - s, TimeUnit.NANOSECONDS);
    }
  }

  @Override public void record(Runnable f) {
    final long s = clock.monotonicTime();
    try {
      f.run();
    } finally {
      final long e = clock.monotonicTime();
      record(e - s, TimeUnit.NANOSECONDS);
    }
  }

  @Override public long count() {
    return count.get();
  }

  @Override public long totalTime() {
    return totalTime.get();
  }
}
