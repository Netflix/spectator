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
package com.netflix.spectator.atlas;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.impl.SwapMeter;

/**
 * <p><b>Experimental:</b> This type may be removed in a future release.</p>
 *
 * Wraps another gauge allowing the underlying type to be swapped.
 */
final class SwapMaxGauge implements MaxGauge, SwapMeter<MaxGauge> {

  private final AtlasRegistry registry;
  private final Id id;
  private volatile MaxGauge underlying;

  /** Create a new instance. */
  SwapMaxGauge(AtlasRegistry registry, Id id, MaxGauge underlying) {
    this.registry = registry;
    this.id = id;
    this.underlying = underlying;
  }

  @Override public Id id() {
    return id;
  }

  @Override public Iterable<Measurement> measure() {
    return get().measure();
  }

  @Override public boolean hasExpired() {
    MaxGauge g = underlying;
    return g == null || g.hasExpired();
  }

  @Override public void set(double value) {
    get().set(value);
  }

  @Override public double value() {
    return get().value();
  }

  @Override public void set(MaxGauge g) {
    underlying = g;
  }

  @Override public MaxGauge get() {
    MaxGauge g = underlying;
    if (g == null) {
      g = unwrap(registry.maxGauge(id));
      underlying = g;
    }
    return g;
  }

  private MaxGauge unwrap(MaxGauge g) {
    MaxGauge tmp = g;
    while (tmp instanceof SwapMaxGauge) {
      tmp = ((SwapMaxGauge) tmp).get();
    }
    return tmp;
  }
}
