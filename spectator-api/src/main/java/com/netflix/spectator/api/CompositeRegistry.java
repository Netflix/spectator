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

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Semaphore;

/**
 * Maps calls to zero or more sub-registries. If zero then it will act similar to the noop
 * registry. Otherwise activity will be sent to all registries that are part of the composite.
 */
public final class CompositeRegistry implements Registry {

  private final Clock clock;
  private final CopyOnWriteArraySet<Registry> registries;

  private final ConcurrentHashMap<Id, AggrMeter> gauges;

  private final Semaphore pollSem = new Semaphore(1);

  /** Creates a new instance. */
  CompositeRegistry(Clock clock) {
    this.clock = clock;
    this.registries = new CopyOnWriteArraySet<>();
    this.gauges = new ConcurrentHashMap<>();
    GaugePoller.schedule(
        new WeakReference<>(this),
        10000L,
        CompositeRegistry::pollGauges);
  }

  private static void pollGauges(Registry r) {
    ((CompositeRegistry) r).pollGauges();
  }

  /** Poll the values from all registered gauges. */
  @SuppressWarnings("PMD")
  void pollGauges() {
    if (pollSem.tryAcquire()) {
      try {
        for (Map.Entry<Id, AggrMeter> e : gauges.entrySet()) {
          Id id = e.getKey();
          Meter meter = e.getValue();
          try {
            if (!meter.hasExpired()) {
              for (Measurement m : meter.measure()) {
                gauge(m.id()).set(m.value());
              }
            }
          } catch (StackOverflowError t) {
            gauges.remove(id);
          } catch (VirtualMachineError | ThreadDeath t) {
            // Avoid catching OutOfMemoryError and other serious problems in the next
            // catch block.
            throw t;
          } catch (Throwable t) {
            // The sampling is calling user functions and therefore we cannot
            // make any guarantees they are well-behaved. We catch most Throwables with
            // the exception of some VM errors and drop the gauge.
            gauges.remove(id);
          }
        }
      } finally {
        pollSem.release();
      }
    }
  }

  /**
   * This method should be used instead of the computeIfAbsent call on the map to
   * minimize thread contention. This method does not require locking for the common
   * case where the key exists, but potentially performs additional computation when
   * absent.
   */
  private AggrMeter computeIfAbsent(Id id) {
    AggrMeter m = gauges.get(id);
    if (m == null) {
      AggrMeter tmp = new AggrMeter(id);
      m = gauges.putIfAbsent(id, tmp);
      if (m == null) {
        m = tmp;
      }
    }
    return m;
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
  }

  /** Remove a registry from the composite. */
  public void remove(Registry registry) {
    registries.remove(registry);
  }

  /** Remove all registries from the composite. */
  public void removeAll() {
    registries.clear();
  }

  @Override public Clock clock() {
    return clock;
  }

  @Override public Id createId(String name) {
    return new DefaultId(name);
  }

  @Override public Id createId(String name, Iterable<Tag> tags) {
    return new DefaultId(name, ArrayTagSet.create(tags));
  }

  @Override public void register(Meter meter) {
    AggrMeter m = computeIfAbsent(meter.id());
    m.add(meter);
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

  @Override public Gauge gauge(Id id) {
    return new CompositeGauge(id, registries);
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
