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
package com.netflix.spectator.sidecar;

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Id;

/**
 * Counter that writes updates in format compatible with SpectatorD.
 */
class SidecarCounter extends SidecarMeter implements Counter {

  private final SidecarWriter writer;

  /** Create a new instance. */
  SidecarCounter(Id id, SidecarWriter writer) {
    super(id, 'c');
    this.writer = writer;
  }

  @Override public void increment() {
    writer.write(idString, 1L);
  }

  @Override public void increment(long delta) {
    if (delta > 0L) {
      writer.write(idString, delta);
    }
  }

  @Override public void add(double amount) {
    if (amount > 0.0) {
      writer.write(idString, amount);
    }
  }

  @Override public double actualCount() {
    return Double.NaN;
  }
}
