/*
 * Copyright 2014-2016 Netflix, Inc.
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

/** Gauge implementation for the default registry. */
class DefaultGauge implements Gauge {

  private final Clock clock;
  private final Id id;
  private final AtomicDouble value;

  /** Create a new instance. */
  DefaultGauge(Clock clock, Id id) {
    this.clock = clock;
    this.id = id;
    this.value = new AtomicDouble(Double.NaN);
  }

  @Override public Id id() {
    return id;
  }

  @Override public Iterable<Measurement> measure() {
    final Measurement m = new Measurement(id, clock.wallTime(), value());
    return Collections.singletonList(m);
  }

  @Override public boolean hasExpired() {
    return false;
  }

  @Override public void set(double v) {
    value.set(v);
  }

  @Override public double value() {
    return value.get();
  }
}
