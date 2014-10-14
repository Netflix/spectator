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
import com.netflix.spectator.api.DistributionSummary;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Distribution summary implementation for the servo registry. */
class ServoDistributionSummary implements DistributionSummary, ServoMeter {

  private final Clock clock;
  private final Id id;

  // Local count so that we have more flexibility on servo counter impl without changing the
  // value returned by the {@link #count()} method.
  private final AtomicLong count;
  private final AtomicLong totalAmount;

  private final StepCounter servoCount;
  private final StepCounter servoTotal;
  private final StepCounter servoTotalOfSquares;
  private final MaxGauge servoMax;

  /** Create a new instance. */
  ServoDistributionSummary(ServoRegistry r, Id id) {
    this.clock = r.clock();
    this.id = id;
    count = new AtomicLong(0L);
    totalAmount = new AtomicLong(0L);

    servoCount = new StepCounter(r.toMonitorConfig(id.withTag(Statistic.count)));
    servoTotal = new StepCounter(r.toMonitorConfig(id.withTag(Statistic.totalAmount)));
    servoTotalOfSquares = new StepCounter(
        r.toMonitorConfig(id.withTag(Statistic.totalOfSquares)));
    servoMax = new MaxGauge(r.toMonitorConfig(id.withTag(Statistic.max)));
  }

  @Override public void addMonitors(List<Monitor<?>> monitors) {
    monitors.add(servoCount);
    monitors.add(servoTotal);
    monitors.add(servoTotalOfSquares);
    monitors.add(servoMax);
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
      servoTotal.increment(amount);
      servoTotalOfSquares.increment(amount * amount);
      servoCount.increment();
      servoMax.update(amount);
    }
  }

  @Override public Iterable<Measurement> measure() {
    final long now = clock.wallTime();
    final List<Measurement> ms = new ArrayList<>(2);
    ms.add(new Measurement(id.withTag(Statistic.count), now, count.get()));
    ms.add(new Measurement(id.withTag(Statistic.totalAmount), now, totalAmount.get()));
    return ms;
  }

  @Override public long count() {
    return count.get();
  }

  @Override public long totalAmount() {
    return totalAmount.get();
  }
}
