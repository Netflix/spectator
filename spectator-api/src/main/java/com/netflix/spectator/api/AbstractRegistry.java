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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;

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
  private final ConcurrentHashMap<Id, Meter> gauges;
  private final ConcurrentHashMap<Id, Object> state;

  private final Semaphore pollSem = new Semaphore(1);

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
    this.gauges = new ConcurrentHashMap<>();
    this.state = new ConcurrentHashMap<>();
    GaugePoller.schedule(
        new WeakReference<>(this),
        config.gaugePollingFrequency().toMillis(),
        AbstractRegistry::pollGauges);
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

  private void handleGaugeException(Id id, Throwable t) {
    logger.warn("dropping gauge [{}], exception occurred when polling value", id, t);
    gauges.remove(id);
  }

  /**
   * Lambda passed to {@link GaugePoller} to avoid having a strong reference to this
   * registry that would prevent garbage collection.
   */
  private static void pollGauges(Registry r) {
    ((AbstractRegistry) r).pollGauges();
  }

  /** Poll the values from all registered gauges. */
  @SuppressWarnings("PMD")
  void pollGauges() {
    if (pollSem.tryAcquire()) {
      try {
        for (Map.Entry<Id, Meter> e : gauges.entrySet()) {
          Id id = e.getKey();
          Meter meter = e.getValue();
          try {
            if (!meter.hasExpired()) {
              for (Measurement m : meter.measure()) {
                gauge(m.id()).set(m.value());
              }
            }
          } catch (StackOverflowError t) {
            handleGaugeException(id, t);
          } catch (VirtualMachineError | ThreadDeath t) {
            // Avoid catching OutOfMemoryError and other serious problems in the next
            // catch block.
            throw t;
          } catch (Throwable t) {
            // The sampling is calling user functions and therefore we cannot
            // make any guarantees they are well-behaved. We catch most Throwables with
            // the exception of some VM errors and drop the gauge.
            handleGaugeException(id, t);
          }
        }
      } finally {
        pollSem.release();
      }
    }
  }

  @Override public void register(Meter meter) {
    Meter aggr = (gauges.size() >= config.maxNumberOfMeters())
      ? meters.get(meter.id())
      : Utils.computeIfAbsent(gauges, meter.id(), AggrMeter::new);
    if (aggr != null) {
      addToAggr(aggr, meter);
    }
  }

  @Override public ConcurrentMap<Id, Object> state() {
    return state;
  }

  @Override public final Counter counter(Id id) {
    try {
      Preconditions.checkNotNull(id, "id");
      Meter m = Utils.computeIfAbsent(meters, id, i -> compute(newCounter(i), NoopCounter.INSTANCE));
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
      Meter m = Utils.computeIfAbsent(meters, id, i ->
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
      Preconditions.checkNotNull(id, "id");
      Meter m = Utils.computeIfAbsent(meters, id, i -> compute(newTimer(i), NoopTimer.INSTANCE));
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

  @Override public final Gauge gauge(Id id) {
    try {
      Preconditions.checkNotNull(id, "id");
      Meter m = Utils.computeIfAbsent(meters, id, i -> compute(newGauge(i), NoopGauge.INSTANCE));
      if (!(m instanceof Gauge)) {
        logTypeError(id, Gauge.class, m.getClass());
        m = NoopGauge.INSTANCE;
      }
      return (Gauge) m;
    } catch (Exception e) {
      propagate(e);
      return NoopGauge.INSTANCE;
    }
  }

  @Override public final Meter get(Id id) {
    return meters.get(id);
  }

  @Override public final Iterator<Meter> iterator() {
    // Force update of gauges before traversing values
    pollGauges();
    return meters.values().iterator();
  }
}
