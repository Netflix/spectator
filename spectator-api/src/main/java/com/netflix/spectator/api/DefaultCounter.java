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
package com.netflix.spectator.api;

import com.netflix.spectator.impl.AtomicDouble;

import java.util.Collections;

/** Counter implementation for the default registry. */
final class DefaultCounter implements Counter {

  private final Clock clock;
  private final Id id;
  private final AtomicDouble count;

  /** Create a new instance. */
  DefaultCounter(Clock clock, Id id) {
    this.clock = clock;
    this.id = id;
    this.count = new AtomicDouble(0.0);
  }

  @Override public Id id() {
    return id;
  }

  @Override public boolean hasExpired() {
    return false;
  }

  @Override public Iterable<Measurement> measure() {
    long now = clock.wallTime();
    double v = count.get();
    return Collections.singleton(new Measurement(id, now, v));
  }

  @Override public void add(double amount) {
    if (Double.isFinite(amount) && amount > 0.0) {
      count.addAndGet(amount);
    }
  }

  @Override public double actualCount() {
    return count.get();
  }
}
