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

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Maps calls to zero or more sub-registries. If zero then it will act similar to the noop
 * registry. Otherwise activity will be sent to all registries that are part of the composite.
 */
final class CompositeRegistry implements Registry {

  /**
   * Id used for a meter storing all gauges registered with the composite. Since there is no
   * activity on a gauge, all gauges will get registered with a sub-registry when it is added
   * by including the meter with this id.
   */
  static final Id GAUGES_ID = new DefaultId("spectator.composite.gauges");

  private final Clock clock;
  private final CopyOnWriteArraySet<Registry> registries;

  private final RegistryMeter gauges;

  /** Creates a new instance. */
  CompositeRegistry(Clock clock) {
    this.clock = clock;
    this.registries = new CopyOnWriteArraySet<>();
    this.gauges = new RegistryMeter(GAUGES_ID);
  }

  /**
   * Find the first registry in the composite that is an instance of {@code c}. If no match is
   * found then null will be returned.
   */
  @SuppressWarnings("unchecked")
  <T extends Registry> T find(Class<T> c) {
    for (Registry r : registries) {
      if (c.isAssignableFrom(r.getClass())) {
        return (T) r;
      }
    }
    return null;
  }

  /** Add a registry to the composite. */
  public void add(Registry registry) {
    registries.add(registry);
    registry.register(gauges);
  }

  /** Remove a registry from the composite. */
  public void remove(Registry registry) {
    registries.remove(registry);
  }

  @Override public Clock clock() {
    return clock;
  }

  @Override public Id createId(String name) {
    return new DefaultId(name);
  }

  @Override public Id createId(String name, Iterable<Tag> tags) {
    return new DefaultId(name, TagList.create(tags));
  }

  @Override public void register(Meter meter) {
    gauges.register(meter);
  }

  @Override public Counter counter(Id id) {
    return new CompositeCounter(id, registries);
  }

  @Override public DistributionSummary distributionSummary(Id id) {
    return new CompositeDistributionSummary(id, registries);
  }

  @Override public Timer timer(Id id) {
    return new CompositeTimer(id, clock, registries);
  }

  @Override public Meter get(Id id) {
    return new CompositeMeter(id, registries);
  }

  @Override public Iterator<Meter> iterator() {
    if (registries.isEmpty()) {
      return Collections.<Meter>emptyList().iterator();
    } else {
      final Set<Id> ids = new HashSet<>();
      for (Registry r : registries) {
        for (Meter m : r) ids.add(m.id());
      }

      return new Iterator<Meter>() {
        private final Iterator<Id> idIter = ids.iterator();

        @Override public boolean hasNext() {
          return idIter.hasNext();
        }

        @Override public Meter next() {
          return get(idIter.next());
        }

        @Override public void remove() {
          throw new UnsupportedOperationException();
        }
      };
    }
  }
}
