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
import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Statistic;
import com.netflix.spectator.impl.AtomicDouble;

import java.util.Collections;

/**
 * Max gauge that keeps track of the maximum value since the last time it was measured.
 */
class StatelessMaxGauge extends StatelessMeter implements Gauge {

  private final AtomicDouble value;
  private final Id stat;

  /** Create a new instance. */
  StatelessMaxGauge(Id id, Clock clock, long ttl) {
    super(id, clock, ttl);
    value = new AtomicDouble(0.0);
    stat = id.withTag(Statistic.max).withTags(id.tags());
  }

  @Override public void set(double v) {
    if (v > 0.0) {
      value.max(v);
      updateLastModTime();
    }
  }

  @Override public double value() {
    return value.get();
  }

  @Override public Iterable<Measurement> measure() {
    final double delta = value.getAndSet(0.0);
    if (delta > 0.0) {
      final Measurement m = new Measurement(stat, clock.wallTime(), delta);
      return Collections.singletonList(m);
    } else {
      return Collections.emptyList();
    }
  }
}
