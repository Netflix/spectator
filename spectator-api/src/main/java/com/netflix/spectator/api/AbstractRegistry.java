/*
 * Copyright 2014-2021 Netflix, Inc.
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
import com.netflix.spectator.impl.Cache;
import com.netflix.spectator.impl.Config;
import com.netflix.spectator.impl.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Base class to make it easier to implement a simple registry that only needs to customise the
 * types returned for Counter, DistributionSummary, and Timer calls.
 */
public abstract class AbstractRegistry implements Registry {

  /** Not used for this registry, always return 0. */
  private static final LongSupplier VERSION = () -> 0L;

  /** Logger instance for the class. */
  protected final Logger logger;

  private final Clock clock;
  private final RegistryConfig config;

  private final ConcurrentHashMap<Id, Meter> meters;
  private final ConcurrentHashMap<Id, Object> state;

  private final Cache<Id, Id> idNormalizationCache;

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
    this.idNormalizationCache = Cache.lfu(new NoopRegistry(), "spectator-id", 1000, 10000);
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
    try {
      return new DefaultId(name);
    } catch (Exception e) {
      propagate(e);
      return NoopId.INSTANCE;
    }
  }

  @Override public final Id createId(String name, Iterable<Tag> tags) {
    try {
      return new DefaultId(name, ArrayTagSet.create(tags));
    } catch (Exception e) {
      propagate(e);
      return NoopId.INSTANCE;
    }
  }

  /**
   * Ensure a the id type is correct. While not recommended, nothing stops users from using a
   * custom implementation of the {@link Id} interface. This can create unexpected and strange
   * problems with the lookups failing, duplicate counters, etc. To avoid issues, this method
   * should be called to sanitize Id values coming from the user. If it is already a valid id,
   * then it will not create a new instance.
   */
  private Id normalizeId(Id id) {
    return (id instanceof DefaultId)
        ? id
        : idNormalizationCache.computeIfAbsent(id, i -> createId(i.name(), i.tags()));
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

  @Deprecated
  @Override public void register(Meter meter) {
    PolledMeter.monitorMeter(this, meter);
  }

  @Override public ConcurrentMap<Id, Object> state() {
    return state;
  }

  @Override public final Counter counter(Id id) {
    Counter c = getOrCreate(id, Counter.class, NoopCounter.INSTANCE, this::newCounter);
    return new SwapCounter(this, VERSION, c.id(), c);
  }

  @Override public final DistributionSummary distributionSummary(Id id) {
    DistributionSummary ds = getOrCreate(
        id,
        DistributionSummary.class,
        NoopDistributionSummary.INSTANCE,
        this::newDistributionSummary);
    return new SwapDistributionSummary(this, VERSION, ds.id(), ds);
  }

  @Override public final Timer timer(Id id) {
    Timer t = getOrCreate(id, Timer.class, NoopTimer.INSTANCE, this::newTimer);
    return new SwapTimer(this, VERSION, t.id(), t);
  }

  @Override public final Gauge gauge(Id id) {
    Gauge g = getOrCreate(id, Gauge.class, NoopGauge.INSTANCE, this::newGauge);
    return new SwapGauge(this, VERSION, g.id(), g);
  }

  @Override public final Gauge maxGauge(Id id) {
    Gauge g = getOrCreate(id, Gauge.class, NoopGauge.INSTANCE, this::newMaxGauge);
    return new SwapMaxGauge(this, VERSION, g.id(), g);
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
    // Typically means the user had an error with a call to createId when propagateWarnings
    // is false.
    if (id == NoopId.INSTANCE) {
      return dflt;
    }

    // Handle the normal processing and ensure exceptions are not propagated
    try {
      Preconditions.checkNotNull(id, "id");
      Id normId = normalizeId(id);
      Meter m = Utils.computeIfAbsent(meters, normId, i -> compute(factory.apply(i), dflt));
      if (!cls.isAssignableFrom(m.getClass())) {
        logTypeError(normId, cls, m.getClass());
        m = dflt;
      }
      return (T) m;
    } catch (Exception e) {
      propagate(e);
      return dflt;
    }
  }

  @Override public final Meter get(Id id) {
    try {
      return meters.get(normalizeId(id));
    } catch (Exception e) {
      propagate(e);
      return null;
    }
  }

  @Override public final Iterator<Meter> iterator() {
    return meters.values().iterator();
  }

  /**
   * Can be called by sub-classes to remove expired meters from the internal map.
   * The SwapMeter types that are returned will lookup a new copy on the next access.
   */
  protected void removeExpiredMeters() {
    int total = 0;
    int expired = 0;
    Iterator<Map.Entry<Id, Meter>> it = meters.entrySet().iterator();
    while (it.hasNext()) {
      ++total;
      Map.Entry<Id, Meter> entry = it.next();
      Meter m = entry.getValue();
      if (m.hasExpired()) {
        ++expired;
        it.remove();
      }
    }
    logger.debug("removed {} expired meters out of {} total", expired, total);
    cleanupCachedState();
  }

  /**
   * Cleanup any expired meter patterns stored in the state. It should only be used as
   * a cache so the entry should get recreated if needed.
   */
  private void cleanupCachedState() {
    int total = 0;
    int expired = 0;
    Iterator<Map.Entry<Id, Object>> it = state.entrySet().iterator();
    while (it.hasNext()) {
      ++total;
      Map.Entry<Id, Object> entry = it.next();
      Object obj = entry.getValue();
      if (obj instanceof Meter && ((Meter) obj).hasExpired()) {
        ++expired;
        it.remove();
      }
    }
    logger.debug("removed {} expired entries from cache out of {} total", expired, total);
  }

  /**
   * This can be called be sub-classes to reset all state for the registry. Typically this
   * should only be exposed for test registries as most users should not be able to reset the
   * state and interrupt metrics collection for the overall system.
   */
  protected void reset() {
    meters.clear();
    state.clear();
  }
}
