/**
 * Copyright 2015 Netflix, Inc.
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

import java.util.ArrayList;
import java.util.List;


/**
 * Wraps a registry as a meter that can be added to another registry. This is currently used to
 * manage the gauges for the composite registry. Since there is no activity gauges should get
 * polled and must get tracked as a group and added to each sub-registry.
 */
class RegistryMeter implements Meter {

  private final Id id;
  private final Registry registry;

  /** Create a new instance. */
  RegistryMeter(Id id) {
    this(id, new DefaultRegistry());
  }

  /** Create a new instance. */
  RegistryMeter(Id id, Registry registry) {
    this.id = id;
    this.registry = registry;
  }

  /** Register a meter with the wrapped registry. */
  void register(Meter meter) {
    registry.register(meter);
  }

  @Override public Id id() {
    return id;
  }

  @Override public Iterable<Measurement> measure() {
    List<Measurement> ms = new ArrayList<>();
    for (Meter m : registry) {
      try {
        for (Measurement measurement : m.measure()) {
          ms.add(measurement);
        }
      } catch (Exception | LinkageError e) {
        // Linkage errors sometimes happen due to compatibility issues. If it is a problem for
        // the application, then it can fail elsewhere. If it happens for monitoring, then the
        // additional context of the id is typically helpful.
        Throwables.propagate("failed to measure " + m.id(), e);
      }
    }
    return ms;
  }

  @Override public boolean hasExpired() {
    return false;
  }
}
