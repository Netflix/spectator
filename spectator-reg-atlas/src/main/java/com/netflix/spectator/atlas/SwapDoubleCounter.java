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
 * Wraps a double counter to allow the implementation to be swapped out.
 */
final class SwapDoubleCounter implements DoubleCounter, SwapMeter<DoubleCounter> {

  private final AtlasRegistry registry;
  private final Id id;
  private volatile DoubleCounter underlying;

  /** Create a new instance. */
  SwapDoubleCounter(AtlasRegistry registry, Id id, DoubleCounter underlying) {
    this.registry = registry;
    this.id = id;
    this.underlying = underlying;
  }

  @Override public Id id() {
    return id;
  }

  @Override public boolean hasExpired() {
    DoubleCounter c = underlying;
    return c == null || c.hasExpired();
  }

  @Override public void add(double amount) {
    get().add(amount);
  }

  @Override public double actualCount() {
    return get().actualCount();
  }

  @Override public Iterable<Measurement> measure() {
    return get().measure();
  }

  @Override public void set(DoubleCounter c) {
    underlying = c;
  }

  @Override public DoubleCounter get() {
    DoubleCounter c = underlying;
    if (c == null) {
      c = unwrap(registry.doubleCounter(id));
      underlying = c;
    }
    return c;
  }

  private DoubleCounter unwrap(DoubleCounter c) {
    DoubleCounter tmp = c;
    while (tmp instanceof SwapDoubleCounter) {
      tmp = ((SwapDoubleCounter) tmp).get();
    }
    return tmp;
  }
}
