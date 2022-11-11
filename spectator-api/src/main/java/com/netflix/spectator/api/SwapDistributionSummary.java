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

/** Wraps another distribution summary allowing the underlying type to be swapped. */
final class SwapDistributionSummary extends SwapMeter<DistributionSummary> implements DistributionSummary {

  /** Create a new instance. */
  SwapDistributionSummary(
      Registry registry,
      LongSupplier versionSupplier,
      Id id,
      DistributionSummary underlying) {
    super(registry, versionSupplier, id, underlying);
  }

  @Override public DistributionSummary lookup() {
    return registry.distributionSummary(id);
  }

  @Override public void record(long amount) {
    get().record(amount);
  }

  @Override public void record(long[] amounts, int n) {
    get().record(amounts, n);
  }

  @Override
  public long count() {
    return get().count();
  }

  @Override
  public long totalAmount() {
    return get().totalAmount();
  }

  @SuppressWarnings("unchecked")
  @Override public BatchUpdater batchUpdater(int batchSize) {
    BatchUpdater updater = get().batchUpdater(batchSize);
    // Registry implementations can implement `Consumer<Supplier<DistributionSummary>>` to
    // allow the meter to be resolved when flushed and avoid needing to hold on to a particular
    // instance of the meter that might have expired and been removed from the registry.
    if (updater instanceof Consumer<?>) {
      ((Consumer<Supplier<DistributionSummary>>) updater).accept(this::get);
    }
    return updater;
  }
}
