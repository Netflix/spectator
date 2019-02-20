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
package com.netflix.spectator.micrometer;

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;

/**
 * Wraps a Micrometer Counter to make it conform to the Spectator API.
 */
class MicrometerCounter extends MicrometerMeter implements Counter {

  private final io.micrometer.core.instrument.Counter impl;

  /** Create a new instance. */
  MicrometerCounter(Id id, io.micrometer.core.instrument.Counter impl) {
    super(id);
    this.impl = impl;
  }

  @Override public Iterable<Measurement> measure() {
    return convert(impl.measure());
  }

  @Override public void add(double amount) {
    impl.increment(amount);
  }

  @Override public double actualCount() {
    return impl.count();
  }
}
