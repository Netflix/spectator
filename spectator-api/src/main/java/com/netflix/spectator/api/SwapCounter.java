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
package com.netflix.spectator.api;

import com.netflix.spectator.impl.SwapMeter;

/** Wraps another counter allowing the underlying type to be swapped. */
final class SwapCounter implements Counter, SwapMeter<Counter> {

  private final Registry registry;
  private final Id id;
  private volatile Counter underlying;

  /** Create a new instance. */
  SwapCounter(Registry registry, Id id, Counter underlying) {
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
    Counter c = underlying;
    return c == null || c.hasExpired();
  }

  @Override public void increment() {
    get().increment();
  }

  @Override public void increment(long amount) {
    get().increment(amount);
  }

  @Override public long count() {
    return get().count();
  }

  @Override public void set(Counter c) {
    underlying = c;
  }

  @Override public Counter get() {
    Counter c = underlying;
    if (c == null) {
      c = unwrap(registry.counter(id));
      underlying = c;
    }
    return c;
  }

  private Counter unwrap(Counter c) {
    Counter tmp = c;
    while (tmp instanceof SwapCounter) {
      tmp = ((SwapCounter) tmp).get();
    }
    return tmp;
  }
}
