/**
 * Copyright 2014 Netflix, Inc.
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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Composite meter that computes a sum aggregate of the overlapping measurements in meters in the
 * set. This is typically used to combine the values of gauges that share the same id.
 */
class AggrMeter implements Meter {
  private final Id id;
  private final ConcurrentLinkedQueue<Meter> queue;

  /** Create a new instance. */
  AggrMeter(Id id) {
    this.id = id;
    this.queue = new ConcurrentLinkedQueue<>();
  }

  /** {@inheritDoc} */
  @Override
  public Id id() {
    return id;
  }

  /** {@inheritDoc} */
  @Override
  public Iterable<Measurement> measure() {
    Map<Id, Measurement> measurements = new HashMap<>();
    Iterator<Meter> iter = queue.iterator();
    while (iter.hasNext()) {
      Meter meter = iter.next();
      if (meter.hasExpired()) {
        iter.remove();
      } else {
        for (Measurement m : meter.measure()) {
          Measurement prev = measurements.get(m.id());
          if (prev == null) {
            measurements.put(m.id(), m);
          } else {
            double v = prev.value() + m.value();
            measurements.put(prev.id(), new Measurement(prev.id(), prev.timestamp(), v));
          }
        }
      }
    }
    return measurements.values();
  }

  /** {@inheritDoc} */
  @Override
  public boolean hasExpired() {
    return queue.isEmpty();
  }

  /** Adds a meter to the set included in the aggregate. */
  void add(Meter m) {
    queue.add(m);
  }
}
