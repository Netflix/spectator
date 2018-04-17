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

/** Wraps another distribution summary allowing the underlying type to be swapped. */
final class SwapDistributionSummary implements DistributionSummary, SwapMeter<DistributionSummary> {

  private final Registry registry;
  private final Id id;
  private volatile DistributionSummary underlying;

  /** Create a new instance. */
  SwapDistributionSummary(Registry registry, Id id, DistributionSummary underlying) {
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
    DistributionSummary d = underlying;
    return d == null || d.hasExpired();
  }

  @Override public void record(long amount) {
    get().record(amount);
  }

  @Override
  public long count() {
    return get().count();
  }

  @Override
  public long totalAmount() {
    return get().totalAmount();
  }

  @Override public void set(DistributionSummary d) {
    underlying = d;
  }

  @Override public DistributionSummary get() {
    DistributionSummary d = underlying;
    if (d == null) {
      d = unwrap(registry.distributionSummary(id));
      underlying = d;
    }
    return d;
  }

  private DistributionSummary unwrap(DistributionSummary d) {
    DistributionSummary tmp = d;
    while (tmp instanceof SwapDistributionSummary) {
      tmp = ((SwapDistributionSummary) tmp).get();
    }
    return tmp;
  }
}
