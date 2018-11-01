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
package com.netflix.spectator.micrometer;

import com.netflix.spectator.api.DistributionSummary;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;

/**
 * Wraps a Micrometer DistributionSummary to make it conform to the Spectator API.
 */
class MicrometerDistributionSummary extends MicrometerMeter implements DistributionSummary {

  private final io.micrometer.core.instrument.DistributionSummary impl;

  /** Create a new instance. */
  MicrometerDistributionSummary(Id id, io.micrometer.core.instrument.DistributionSummary impl) {
    super(id);
    this.impl = impl;
  }

  @Override public void record(long amount) {
    impl.record(amount);
  }

  @Override public long count() {
    return impl.count();
  }

  @Override public long totalAmount() {
    return (long) impl.totalAmount();
  }

  @Override public Iterable<Measurement> measure() {
    return convert(impl.measure());
  }
}
