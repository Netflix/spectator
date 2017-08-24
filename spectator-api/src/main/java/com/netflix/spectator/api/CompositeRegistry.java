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

import com.netflix.spectator.api.patterns.PolledGauge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Maps calls to zero or more sub-registries. If zero then it will act similar to the noop
 * registry. Otherwise activity will be sent to all registries that are part of the composite.
 */
public final class CompositeRegistry implements Registry {

  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
  private final ReentrantReadWriteLock.ReadLock rlock = lock.readLock();
  private final ReentrantReadWriteLock.WriteLock wlock = lock.writeLock();

  private final Clock clock;

  private final List<Registry> registries;

  private final ConcurrentHashMap<Id, SwapCounter> counters;
  private final ConcurrentHashMap<Id, SwapDistributionSummary> distSummaries;
  private final ConcurrentHashMap<Id, SwapTimer> timers;
  private final ConcurrentHashMap<Id, SwapGauge> gauges;

  private final ConcurrentHashMap<Id, Object> state;

  /** Creates a new instance. */
  CompositeRegistry(Clock clock) {
    this.clock = clock;
    this.registries = new ArrayList<>();
    this.counters = new ConcurrentHashMap<>();
    this.distSummaries = new ConcurrentHashMap<>();
    this.timers = new ConcurrentHashMap<>();
    this.gauges = new ConcurrentHashMap<>();
    this.state = new ConcurrentHashMap<>();
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
    wlock.lock();
    try {
      registries.add(registry);
      updateMeters();
    } finally {
      wlock.unlock();
    }
  }

  /** Remove a registry from the composite. */
  public void remove(Registry registry) {
    wlock.lock();
    try {
      registries.remove(registry);
      updateMeters();
    } finally {
      wlock.unlock();
    }
  }

  /** Remove all registries from the composite. */
  public void removeAll() {
    wlock.lock();
    try {
      registries.clear();
      updateMeters();
    } finally {
      wlock.unlock();
    }
  }

  private void updateMeters() {
    counters.forEach((id, c) -> c.setUnderlying(newCounter(id)));
    distSummaries.forEach((id, d) -> d.setUnderlying(newDistributionSummary(id)));
    timers.forEach((id, t) -> t.setUnderlying(newTimer(id)));
    gauges.forEach((id, g) -> g.setUnderlying(newGauge(id)));
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
    PolledGauge.monitorMeter(this, meter);
  }

  @Override public ConcurrentMap<Id, Object> state() {
    return state;
  }

  private Counter newCounter(Id id) {
    rlock.lock();
    try {
      Counter c;
      switch (registries.size()) {
        case 0:
          c = NoopCounter.INSTANCE;
          break;
        case 1:
          c = registries.get(0).counter(id);
          break;
        default:
          List<Counter> cs = registries.stream()
              .map(r -> r.counter(id))
              .collect(Collectors.toList());
          c = new CompositeCounter(id, cs);
          break;
      }
      return c;
    } finally {
      rlock.unlock();
    }
  }

  @Override public Counter counter(Id id) {
    return Utils.computeIfAbsent(counters, id, i -> new SwapCounter(newCounter(i)));
  }

  private DistributionSummary newDistributionSummary(Id id) {
    rlock.lock();
    try {
      DistributionSummary t;
      switch (registries.size()) {
        case 0:
          t = NoopDistributionSummary.INSTANCE;
          break;
        case 1:
          t = registries.get(0).distributionSummary(id);
          break;
        default:
          List<DistributionSummary> ds = registries.stream()
              .map(r -> r.distributionSummary(id))
              .collect(Collectors.toList());
          t = new CompositeDistributionSummary(id, ds);
          break;
      }
      return t;
    } finally {
      rlock.unlock();
    }
  }

  @Override public DistributionSummary distributionSummary(Id id) {
    return Utils.computeIfAbsent(distSummaries, id, i -> new SwapDistributionSummary(newDistributionSummary(i)));
  }

  private Timer newTimer(Id id) {
    rlock.lock();
    try {
      Timer t;
      switch (registries.size()) {
        case 0:
          t = NoopTimer.INSTANCE;
          break;
        case 1:
          t = registries.get(0).timer(id);
          break;
        default:
          List<Timer> ts = registries.stream()
              .map(r -> r.timer(id))
              .collect(Collectors.toList());
          t = new CompositeTimer(id, clock, ts);
          break;
      }
      return t;
    } finally {
      rlock.unlock();
    }
  }

  @Override public Timer timer(Id id) {
    return Utils.computeIfAbsent(timers, id, i -> new SwapTimer(newTimer(i)));
  }

  private Gauge newGauge(Id id) {
    rlock.lock();
    try {
      Gauge t;
      switch (registries.size()) {
        case 0:
          t = NoopGauge.INSTANCE;
          break;
        case 1:
          t = registries.get(0).gauge(id);
          break;
        default:
          List<Gauge> gs = registries.stream()
              .map(r -> r.gauge(id))
              .collect(Collectors.toList());
          t = new CompositeGauge(id, gs);
          break;
      }
      return t;
    } finally {
      rlock.unlock();
    }
  }

  @Override public Gauge gauge(Id id) {
    return Utils.computeIfAbsent(gauges, id, i -> new SwapGauge(newGauge(i)));
  }

  @Override public Meter get(Id id) {
    rlock.lock();
    try {
      for (Registry r : registries) {
        Meter m = r.get(id);
        if (m != null) {
          if (m instanceof Counter) {
            return counter(id);
          } else if (m instanceof Timer) {
            return timer(id);
          } else if (m instanceof DistributionSummary) {
            return distributionSummary(id);
          } else if (m instanceof Gauge) {
            return gauge(id);
          } else {
            return null;
          }
        }
      }
      return null;
    } finally {
      rlock.unlock();
    }
  }

  @Override public Iterator<Meter> iterator() {
    rlock.lock();
    try {
      if (registries.isEmpty()) {
        return Collections.<Meter>emptyList().iterator();
      } else {
        final Set<Id> ids = new HashSet<>();
        for (Registry r : registries) {
          for (Meter m : r) ids.add(m.id());
        }

        return new Iterator<Meter>() {
          private final Iterator<Id> idIter = ids.iterator();

          @Override
          public boolean hasNext() {
            return idIter.hasNext();
          }

          @Override
          public Meter next() {
            return get(idIter.next());
          }

          @Override
          public void remove() {
            throw new UnsupportedOperationException();
          }
        };
      }
    } finally {
      rlock.unlock();
    }
  }
}
