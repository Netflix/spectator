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

import java.util.function.Supplier;

/** Wraps another distribution summary allowing the underlying type to be swapped. */
final class SwapDistributionSummary implements DistributionSummary, Supplier<DistributionSummary> {

  private volatile DistributionSummary underlying;

  /** Create a new instance. */
  SwapDistributionSummary(DistributionSummary underlying) {
    this.underlying = underlying;
  }

  void setUnderlying(DistributionSummary d) {
    underlying = d;
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

  @Override public void record(long amount) {
    underlying.record(amount);
  }

  @Override
  public long count() {
    return underlying.count();
  }

  @Override
  public long totalAmount() {
    return underlying.totalAmount();
  }

  @Override public DistributionSummary get() {
    return underlying;
  }
}
