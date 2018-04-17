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

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/** Wraps another timer allowing the underlying type to be swapped. */
final class SwapTimer implements Timer, SwapMeter<Timer> {

  private final Registry registry;
  private final Id id;
  private volatile Timer underlying;

  /** Create a new instance. */
  SwapTimer(Registry registry, Id id, Timer underlying) {
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
    Timer t = underlying;
    return t == null || t.hasExpired();
  }

  @Override public void record(long amount, TimeUnit unit) {
    get().record(amount, unit);
  }

  @Override public <T> T record(Callable<T> f) throws Exception {
    return get().record(f);
  }

  @Override public void record(Runnable f) {
    get().record(f);
  }

  @Override public long count() {
    return get().count();
  }

  @Override public long totalTime() {
    return get().totalTime();
  }

  @Override public void set(Timer t) {
    underlying = t;
  }

  @Override public Timer get() {
    Timer t = underlying;
    if (t == null) {
      t = unwrap(registry.timer(id));
      underlying = t;
    }
    return t;
  }

  private Timer unwrap(Timer t) {
    Timer tmp = t;
    while (tmp instanceof SwapTimer) {
      tmp = ((SwapTimer) tmp).get();
    }
    return tmp;
  }
}
