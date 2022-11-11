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

import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Wraps another counter allowing the underlying type to be swapped. */
final class SwapCounter extends SwapMeter<Counter> implements Counter {

  /** Create a new instance. */
  SwapCounter(Registry registry, LongSupplier versionSupplier, Id id, Counter underlying) {
    super(registry, versionSupplier, id, underlying);
  }

  @Override public Counter lookup() {
    return registry.counter(id);
  }

  @Override public void add(double amount) {
    get().add(amount);
  }

  @Override public double actualCount() {
    return get().actualCount();
  }

  @SuppressWarnings("unchecked")
  @Override public BatchUpdater batchUpdater(int batchSize) {
    BatchUpdater updater = get().batchUpdater(batchSize);
    // Registry implementations can implement `Consumer<Supplier<Counter>>` to allow the
    // meter to be resolved when flushed and avoid needing to hold on to a particular
    // instance of the meter that might have expired and been removed from the registry.
    if (updater instanceof Consumer<?>) {
      ((Consumer<Supplier<Counter>>) updater).accept(this::get);
    }
    return updater;
  }
}
