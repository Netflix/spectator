/*
 * Copyright 2014-2018 Netflix, Inc.
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

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;

import java.util.Collections;

/** Counter implementation for the metric3 registry. */
class MetricsCounter implements Counter {

  private final Clock clock;
  private final Id id;
  private final com.codahale.metrics.Meter impl;

  /** Create a new instance. */
  MetricsCounter(Clock clock, Id id, com.codahale.metrics.Meter impl) {
    this.clock = clock;
    this.id = id;
    this.impl = impl;
  }

  @Override public Id id() {
    return id;
  }

  @Override public boolean hasExpired() {
    return false;
  }

  @Override public Iterable<Measurement> measure() {
    long now = clock.wallTime();
    long v = impl.getCount();
    return Collections.singleton(new Measurement(id, now, v));
  }

  @Override public void add(double amount) {
    impl.mark((long) amount);
  }

  @Override public double actualCount() {
    return impl.getCount();
  }
}
