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
package com.netflix.spectator.spark;

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DistributionSummary;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Statistic;

import java.util.ArrayList;
import java.util.List;

/**
 * Distribution summary that is mapped to two counters: total time and count.
 */
class SidecarDistributionSummary implements DistributionSummary {

  private final Id id;
  private final Counter count;
  private final Counter totalAmount;

  /** Create a new instance. */
  SidecarDistributionSummary(Clock clock, Id id) {
    this.id = id;
    count = new SidecarCounter(clock, id.withTag(Statistic.count));
    totalAmount = new SidecarCounter(clock, id.withTag(Statistic.totalAmount));
  }

  @Override public Id id() {
    return id;
  }

  @Override public void record(long amount) {
    count.increment();
    totalAmount.increment(amount);
  }

  @Override public long count() {
    return count.count();
  }

  @Override public long totalAmount() {
    return totalAmount.count();
  }

  @Override public Iterable<Measurement> measure() {
    List<Measurement> ms = new ArrayList<>();
    for (Measurement m : count.measure()) {
      ms.add(m);
    }
    for (Measurement m : totalAmount.measure()) {
      ms.add(m);
    }
    return ms;
  }

  @Override public boolean hasExpired() {
    return false;
  }
}
