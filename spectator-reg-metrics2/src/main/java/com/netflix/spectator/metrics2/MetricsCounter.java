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
package com.netflix.spectator.metrics2;

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;

import java.util.Collections;

/** Counter implementation for the metrics2 registry. */
class MetricsCounter implements Counter {

  private final Clock clock;
  private final Id id;
  private final com.yammer.metrics.core.Meter impl;

  /** Create a new instance. */
  MetricsCounter(Clock clock, Id id, com.yammer.metrics.core.Meter impl) {
    this.clock = clock;
    this.id = id;
    this.impl = impl;
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
  public Iterable<Measurement> measure() {
    long now = clock.wallTime();
    long v = impl.count();
    return Collections.singleton(new Measurement(id, now, v));
  }

  /** {@inheritDoc} */
  @Override
  public void increment() {
    impl.mark();
  }

  /** {@inheritDoc} */
  @Override
  public void increment(long amount) {
    impl.mark(amount);
  }

  /** {@inheritDoc} */
  @Override
  public long count() {
    return impl.count();
  }
}
