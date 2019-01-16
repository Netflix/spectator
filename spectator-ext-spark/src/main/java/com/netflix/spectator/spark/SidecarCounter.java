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
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.impl.AtomicDouble;

import java.util.Collections;

/**
 * Counter that tracks the delta since the last measurement was taken.
 */
class SidecarCounter implements Counter {

  private final Clock clock;
  private final Id id;
  private final AtomicDouble value;

  /** Create a new instance. */
  SidecarCounter(Clock clock, Id id) {
    this.clock = clock;
    this.id = id.withTag(DataType.COUNTER);
    this.value = new AtomicDouble(0.0);
  }

  @Override public Id id() {
    return id;
  }

  @Override public void add(double amount) {
    if (Double.isFinite(amount) && amount > 0.0) {
      value.addAndGet(amount);
    }
  }

  @Override public double actualCount() {
    return value.get();
  }

  @Override public Iterable<Measurement> measure() {
    Measurement m = new Measurement(id, clock.wallTime(), value.getAndSet(0L));
    return Collections.singletonList(m);
  }

  @Override public boolean hasExpired() {
    return false;
  }
}
