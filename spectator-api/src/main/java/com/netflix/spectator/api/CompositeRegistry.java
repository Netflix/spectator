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

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Maps calls to zero or more sub-registries. If zero then it will act similar to the noop
 * registry. Otherwise activity will be sent to all registries that are part of the composite.
 */
final class CompositeRegistry implements Registry {

  private final Clock clock;
  private final Registry[] registries;

  /** Creates a new instance. */
  CompositeRegistry(Clock clock, Registry[] registries) {
    this.clock = clock;
    this.registries = registries;
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
    for (Registry r : registries) {
      r.register(meter);
    }
  }

  @Override public Counter counter(Id id) {
    final int n = registries.length;
    if (n == 0) {
      return NoopCounter.INSTANCE;
    } else {
      final Counter[] meters = new Counter[n];
      for (int i = 0; i < n; ++i) {
        meters[i] = registries[i].counter(registries[i].createId(id.name(), id.tags()));
      }
      return new CompositeCounter(id, meters);
    }
  }

  @Override public DistributionSummary distributionSummary(Id id) {
    final int n = registries.length;
    if (n == 0) {
      return NoopDistributionSummary.INSTANCE;
    } else {
      final DistributionSummary[] meters = new DistributionSummary[n];
      for (int i = 0; i < n; ++i) {
        meters[i] = registries[i].distributionSummary(registries[i].createId(id.name(), id.tags()));
      }
      return new CompositeDistributionSummary(id, meters);
    }
  }

  @Override public Timer timer(Id id) {
    final int n = registries.length;
    if (n == 0) {
      return NoopTimer.INSTANCE;
    } else {
      final Timer[] meters = new Timer[n];
      for (int i = 0; i < n; ++i) {
        meters[i] = registries[i].timer(registries[i].createId(id.name(), id.tags()));
      }
      return new CompositeTimer(id, clock, meters);
    }
  }

  @Override public Meter get(Id id) {
    final int n = registries.length;
    if (n == 0) {
      return null;
    } else {
      final Meter[] meters = new Meter[n];
      for (int i = 0; i < n; ++i) {
        meters[i] = registries[i].get(registries[i].createId(id.name(), id.tags()));
      }
      return new CompositeMeter(id) {
        @Override protected Meter[] meters() {
          return meters;
        }
      };
    }
  }

  @Override public Iterator<Meter> iterator() {
    if (registries.length == 0) {
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
