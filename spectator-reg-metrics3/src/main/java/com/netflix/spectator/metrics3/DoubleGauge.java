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
package com.netflix.spectator.metrics3;

import com.codahale.metrics.Gauge;
import com.netflix.spectator.impl.AtomicDouble;

/** Metrics3 gauge type based on an {@link AtomicDouble}. */
class DoubleGauge implements Gauge<Double> {

  private AtomicDouble value = new AtomicDouble(Double.NaN);

  /** Update the value. */
  void set(double v) {
    value.set(v);
  }

  @Override public Double getValue() {
    return value.get();
  }
}
