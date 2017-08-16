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
package com.netflix.spectator.api.patterns;

import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Meter;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Utils;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.ToDoubleFunction;

/**
 * Helper for configuring a gauge that will receive a value by regularly polling the
 * source in the background.
 *
 * <p>Example usage:</p>
 *
 * <pre>
 *   Registry registry = ...
 *   AtomicLong connections = PolledGauge.using(registry)
 *     .withName("server.currentConnections")
 *     .monitor(new AtomicLong());
 *
 *   // When a connection is added
 *   connections.incrementAndGet();
 *
 *   // When a connection is removed
 *   connections.decrementAndGet();
 * </pre>
 *
 * <p>Polling frequency will depend on the underlying registry implementation, but users should
 * assume it will be frequently checked and that the provided function is cheap. Users should
 * keep in mind that polling will not capture all activity, just sample it at some frequency.
 * For example, if monitoring a queue, then a gauge will only tell you the last sampled size
 * when the value is reported. If more details are needed, then use an alternative type
 * and ensure that all changes are reported when they occur.</p>
 *
 * <p>For example, consider tracking the number of currently established connections to a server.
 * Using a polled gauge will show the last sampled number when reported. An alternative would
 * be to report the number of connections to a {@link com.netflix.spectator.api.DistributionSummary}
 * every time a connection is added or removed. The distribution summary would provide more
 * accurate tracking such as max and average number of connections across an interval of time.
 * The polled gauge would not provide that level of detail.</p>
 *
 * <p>If multiple values are monitored with the same id, then the values will be aggregated and
 * the sum will be reported. For example, registering multiple gauges for active threads in
 * a thread pool with the same id would produce a value that is the overall number
 * of active threads. For other behaviors, manage it on the user side and avoid multiple
 * registrations.</p>
 */
public final class PolledGauge {

  private PolledGauge() {
  }

  /**
   * Return a builder for configuring a polled gauge reporting to the provided registry.
   *
   * @param registry
   *     Registry that will maintain the state and receive the sampled values for the
   *     configured gauge.
   * @return
   *     Builder for configuring a polled gauge.
   */
  public static IdBuilder<Builder> using(Registry registry) {
    return new IdBuilder<Builder>(registry) {
      @Override protected Builder createTypeBuilder(Id id) {
        return new Builder(registry, id);
      }
    };
  }

  /** Force the polling of all gauges associated with the registry. */
  public static void update(Registry registry) {
    registry.state().values().forEach(obj -> {
      if (obj instanceof GaugeTuple) {
        ((GaugeTuple) obj).doUpdate(registry);
      }
    });
  }

  /**
   * Explicitly disable polling for the gauge registered with {@code id}. This is optional
   * and is mostly used if it is desirable for the gauge to go away immediately. The polling
   * will stop automatically when the referred object is garbage collected. See
   * {@link Builder#monitor(Object, ToDoubleFunction)} for more information.
   */
  public static void remove(Id id) {

  }

  /**
   * Builder for configuring a polled gauge value.
   */
  public static final class Builder extends TagsBuilder<Builder> {

    private final Registry registry;
    private final Id baseId;
    private ScheduledExecutorService executor;

    /** Create a new instance. */
    Builder(Registry registry, Id baseId) {
      super();
      this.registry = registry;
      this.baseId = baseId;
    }

    /**
     * Set the executor to be used for polling the value. If not set, then the default
     * executor will be used which is limited to a single thread to minimize the worst
     * case resource usage for collecting the gauge data. Use a custom executor if more
     * resources are needed or if the polling operation is expensive.
     *
     * @return
     *     This builder instance to allow chaining of operations.
     */
    public Builder withExecutor(ScheduledExecutorService executor) {
      this.executor = executor;
      return this;
    }

    /**
     * Poll the value of the provided {@link Number}. The implementation provided must
     * be thread safe. The most common usages of this are to monitor instances of
     * {@link java.util.concurrent.atomic.AtomicInteger}
     * or {@link java.util.concurrent.atomic.AtomicLong}.
     *
     * @param number
     *     Thread-safe implementation of {@link Number} used to access the value.
     * @return
     *     The number that was passed in to allow the builder to be used inline as part
     *     of an assignment.
     */
    public <T extends Number> T monitor(T number) {
      return monitor(number, Number::doubleValue);
    }

    /**
     * Poll by executing {@code f(obj)} and reporting the returned value. The provided
     * function must be thread safe and cheap to execute. Expensive operations, including
     * any IO or network calls, should not be performed inline unless using a custom
     * executor by calling {@link #withExecutor(ScheduledExecutorService)}. Assume that the
     * function will be called frequently and may be called concurrently.
     *
     * A weak reference will be kept to {@code obj} so that monitoring the object will
     * not prevent garbage collection. The gauge will go away when {@code obj} is collected.
     * To explicitly disable polling call {@link #remove(Id)} with the same id used with
     * this builder.
     *
     * @param obj
     *     Object used to compute a value.
     * @param f
     *     Function that is applied on the value for the number.
     * @return
     *     The object that was passed in so the registration can be done as part of an assignment
     *     statement.
     */
    @SuppressWarnings("unchecked")
    public <T> T monitor(T obj, ToDoubleFunction<T> f) {
      final Id id = baseId.withTags(extraTags);
      final Gauge gauge = registry.gauge(id);
      final ValueTuple<T> tuple = new ValueTuple<>(gauge);

      ConcurrentMap<Id, Object> state = registry.state();
      Object c = Utils.computeIfAbsent(state, id, i -> tuple);
      if (!(c instanceof ValueTuple)) {
        Utils.propagateTypeError(registry, id, PolledGauge.class, c.getClass());
      } else {
        ValueTuple<T> t = (ValueTuple<T>) c;
        t.add(obj, f);
        t.schedule(registry, executor);
      }

      return obj;
    }

    /**
     * Poll the value of the provided {@link Collection}. The implementation provided must
     * be thread safe. Keep in mind that computing the size can be an expensive operation
     * for some collection types.
     *
     * @param collection
     *     Thread-safe implementation of {@link Collection}.
     * @return
     *     The collection that was passed in to allow the builder to be used inline as part
     *     of an assignment.
     */
    public <T extends Collection<?>> T monitorSize(T collection) {
      return monitor(collection, Collection::size);
    }

    /**
     * Poll the value of the provided {@link Map}. The implementation provided must
     * be thread safe. Keep in mind that computing the size can be an expensive operation
     * for some map types.
     *
     * @param map
     *     Thread-safe implementation of {@link Map}.
     * @return
     *     The collection that was passed in to allow the builder to be used inline as part
     *     of an assignment.
     */
    public <T extends Map<?, ?>> T monitorSize(T map) {
      return monitor(map, Map::size);
    }
  }

  /**
   * Provided for backwards compatibility to support the {@link Registry#register(Meter)}
   * method. Use the builder created with {@link #using(Registry)} instead.
   *
   * @deprecated This method only exists to allow for backwards compatibility and should
   * be considered an internal detail. Scheduled to be removed in 2.0.
   */
  @Deprecated
  public static void monitorMeter(Registry registry, Meter meter) {
    ConcurrentMap<Id, Object> state = registry.state();
    Object c = Utils.computeIfAbsent(state, meter.id(), MeterTuple::new);
    if (!(c instanceof MeterTuple)) {
      Utils.propagateTypeError(registry, meter.id(), MeterTuple.class, c.getClass());
    } else {
      MeterTuple t = (MeterTuple) c;
      t.add(meter);
      t.schedule(registry, null);
    }
  }

  /** Base class for gauge tuples used for bookkeeping. */
  abstract static class GaugeTuple {
    private boolean scheduled = false;

    /** Return the id for the gauge. */
    protected abstract Id id();

    /** Return the true if this gauge has expired. */
    protected abstract boolean hasExpired();

    /** Sample the gauge and send updates to the registry. */
    protected abstract void update(Registry registry);

    /**
     * Update the registry if this gauge is not expired, otherwise cleanup any state
     * associated with this gauge.
     */
    void doUpdate(Registry registry) {
      if (hasExpired()) {
        registry.state().remove(id());
      } else {
        update(registry);
      }
    }

    /** Schedule a task to regularly update the registry. */
    void schedule(Registry registry, ScheduledExecutorService executor) {
      if (!scheduled) {
        long delay = registry.config().gaugePollingFrequency().toMillis();
        WeakReference<GaugeTuple> tupleRef = new WeakReference<>(this);
        if (executor == null) {
          GaugePoller.schedule(tupleRef, delay, t -> t.update(registry));
        } else {
          GaugePoller.schedule(executor, tupleRef, delay, t -> t.update(registry));
        }
        scheduled = true;
      }
    }
  }

  /** Keep track of the object reference, counter, and other associated bookkeeping info. */
  static final class ValueTuple<T> extends GaugeTuple {
    private final Gauge gauge;
    private final ConcurrentLinkedQueue<RefPair<T>> pairs;

    /** Create new instance. */
    ValueTuple(Gauge gauge) {
      super();
      this.gauge = gauge;
      this.pairs = new ConcurrentLinkedQueue<>();
    }

    private void add(T obj, ToDoubleFunction<T> f) {
      pairs.add(new RefPair<>(obj, f));
    }

    @Override protected Id id() {
      return gauge.id();
    }

    @Override protected boolean hasExpired() {
      return pairs.isEmpty();
    }

    @Override protected void update(Registry registry) {
      double sum = Double.NaN;
      Iterator<RefPair<T>> iter = pairs.iterator();
      while (iter.hasNext()) {
        final RefPair<T> pair = iter.next();
        final T obj = pair.ref.get();
        if (obj != null) {
          double v = pair.f.applyAsDouble(obj);
          if (!Double.isNaN(v)) {
            sum = Double.isNaN(sum) ? v : sum + v;
          }
        } else {
          iter.remove();
        }
      }
      gauge.set(sum);
    }
  }

  /**
   * Pair consisting of weak reference to an object and a function to sample a numeric
   * value from the object.
   */
  static final class RefPair<T> {
    private final WeakReference<T> ref;
    private final ToDoubleFunction<T> f;

    /** Create new instance. */
    RefPair(T obj, ToDoubleFunction<T> f) {
      this.ref = new WeakReference<T>(obj);
      this.f = f;
    }
  }

  /** Keep track of a meter and associated metadata. */
  static final class MeterTuple extends GaugeTuple {
    private final Id id;
    private final ConcurrentLinkedQueue<Meter> queue;

    /** Create a new instance. */
    MeterTuple(Id id) {
      super();
      this.id = id;
      this.queue = new ConcurrentLinkedQueue<>();
    }

    /** Adds a meter to the set included in the aggregate. */
    void add(Meter m) {
      queue.add(m);
    }

    @Override protected Id id() {
      return id;
    }

    @Override protected boolean hasExpired() {
      return queue.isEmpty();
    }

    private Iterable<Measurement> measure() {
      Map<Id, Measurement> measurements = new HashMap<>();
      Iterator<Meter> iter = queue.iterator();
      while (iter.hasNext()) {
        Meter meter = iter.next();
        if (meter.hasExpired()) {
          iter.remove();
        } else {
          for (Measurement m : meter.measure()) {
            Measurement prev = measurements.get(m.id());
            if (prev == null) {
              measurements.put(m.id(), m);
            } else {
              double v = prev.value() + m.value();
              measurements.put(prev.id(), new Measurement(prev.id(), prev.timestamp(), v));
            }
          }
        }
      }
      return measurements.values();
    }

    @Override protected void update(Registry registry) {
      for (Measurement m : measure()) {
        registry.gauge(m.id()).set(m.value());
      }
    }
  }
}
