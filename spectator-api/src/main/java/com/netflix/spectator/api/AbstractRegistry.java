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

import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base class to make it easier to implement a simple registry that only needs to customise the
 * types returned for Counter, DistributionSummary, and Timer calls.
 */
public abstract class AbstractRegistry implements Registry {

  private final Clock clock;

  private final ConcurrentHashMap<Id, Meter> meters;

  /**
   * Create a new instance.
   *
   * @param clock
   *     Clock used for performing all timing measurements.
   */
  public AbstractRegistry(Clock clock) {
    this.clock = clock;
    meters = new ConcurrentHashMap<>();
  }

  /**
   * Create a new counter instance for a given id.
   *
   * @param id
   *     Identifier used to lookup this meter in the registry.
   * @return
   *     New counter instance.
   */
  protected abstract Counter newCounter(Id id);

  /**
   * Create a new distribution summary instance for a given id.
   *
   * @param id
   *     Identifier used to lookup this meter in the registry.
   * @return
   *     New counter instance.
   */
  protected abstract DistributionSummary newDistributionSummary(Id id);

  /**
   * Create a new timer instance for a given id.
   *
   * @param id
   *     Identifier used to lookup this meter in the registry.
   * @return
   *     New counter instance.
   */
  protected abstract Timer newTimer(Id id);

  @Override public final Clock clock() {
    return clock;
  }

  @Override public final Id createId(String name) {
    return new DefaultId(name, TagList.EMPTY);
  }

  @Override public final Id createId(String name, Iterable<Tag> tags) {
    return new DefaultId(name, TagList.create(tags));
  }

  private void logTypeError(Id id, Class<?> desired, Class<?> found) {
    final String dtype = desired.getName();
    final String ftype = found.getName();
    final String msg = String.format("cannot access '%s' as a %s, it already exists as a %s",
      id, dtype, ftype);
    Throwables.propagate(new IllegalStateException(msg));
  }

  private void addToAggr(Meter aggr, Meter meter) {
    if (aggr instanceof AggrMeter) {
      ((AggrMeter) aggr).add(meter);
    } else {
      logTypeError(meter.id(), meter.getClass(), aggr.getClass());
    }
  }

  private Meter putIfAbsent(Id id, Meter m, Meter fallback) {
    return (meters.size() >= Config.maxNumberOfMeters()) ? fallback : meters.putIfAbsent(id, m);
  }

  @Override public final void register(Meter meter) {
    Meter m = meters.get(meter.id());
    if (m == null) {
      if (meters.size() >= Config.maxNumberOfMeters()) {
        return;
      }
      AggrMeter aggr = new AggrMeter(meter.id());
      m = meters.putIfAbsent(meter.id(), aggr);
      if (m == null) {
        aggr.add(meter);
      } else {
        addToAggr(m, meter);
      }
    } else {
      addToAggr(m, meter);
    }
  }

  @Override public final Counter counter(Id id) {
    Meter m = meters.get(id);
    if (m == null) {
      Counter c = newCounter(id);
      m = putIfAbsent(id, c, NoopCounter.INSTANCE);
      if (m == null) {
        m = c;
      }
    }
    if (!(m instanceof Counter)) {
      logTypeError(id, Counter.class, m.getClass());
      m = NoopCounter.INSTANCE;
    }
    return (Counter) m;
  }

  @Override public final DistributionSummary distributionSummary(Id id) {
    Meter m = meters.get(id);
    if (m == null) {
      DistributionSummary s = newDistributionSummary(id);
      m = putIfAbsent(id, s, NoopDistributionSummary.INSTANCE);
      if (m == null) {
        m = s;
      }
    }
    if (!(m instanceof DistributionSummary)) {
      logTypeError(id, DistributionSummary.class, m.getClass());
      m = NoopDistributionSummary.INSTANCE;
    }
    return (DistributionSummary) m;
  }

  @Override public final Timer timer(Id id) {
    Meter m = meters.get(id);
    if (m == null) {
      Timer t = newTimer(id);
      m = putIfAbsent(id, t, NoopTimer.INSTANCE);
      if (m == null) {
        m = t;
      }
    }
    if (!(m instanceof Timer)) {
      logTypeError(id, Timer.class, m.getClass());
      m = NoopTimer.INSTANCE;
    }
    return (Timer) m;
  }

  @Override public final Meter get(Id id) {
    return meters.get(id);
  }

  @Override public final Iterator<Meter> iterator() {
    return meters.values().iterator();
  }
}
