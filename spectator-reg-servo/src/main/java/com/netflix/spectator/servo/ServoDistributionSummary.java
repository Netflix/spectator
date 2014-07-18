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

import com.netflix.servo.monitor.BasicDistributionSummary;
import com.netflix.servo.monitor.Monitor;
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
  private final ServoId id;
  private final BasicDistributionSummary impl;

  // Local count so that we have more flexibility on servo counter impl without changing the
  // value returned by the {@link #count()} method.
  private final AtomicLong count;
  private final AtomicLong totalAmount;

  private final Id countId;
  private final Id totalAmountId;

  /** Create a new instance. */
  ServoDistributionSummary(Clock clock, ServoId id, BasicDistributionSummary impl) {
    this.clock = clock;
    this.id = id;
    this.impl = impl;
    this.count = new AtomicLong(0L);
    this.totalAmount = new AtomicLong(0L);
    countId = id.withTag("statistic", "count");
    totalAmountId = id.withTag("statistic", "totalAmount");
  }

  /** {@inheritDoc} */
  @Override
  public Monitor<?> monitor() {
    return impl;
  }

  /** {@inheritDoc} */
  @Override
  public Id id() {
    return id;
  }

  /** {@inheritDoc} */
  @Override
  public boolean hasExpired() {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public void record(long amount) {
    impl.record(amount);
    totalAmount.addAndGet(amount);
    count.incrementAndGet();
  }

  /** {@inheritDoc} */
  @Override
  public Iterable<Measurement> measure() {
    final long now = clock.wallTime();
    final List<Measurement> ms = new ArrayList<>(2);
    ms.add(new Measurement(countId, now, count.get()));
    ms.add(new Measurement(totalAmountId, now, totalAmount.get()));
    return ms;
  }

  /** {@inheritDoc} */
  @Override
  public long count() {
    return count.get();
  }

  /** {@inheritDoc} */
  @Override
  public long totalAmount() {
    return totalAmount.get();
  }
}
