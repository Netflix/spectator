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

import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;

import java.util.function.DoubleConsumer;

/**
 * Wraps a Micrometer Gauge to make it conform to the Spectator API.
 */
class MicrometerGauge extends MicrometerMeter implements Gauge {

  private final DoubleConsumer updateFunc;
  private final io.micrometer.core.instrument.Gauge impl;

  /** Create a new instance. */
  MicrometerGauge(Id id, DoubleConsumer updateFunc, io.micrometer.core.instrument.Gauge impl) {
    super(id);
    this.updateFunc = updateFunc;
    this.impl = impl;
  }

  @Override public void set(double v) {
    updateFunc.accept(v);
  }

  @Override public double value() {
    return impl.value();
  }

  @Override public Iterable<Measurement> measure() {
    return convert(impl.measure());
  }
}
