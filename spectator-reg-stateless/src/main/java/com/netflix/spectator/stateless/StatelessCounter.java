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
package com.netflix.spectator.stateless;

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Statistic;
import com.netflix.spectator.impl.AtomicDouble;

import java.util.Collections;

/**
 * Counter that keeps track of the delta since the last time it was measured.
 */
class StatelessCounter extends StatelessMeter implements Counter {

  private final AtomicDouble count;
  private final Id stat;

  /** Create a new instance. */
  StatelessCounter(Id id, Clock clock, long ttl) {
    super(id, clock, ttl);
    count = new AtomicDouble(0.0);
    stat = id.withTag(Statistic.count).withTags(id.tags());
  }

  @Override public Iterable<Measurement> measure() {
    final double delta = count.getAndSet(0.0);
    if (delta > 0.0) {
      final Measurement m = new Measurement(stat, clock.wallTime(), delta);
      return Collections.singletonList(m);
    } else {
      return Collections.emptyList();
    }
  }

  @Override public void add(double amount) {
    if (amount > 0.0) {
      count.addAndGet(amount);
      updateLastModTime();
    }
  }

  @Override public double actualCount() {
    return count.get();
  }
}
