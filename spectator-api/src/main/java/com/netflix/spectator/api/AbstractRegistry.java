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

import com.netflix.spectator.impl.Config;
import com.netflix.spectator.impl.Preconditions;

import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Base class to make it easier to implement a simple registry that only needs to customise the
 * types returned for Counter, DistributionSummary, and Timer calls.
 */
public abstract class AbstractRegistry implements Registry {

  private final Clock clock;
  private final RegistryConfig config;

  private final ConcurrentHashMap<Id, Meter> meters;

  /**
   * Create a new instance.
   *
   * @param clock
   *     Clock used for performing all timing measurements.
   */
  public AbstractRegistry(Clock clock) {
    this(clock, Config.defaultConfig());
  }

  /**
   * Create a new instance.
   *
   * @param clock
   *     Clock used for performing all timing measurements.
   * @param config
   *     Configuration settings for the registry.
   */
  public AbstractRegistry(Clock clock, RegistryConfig config) {
    this.clock = clock;
    this.config = config;
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
   *     New distribution summary instance.
   */
  protected abstract DistributionSummary newDistributionSummary(Id id);

  /**
   * Create a new timer instance for a given id.
   *
   * @param id
   *     Identifier used to lookup this meter in the registry.
   * @return
   *     New timer instance.
   */
  protected abstract Timer newTimer(Id id);

  @Override public final Clock clock() {
    return clock;
  }

  @Override public final RegistryConfig config() {
    return config;
  }

  @Override public final Id createId(String name) {
    return new DefaultId(name);
  }

  @Override public final Id createId(String name, Iterable<Tag> tags) {
    return new DefaultId(name, ArrayTagSet.create(tags));
  }

  private void logTypeError(Id id, Class<?> desired, Class<?> found) {
    final String dtype = desired.getName();
    final String ftype = found.getName();
    final String msg = String.format("cannot access '%s' as a %s, it already exists as a %s",
      id, dtype, ftype);
    propagate(new IllegalStateException(msg));
  }

  private void addToAggr(Meter aggr, Meter meter) {
    if (aggr instanceof AggrMeter) {
      ((AggrMeter) aggr).add(meter);
    } else {
      logTypeError(meter.id(), meter.getClass(), aggr.getClass());
    }
  }

  private Meter compute(Meter m, Meter fallback) {
    return (meters.size() >= config.maxNumberOfMeters()) ? fallback : m;
  }

  /**
   * This method should be used instead of the
   * {@link ConcurrentHashMap#computeIfAbsent(Object, Function)} call to minimize
   * thread contention. This method does not require locking for the common case
   * where the key exists, but potentially performs additional computation when
   * absent.
   */
  private Meter computeIfAbsent(Id id, Function<Id, Meter> f) {
    Meter m = meters.get(id);
    if (m == null) {
      Meter tmp = f.apply(id);
      m = meters.putIfAbsent(id, tmp);
      if (m == null) {
        m = tmp;
      }
    }
    return m;
  }

  @Override public void register(Meter meter) {
    Meter aggr = (meters.size() >= config.maxNumberOfMeters())
      ? meters.get(meter.id())
      : computeIfAbsent(meter.id(), AggrMeter::new);
    if (aggr != null) {
      addToAggr(aggr, meter);
    }
  }

  @Override public final Counter counter(Id id) {
    try {
      Preconditions.checkNotNull(id, "id");
      Meter m = computeIfAbsent(id, i -> compute(newCounter(i), NoopCounter.INSTANCE));
      if (!(m instanceof Counter)) {
        logTypeError(id, Counter.class, m.getClass());
        m = NoopCounter.INSTANCE;
      }
      return (Counter) m;
    } catch (Exception e) {
      propagate(e);
      return NoopCounter.INSTANCE;
    }
  }

  @Override public final DistributionSummary distributionSummary(Id id) {
    try {
      Preconditions.checkNotNull(id, "id");
      Meter m = computeIfAbsent(id, i ->
          compute(newDistributionSummary(i), NoopDistributionSummary.INSTANCE));
      if (!(m instanceof DistributionSummary)) {
        logTypeError(id, DistributionSummary.class, m.getClass());
        m = NoopDistributionSummary.INSTANCE;
      }
      return (DistributionSummary) m;
    } catch (Exception e) {
      propagate(e);
      return NoopDistributionSummary.INSTANCE;
    }
  }

  @Override public final Timer timer(Id id) {
    try {
      Meter m = computeIfAbsent(id, i -> compute(newTimer(i), NoopTimer.INSTANCE));
      if (!(m instanceof Timer)) {
        logTypeError(id, Timer.class, m.getClass());
        m = NoopTimer.INSTANCE;
      }
      return (Timer) m;
    } catch (Exception e) {
      propagate(e);
      return NoopTimer.INSTANCE;
    }
  }

  @Override public final Meter get(Id id) {
    return meters.get(id);
  }

  @Override public final Iterator<Meter> iterator() {
    return meters.values().iterator();
  }
}
