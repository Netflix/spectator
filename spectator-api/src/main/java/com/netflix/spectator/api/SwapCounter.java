/*
 * Copyright 2014-2017 Netflix, Inc.
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

/** Wraps another counter allowing the underlying type to be swapped. */
final class SwapCounter implements Counter {

  private volatile Counter underlying;

  /** Create a new instance. */
  SwapCounter(Counter underlying) {
    this.underlying = underlying;
  }

  void setUnderlying(Counter c) {
    underlying = c;
  }

  @Override public Id id() {
    return underlying.id();
  }

  @Override public Iterable<Measurement> measure() {
    return underlying.measure();
  }

  @Override public boolean hasExpired() {
    return underlying.hasExpired();
  }

  @Override public void increment() {
    underlying.increment();
  }

  @Override public void increment(long amount) {
    underlying.increment(amount);
  }

  @Override public long count() {
    return underlying.count();
  }
}
