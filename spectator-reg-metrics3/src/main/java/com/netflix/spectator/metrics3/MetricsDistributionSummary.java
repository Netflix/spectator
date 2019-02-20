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
package com.netflix.spectator.metrics3;

import com.codahale.metrics.Snapshot;
import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.DistributionSummary;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;

/** Distribution summary implementation for the metric3 registry. */
class MetricsDistributionSummary implements DistributionSummary {

  private final Clock clock;
  private final Id id;
  private final com.codahale.metrics.Histogram impl;
  private final AtomicLong totalAmount;

  /** Create a new instance. */
  MetricsDistributionSummary(Clock clock, Id id, com.codahale.metrics.Histogram impl) {
    this.clock = clock;
    this.id = id;
    this.impl = impl;
    this.totalAmount = new AtomicLong(0L);
  }

  @Override public Id id() {
    return id;
  }

  @Override public boolean hasExpired() {
    return false;
  }

  @Override public void record(long amount) {
    impl.update(amount);
  }

  @Override public Iterable<Measurement> measure() {
    final long now = clock.wallTime();
    final Snapshot snapshot = impl.getSnapshot();
    return Collections.singleton(new Measurement(id, now, snapshot.getMean()));
  }

  @Override public long count() {
    return impl.getCount();
  }

  @Override public long totalAmount() {
    return totalAmount.get();
  }
}
