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

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/** Wraps another timer allowing the underlying type to be swapped. */
final class SwapTimer implements Timer {

  private volatile Timer underlying;

  /** Create a new instance. */
  SwapTimer(Timer underlying) {
    this.underlying = underlying;
  }

  void setUnderlying(Timer t) {
    underlying = t;
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

  @Override public void record(long amount, TimeUnit unit) {
    underlying.record(amount, unit);
  }

  @Override public <T> T record(Callable<T> f) throws Exception {
    return underlying.record(f);
  }

  @Override public void record(Runnable f) {
    underlying.record(f);
  }

  @Override public long count() {
    return underlying.count();
  }

  @Override public long totalTime() {
    return underlying.totalTime();
  }
}
