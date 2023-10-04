/*
 * Copyright 2014-2023 Netflix, Inc.
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

import com.netflix.spectator.api.patterns.PolledMeter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.function.LongSupplier;

/**
 * Maps calls to zero or more sub-registries. If zero then it will act similar to the noop
 * registry. Otherwise activity will be sent to all registries that are part of the composite.
 */
public final class CompositeRegistry implements Registry {

  private final Lock lock = new ReentrantLock();

  private final Clock clock;

  private final AtomicReference<Registry[]> registries;
  private final AtomicLong version;
  private final LongSupplier versionSupplier;

  private final ConcurrentHashMap<Id, Object> state;


  /** Creates a new instance. */
  CompositeRegistry(Clock clock) {
    this.clock = clock;
    this.registries = new AtomicReference<>(new Registry[0]);
    this.version = new AtomicLong();
    this.versionSupplier = version::get;
    this.state = new ConcurrentHashMap<>();
  }

  /**
   * Find the first registry in the composite that is an instance of {@code c}. If no match is
   * found then null will be returned.
   */
  @SuppressWarnings("unchecked")
  <T extends Registry> T find(Class<T> c) {
    for (Registry r : registries.get()) {
      if (c.isAssignableFrom(r.getClass())) {
        return (T) r;
      }
    }
    return null;
  }

  private int indexOf(Registry[] rs, Registry registry) {
    for (int i = 0; i < rs.length; ++i) {
      if (rs[i].equals(registry))
        return i;
    }
    return -1;
  }

  /** Add a registry to the composite. */
  public void add(Registry registry) {
    lock.lock();
    try {
      Registry[] rs = registries.get();
      int pos = indexOf(rs, registry);
      if (pos == -1) {
        Registry[] tmp = new Registry[rs.length + 1];
        System.arraycopy(rs, 0, tmp, 0, rs.length);
        tmp[rs.length] = registry;
        registries.set(tmp);
        version.incrementAndGet();
      }
    } finally {
      lock.unlock();
    }
  }

  /** Remove a registry from the composite. */
  public void remove(Registry registry) {
    lock.lock();
    try {
      Registry[] rs = registries.get();
      int pos = indexOf(rs, registry);
      if (pos >= 0) {
        Registry[] tmp = new Registry[rs.length - 1];
        if (pos > 0)
          System.arraycopy(rs, 0, tmp, 0, pos);
        if (pos < tmp.length)
          System.arraycopy(rs, pos + 1, tmp, pos, rs.length - pos - 1);
        registries.set(tmp);
        version.incrementAndGet();
      }
    } finally {
      lock.unlock();
    }
  }

  /** Remove all registries from the composite. */
  public void removeAll() {
    lock.lock();
    try {
      registries.set(new Registry[0]);
      state.clear();
    } finally {
      lock.unlock();
    }
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

  @Override public Id createId(String name, String... tags) {
    return new DefaultId(name, ArrayTagSet.create(tags));
  }

  @Override public Id createId(String name, Map<String, String> tags) {
    return new DefaultId(name, ArrayTagSet.create(tags));
  }

  @Deprecated
  @Override public void register(Meter meter) {
    PolledMeter.monitorMeter(this, meter);
  }

  @Override public ConcurrentMap<Id, Object> state() {
    return state;
  }

  private <T> List<T> meters(Registry[] rs, Id id, BiFunction<Registry, Id, T> f) {
    List<T> ms = new ArrayList<>(rs.length);
    for (Registry r : rs) {
      ms.add(f.apply(r, id));
    }
    return ms;
  }

  private Counter newCounter(Id id) {
    Registry[] rs = registries.get();
    Counter c;
    switch (rs.length) {
      case 0:
        c = NoopCounter.INSTANCE;
        break;
      case 1:
        c = rs[0].counter(id);
        break;
      default:
        List<Counter> cs = meters(rs, id, Registry::counter);
        c = new CompositeCounter(id, cs);
        break;
    }
    return c;
  }

  @Override public Counter counter(Id id) {
    return new SwapCounter(this, versionSupplier, id, newCounter(id));
  }

  private DistributionSummary newDistributionSummary(Id id) {
    Registry[] rs = registries.get();
    DistributionSummary t;
    switch (rs.length) {
      case 0:
        t = NoopDistributionSummary.INSTANCE;
        break;
      case 1:
        t = rs[0].distributionSummary(id);
        break;
      default:
        List<DistributionSummary> ds = meters(rs, id, Registry::distributionSummary);
        t = new CompositeDistributionSummary(id, ds);
        break;
    }
    return t;
  }

  @Override public DistributionSummary distributionSummary(Id id) {
    return new SwapDistributionSummary(this, versionSupplier, id, newDistributionSummary(id));
  }

  private Timer newTimer(Id id) {
    Registry[] rs = registries.get();
    Timer t;
    switch (rs.length) {
      case 0:
        t = NoopTimer.INSTANCE;
        break;
      case 1:
        t = rs[0].timer(id);
        break;
      default:
        List<Timer> ts = meters(rs, id, Registry::timer);
        t = new CompositeTimer(id, clock, ts);
        break;
    }
    return t;
  }

  @Override public Timer timer(Id id) {
    return new SwapTimer(this, versionSupplier, id, newTimer(id));
  }

  private Gauge newGauge(Id id) {
    Registry[] rs = registries.get();
    Gauge t;
    switch (rs.length) {
      case 0:
        t = NoopGauge.INSTANCE;
        break;
      case 1:
        t = rs[0].gauge(id);
        break;
      default:
        List<Gauge> gs = meters(rs, id, Registry::gauge);
        t = new CompositeGauge(id, gs);
        break;
    }
    return t;
  }

  @Override public Gauge gauge(Id id) {
    return new SwapGauge(this, versionSupplier, id, newGauge(id));
  }

  private Gauge newMaxGauge(Id id) {
    Registry[] rs = registries.get();
    Gauge t;
    switch (rs.length) {
      case 0:
        t = NoopGauge.INSTANCE;
        break;
      case 1:
        t = rs[0].maxGauge(id);
        break;
      default:
        List<Gauge> gs = meters(rs, id, Registry::maxGauge);
        t = new CompositeGauge(id, gs);
        break;
    }
    return t;
  }

  @Override public Gauge maxGauge(Id id) {
    return new SwapGauge(this, versionSupplier, id, newMaxGauge(id));
  }

  @Override public Meter get(Id id) {
    for (Registry r : registries.get()) {
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
  }

  @Override public Iterator<Meter> iterator() {
    Registry[] rs = registries.get();
    if (rs.length == 0) {
      return Collections.emptyIterator();
    } else {
      final Set<Id> ids = new HashSet<>();
      for (Registry r : rs) {
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
  }
}
