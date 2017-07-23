/**
 * Copyright 2015 Netflix, Inc.
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
import com.netflix.servo.monitor.NumericMonitor;
import com.netflix.servo.monitor.StepCounter;
import com.netflix.spectator.api.AbstractTimer;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Statistic;
import com.netflix.spectator.api.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Timer implementation for the servo registry. */
class ServoTimer extends AbstractTimer implements ServoMeter {

  private static final double CNV_SECONDS = 1.0 / TimeUnit.SECONDS.toNanos(1L);
  private static final double CNV_SQUARES = CNV_SECONDS * CNV_SECONDS;

  private final Id id;

  // Local count so that we have more flexibility on servo counter impl without changing the
  // value returned by the {@link #count()} method.
  private final AtomicLong count;
  private final AtomicLong totalTime;

  private final StepCounter servoCount;
  private final StepCounter servoTotal;
  private final DoubleCounter servoTotalOfSquares;
  private final MaxGauge servoMax;

  private final AtomicLong lastUpdated;

  /** Create a new instance. */
  ServoTimer(ServoRegistry r, Id id) {
    super(r.clock());
    this.id = id;
    count = new AtomicLong(0L);
    totalTime = new AtomicLong(0L);

    ServoClock sc = new ServoClock(clock);
    servoCount = new StepCounter(r.toMonitorConfig(id.withTag(Statistic.count), null), sc);
    servoTotal = new StepCounter(r.toMonitorConfig(id.withTag(Statistic.totalTime), null), sc);
    servoTotalOfSquares = new DoubleCounter(
        r.toMonitorConfig(id.withTag(Statistic.totalOfSquares), null), sc);

    // Constructor that takes a clock param is not public
    servoMax = new MaxGauge(r.toMonitorConfig(id.withTag(Statistic.max), null));

    lastUpdated = new AtomicLong(0L);
  }

  @Override public void addMonitors(List<Monitor<?>> monitors) {
    monitors.add(servoCount);
    monitors.add(new FactorMonitor<>(servoTotal, CNV_SECONDS));
    monitors.add(new FactorMonitor<>(servoTotalOfSquares, CNV_SQUARES));
    monitors.add(new FactorMonitor<>(servoMax, CNV_SECONDS));
  }

  @Override public Id id() {
    return id;
  }

  @Override public boolean hasExpired() {
    long now = clock.wallTime();
    return now - lastUpdated.get() > ServoRegistry.EXPIRATION_TIME_MILLIS;
  }

  @Override public void record(long amount, TimeUnit unit) {
    if (amount >= 0) {
      final long nanos = unit.toNanos(amount);
      final double nanosSquared = (double) nanos * (double) nanos;
      totalTime.addAndGet(nanos);
      count.incrementAndGet();
      servoTotal.increment(nanos);
      servoTotalOfSquares.increment(nanosSquared);
      servoCount.increment();
      servoMax.update(nanos);
      lastUpdated.set(clock.wallTime());
    }
  }

  private Measurement newMeasurement(Tag t, long timestamp, Number n) {
    return new Measurement(id.withTag(t), timestamp, n.doubleValue());
  }

  private double getValue(NumericMonitor<?> m, double factor) {
    return m.getValue(0).doubleValue() * factor;
  }

  @Override public Iterable<Measurement> measure() {
    final long now = clock.wallTime();
    final List<Measurement> ms = new ArrayList<>(2);
    ms.add(newMeasurement(Statistic.count, now, servoCount.getValue(0)));
    ms.add(newMeasurement(Statistic.totalTime, now, getValue(servoTotal, CNV_SECONDS)));
    ms.add(newMeasurement(Statistic.totalOfSquares, now, getValue(servoTotalOfSquares, CNV_SQUARES)));
    ms.add(newMeasurement(Statistic.max, now, getValue(servoMax, CNV_SECONDS)));
    return ms;
  }

  @Override public long count() {
    return count.get();
  }

  @Override public long totalTime() {
    return totalTime.get();
  }
}
