/*
 * Copyright 2014-2022 Netflix, Inc.
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
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Wraps another timer allowing the underlying type to be swapped. */
final class SwapTimer extends SwapMeter<Timer> implements Timer {

  /** Create a new instance. */
  SwapTimer(Registry registry, LongSupplier versionSupplier, Id id, Timer underlying) {
    super(registry, versionSupplier, id, underlying);
  }

  @Override public Timer lookup() {
    return registry.timer(id);
  }

  @Override public void record(long amount, TimeUnit unit) {
    get().record(amount, unit);
  }

  @Override public <T> T record(Callable<T> f) throws Exception {
    final long s = registry.clock().monotonicTime();
    try {
      return f.call();
    } finally {
      final long e = registry.clock().monotonicTime();
      record(e - s, TimeUnit.NANOSECONDS);
    }
  }

  @Override public void record(Runnable f) {
    final long s = registry.clock().monotonicTime();
    try {
      f.run();
    } finally {
      final long e = registry.clock().monotonicTime();
      record(e - s, TimeUnit.NANOSECONDS);
    }
  }

  @Override public long count() {
    return get().count();
  }

  @Override public long totalTime() {
    return get().totalTime();
  }

  @SuppressWarnings("unchecked")
  @Override public BatchUpdater batchUpdater(int batchSize) {
    BatchUpdater updater = get().batchUpdater(batchSize);
    // Registry implementations can implement `Consumer<Supplier<Timer>>` to allow the
    // meter to be resolved when flushed and avoid needing to hold on to a particular
    // instance of the meter that might have expired and been removed from the registry.
    if (updater instanceof Consumer<?>) {
      ((Consumer<Supplier<Timer>>) updater).accept(this::get);
    }
    return updater;
  }
}
