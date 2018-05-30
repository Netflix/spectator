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

import com.netflix.spectator.api.patterns.PolledMeter;
import com.netflix.spectator.impl.Config;
import com.netflix.spectator.impl.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * Base class to make it easier to implement a simple registry that only needs to customise the
 * types returned for Counter, DistributionSummary, and Timer calls.
 */
public abstract class AbstractRegistry implements Registry {
  /** Logger instance for the class. */
  protected final Logger logger;

  private final Clock clock;
  private final RegistryConfig config;

  private final ConcurrentHashMap<Id, Meter> meters;
  private final ConcurrentHashMap<Id, Object> state;

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
    this.logger = LoggerFactory.getLogger(getClass());
    this.clock = clock;
    this.config = config;
    this.meters = new ConcurrentHashMap<>();
    this.state = new ConcurrentHashMap<>();
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

  /**
   * Create a new gauge instance for a given id.
   *
   * @param id
   *     Identifier used to lookup this meter in the registry.
   * @return
   *     New gauge instance.
   */
  protected abstract Gauge newGauge(Id id);

  /**
   * Create a new max gauge instance for a given id.
   *
   * @param id
   *     Identifier used to lookup this meter in the registry.
   * @return
   *     New gauge instance.
   */
  protected abstract Gauge newMaxGauge(Id id);

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

  private Meter compute(Meter m, Meter fallback) {
    return (meters.size() >= config.maxNumberOfMeters()) ? fallback : m;
  }

  @Override public void register(Meter meter) {
    PolledMeter.monitorMeter(this, meter);
  }

  @Override public ConcurrentMap<Id, Object> state() {
    return state;
  }

  @Override public final Counter counter(Id id) {
    Counter c = getOrCreate(id, Counter.class, NoopCounter.INSTANCE, this::newCounter);
    return new SwapCounter(this, id, c);
  }

  @Override public final DistributionSummary distributionSummary(Id id) {
    DistributionSummary ds = getOrCreate(
        id,
        DistributionSummary.class,
        NoopDistributionSummary.INSTANCE,
        this::newDistributionSummary);
    return new SwapDistributionSummary(this, id, ds);
  }

  @Override public final Timer timer(Id id) {
    Timer t = getOrCreate(id, Timer.class, NoopTimer.INSTANCE, this::newTimer);
    return new SwapTimer(this, id, t);
  }

  @Override public final Gauge gauge(Id id) {
    Gauge g = getOrCreate(id, Gauge.class, NoopGauge.INSTANCE, this::newGauge);
    return new SwapGauge(this, id, g);
  }

  @Override public final Gauge maxGauge(Id id) {
    Gauge g = getOrCreate(id, Gauge.class, NoopGauge.INSTANCE, this::newMaxGauge);
    return new SwapMaxGauge(this, id, g);
  }

  /**
   * Helper used to get or create an instance of a core meter type. This is mostly used
   * internally to this implementation, but may be useful in rare cases for creating
   * customizations based on a core type in a sub-class.
   *
   * @param id
   *     Identifier used to lookup this meter in the registry.
   * @param cls
   *     Type of the meter.
   * @param dflt
   *     Default value used if there is a failure during the lookup and it is not configured
   *     to propagate.
   * @param factory
   *     Function for creating a new instance of the meter type if one is not already available
   *     in the registry.
   * @return
   *     Instance of the meter.
   */
  @SuppressWarnings("unchecked")
  protected <T extends Meter> T getOrCreate(Id id, Class<T> cls, T dflt, Function<Id, T> factory) {
    try {
      Preconditions.checkNotNull(id, "id");
      Meter m = Utils.computeIfAbsent(meters, id, i -> compute(factory.apply(i), dflt));
      if (!cls.isAssignableFrom(m.getClass())) {
        logTypeError(id, cls, m.getClass());
        m = dflt;
      }
      return (T) m;
    } catch (Exception e) {
      propagate(e);
      return dflt;
    }
  }

  @Override public final Meter get(Id id) {
    return meters.get(id);
  }

  @Override public final Iterator<Meter> iterator() {
    return meters.values().iterator();
  }

  /**
   * Can be called by sub-classes to remove expired meters from the internal map.
   * The SwapMeter types that are returned will lookup a new copy on the next access.
   */
  protected void removeExpiredMeters() {
    Iterator<Map.Entry<Id, Meter>> it = meters.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<Id, Meter> entry = it.next();
      Meter m = entry.getValue();
      if (m.hasExpired()) {
        it.remove();
      }
    }
  }
}
