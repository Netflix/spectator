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

import java.util.Collections;
import java.util.function.ToDoubleFunction;

/**
 * Gauge that is defined by executing a {@link ToDoubleFunction} on an object.
 */
class ObjectGauge<T> extends AbstractMeter<T> {

  private final ToDoubleFunction<T> f;

  /**
   * Create a gauge that samples the provided number for the value.
   *
   * @param clock
   *     Clock used for accessing the current time.
   * @param id
   *     Identifier for the gauge.
   * @param obj
   *     {@link Object} used to access the value.
   * @param f
   *     Function that is applied on the value for the number. The operation {@code f.apply(obj)}
   *     should be thread-safe.
   */
  ObjectGauge(Clock clock, Id id, T obj, ToDoubleFunction<T> f) {
    super(clock, id, obj);
    this.f = f;
  }

  @Override public Iterable<Measurement> measure() {
    return Collections.singleton(new Measurement(id, clock.wallTime(), value()));
  }

  /** Return the current value for evaluating `f` over `obj`. */
  double value() {
    final T obj = ref.get();
    return (obj == null) ? Double.NaN : f.applyAsDouble(obj);
  }
}
