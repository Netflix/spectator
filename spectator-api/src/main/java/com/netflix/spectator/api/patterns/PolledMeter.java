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
package com.netflix.spectator.api.patterns;

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Meter;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Utils;
import com.netflix.spectator.impl.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

/**
 * Helper for configuring a meter that will receive a value by regularly polling the
 * source in the background.
 *
 * <p>Example usage:</p>
 *
 * <pre>
 *   Registry registry = ...
 *   AtomicLong connections = PolledMeter.using(registry)
 *     .withName("server.currentConnections")
 *     .monitorValue(new AtomicLong());
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
 * For example, if monitoring a queue, then a meter will only tell you the last sampled size
 * when the value is reported. If more details are needed, then use an alternative type
 * and ensure that all changes are reported when they occur.</p>
 *
 * <p>For example, consider tracking the number of currently established connections to a server.
 * Using a polled meter will show the last sampled number when reported. An alternative would
 * be to report the number of connections to a {@link com.netflix.spectator.api.DistributionSummary}
 * every time a connection is added or removed. The distribution summary would provide more
 * accurate tracking such as max and average number of connections across an interval of time.
 * The polled meter would not provide that level of detail.</p>
 *
 * <p>If multiple values are monitored with the same id, then the values will be aggregated and
 * the sum will be reported. For example, registering multiple meters for active threads in
 * a thread pool with the same id would produce a value that is the overall number
 * of active threads. For other behaviors, manage it on the user side and avoid multiple
 * registrations.</p>
 */
public final class PolledMeter {

  private static final Logger LOGGER = LoggerFactory.getLogger(PolledMeter.class);

  private PolledMeter() {
  }

  /**
   * Return a builder for configuring a polled meter reporting to the provided registry.
   *
   * @param registry
   *     Registry that will maintain the state and receive the sampled values for the
   *     configured meter.
   * @return
   *     Builder for configuring a polled meter.
   */
  public static IdBuilder<Builder> using(Registry registry) {
    return new IdBuilder<Builder>(registry) {
      @Override protected Builder createTypeBuilder(Id id) {
        return new Builder(registry, id);
      }
    };
  }

  /** Force the polling of all meters associated with the registry. */
  public static void update(Registry registry) {
    Iterator<Map.Entry<Id, Object>> iter = registry.state().entrySet().iterator();
    while (iter.hasNext()) {
      Map.Entry<Id, Object> entry = iter.next();
      if (entry.getValue() instanceof AbstractMeterState) {
        AbstractMeterState tuple = (AbstractMeterState) entry.getValue();
        tuple.doUpdate(registry);
        if (tuple.hasExpired()) {
          iter.remove();
        }
      }
    }
  }

  /**
   * Explicitly disable polling for the meter registered with {@code id}. This is optional
   * and is mostly used if it is desirable for the meter to go away immediately. The polling
   * will stop automatically when the referred object is garbage collected. See
   * {@link Builder#monitorValue(Object, ToDoubleFunction)} for more information.
   */
  public static void remove(Registry registry, Id id) {
    Object obj = registry.state().get(id);
    if (obj instanceof AbstractMeterState) {
      ((AbstractMeterState) obj).cleanup(registry);
    }
  }

  /**
   * Poll by executing {@code f.accept(registry)} using the default thread pool for polling
   * gauges at the default frequency configured for the registry. If more customization is
   * needed, then it can be done by using a {@code ScheduledExecutorService} directly and
   * updating meters in the registry. The common use-case is being able to update multiple
   * meters based on sampling a single time.
   *
   * <p>The provided function must be thread safe and cheap to execute. Expensive operations,
   * including any IO or network calls, should not be performed inline. Assume that the function
   * will be called frequently and may be called concurrently.
   *
   * @param registry
   *     Registry that will maintain the state and receive the sampled values for the
   *     configured meter.
   * @param f
   *     Function to call to update the registry.
   * @return
   *     Future that can be used to cancel the polling.
   */
  public static ScheduledFuture<?> poll(Registry registry, Runnable f) {
    // Call once to initialize meters and quickly fail if something is broken
    // with the function
    f.run();
    long delay = registry.config().gaugePollingFrequency().toMillis();
    return GaugePoller.schedule(delay, f);
  }

  /**
   * Builder for configuring a polled meter value.
   */
  public static final class Builder extends TagsBuilder<Builder> {

    private final Registry registry;
    private final Id baseId;
    private ScheduledExecutorService executor;
    private long delay;

    /** Create a new instance. */
    Builder(Registry registry, Id baseId) {
      super();
      this.registry = registry;
      this.baseId = baseId;
      this.delay = registry.config().gaugePollingFrequency().toMillis();
    }

    /**
     * Set the executor to be used for polling the value. If not set, then the default
     * executor will be used which is limited to a single thread to minimize the worst
     * case resource usage for collecting the meter data. Use a custom executor if more
     * resources are needed or if the polling operation is expensive.
     *
     * @return
     *     This builder instance to allow chaining of operations.
     */
    public Builder scheduleOn(ScheduledExecutorService executor) {
      this.executor = executor;
      return this;
    }

    /**
     * Set the delay at which the value should be refreshed. If not set, then
     * the default value will be the gauge polling frequency set in the registry
     * configuration.
     *
     * @see
     *     com.netflix.spectator.api.RegistryConfig#gaugePollingFrequency()
     *
     * @return
     *     This builder instance to allow chaining of operations.
     */
    public Builder withDelay(Duration delay) {
      this.delay = delay.toMillis();
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
    public <T extends Number> T monitorValue(T number) {
      try {
        return monitorValue(number, Number::doubleValue);
      } catch (Exception e) {
        registry.propagate(e);
        return number;
      }
    }

    /**
     * Poll by executing {@code f(obj)} and reporting the returned value. The provided
     * function must be thread safe and cheap to execute. Expensive operations, including
     * any IO or network calls, should not be performed inline unless using a custom
     * executor by calling {@link #scheduleOn(ScheduledExecutorService)}. Assume that the
     * function will be called frequently and may be called concurrently.
     *
     * <p>A weak reference will be kept to {@code obj} so that monitoring the object will
     * not prevent garbage collection. The meter will go away when {@code obj} is collected.
     * If {@code obj} is null, then it will be treated as an already collected object and a
     * warning will be logged.</p>
     *
     * <p>To explicitly disable polling call {@link #remove(Registry, Id)} with the same id used
     * with this builder.</p>
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
    public <T> T monitorValue(T obj, ToDoubleFunction<T> f) {
      final Id id = baseId.withTags(extraTags);
      if (obj == null) {
        registry.propagate(new IllegalArgumentException(
            "obj is null for PolledMeter (id = " + id + "), no data will be reported. "
                + "See the API docs for monitorValue for guidance on how to fix the code."));
        return null;
      }
      final Gauge gauge = registry.gauge(id);
      final ValueState<T> tuple = new ValueState<>(gauge);

      ConcurrentMap<Id, Object> state = registry.state();
      Object c = Utils.computeIfAbsent(state, id, i -> tuple);
      if (!(c instanceof ValueState)) {
        Utils.propagateTypeError(registry, id, PolledMeter.class, c.getClass());
      } else {
        ValueState<T> t = (ValueState<T>) c;
        t.add(obj, f);
        t.schedule(registry, executor, delay);
      }

      return obj;
    }

    /**
     * Poll by executing {@code supplier.getAsDouble()} and reporting the returned value. The
     * provided function must be thread safe and cheap to execute. Expensive operations, including
     * any IO or network calls, should not be performed inline unless using a custom executor by
     * calling {@link #scheduleOn(ScheduledExecutorService)}. Assume that the function will be
     * called frequently and may be called concurrently.
     *
     * <p>This helper should only be used with static method references. It will keep a strong
     * reference to any enclosed objects and they will never get garbage collected unless removed
     * explicitly by the user.</p>
     *
     * <p>To explicitly disable polling call {@link #remove(Registry, Id)} with the same id used
     * with this builder.</p>
     *
     * @param supplier
     *     Supplier that will be called to access the value.
     */
    public void monitorStaticMethodValue(DoubleSupplier supplier) {
      monitorValue(supplier, DoubleSupplier::getAsDouble);
    }

    /**
     * Poll the value of the provided {@link Number} and update a counter with the delta
     * since the last time the value was sampled. The implementation provided must
     * be thread safe. The most common usages of this are to monitor instances of
     * {@link java.util.concurrent.atomic.AtomicInteger},
     * {@link java.util.concurrent.atomic.AtomicLong}, or
     * {@link java.util.concurrent.atomic.LongAdder}. For more information see
     * {@link #monitorMonotonicCounter(Object, ToLongFunction)}.
     *
     * @param number
     *     Thread-safe implementation of {@link Number} used to access the value.
     * @return
     *     The number that was passed in to allow the builder to be used inline as part
     *     of an assignment.
     */
    public <T extends Number> T monitorMonotonicCounter(T number) {
      return monitorMonotonicCounter(number, Number::longValue);
    }

    /**
     * Map a monotonically increasing long or int value to a counter. Monotonic counters
     * are frequently used as a simple way for exposing the amount of change. In order to be
     * useful, they need to be polled frequently so the change can be measured regularly over
     * time.
     *
     * <p>Example monotonic counters provided by the JDK:</p>
     *
     * <ul>
     *   <li>{@link java.util.concurrent.ThreadPoolExecutor#getCompletedTaskCount()}</li>
     *   <li>{@link java.lang.management.GarbageCollectorMXBean#getCollectionCount()}</li>
     * </ul>
     *
     * <p>Example usage:</p>
     *
     * <pre>
     *   Registry registry = ...
     *   MonotonicCounter.using(registry)
     *     .withName("pool.completedTasks")
     *     .monitorMonotonicCounter(executor, ThreadPoolExecutor::getCompletedTaskCount);
     * </pre>
     *
     * <p>The value is polled by executing {@code f(obj)} and a counter will be updated with
     * the delta since the last time the value was sampled. The provided function must be
     * thread safe and cheap to execute. Expensive operations, including any IO or network
     * calls, should not be performed inline unless using a custom executor by calling
     * {@link #scheduleOn(ScheduledExecutorService)}. Assume that the function will be called
     * frequently and may be called concurrently.</p>
     *
     * <p>A weak reference will be kept to {@code obj} so that monitoring the object will
     * not prevent garbage collection. The meter will go away when {@code obj} is collected.
     * If {@code obj} is null, then it will be treated as an already collected object and a
     * warning will be logged.</p>
     *
     * <p>To explicitly disable polling call {@link #remove(Registry, Id)} with the same id used
     * with this builder.</p>
     *
     * @param obj
     *     Object used to compute a value.
     * @param f
     *     Function that is applied on the value for the number.
     * @return
     *     The object that was passed in so the registration can be done as part of an assignment
     *     statement.
     */
    public <T> T monitorMonotonicCounter(T obj, ToLongFunction<T> f) {
      return monitorMonotonicCounterDouble(obj, f::applyAsLong);
    }

    /**
     * Map a monotonically increasing double value to a counter. Monotonic counters
     * are frequently used as a simple way for exposing the amount of change. In order to be
     * useful, they need to be polled frequently so the change can be measured regularly over
     * time.
     *
     * <p>The value is polled by executing {@code f(obj)} and a counter will be updated with
     * the delta since the last time the value was sampled. The provided function must be
     * thread safe and cheap to execute. Expensive operations, including any IO or network
     * calls, should not be performed inline unless using a custom executor by calling
     * {@link #scheduleOn(ScheduledExecutorService)}. Assume that the function will be called
     * frequently and may be called concurrently.</p>
     *
     * <p>A weak reference will be kept to {@code obj} so that monitoring the object will
     * not prevent garbage collection. The meter will go away when {@code obj} is collected.
     * If {@code obj} is null, then it will be treated as an already collected object and a
     * warning will be logged.</p>
     *
     * <p>To explicitly disable polling call {@link #remove(Registry, Id)} with the same id used
     * with this builder.</p>
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
    public <T> T monitorMonotonicCounterDouble(T obj, ToDoubleFunction<T> f) {
      final Id id = baseId.withTags(extraTags);
      if (obj == null) {
        registry.propagate(new IllegalArgumentException(
            "obj is null for PolledMeter (id = " + id + "), no data will be reported. "
                + "See the API docs for monitorMonotonicCounter for guidance on how to "
                + "fix the code."));
        return null;
      }
      final Counter counter = registry.counter(id);
      final CounterState<T> tuple = new CounterState<>(counter);

      ConcurrentMap<Id, Object> state = registry.state();
      Object c = Utils.computeIfAbsent(state, id, i -> tuple);
      if (!(c instanceof CounterState)) {
        Utils.propagateTypeError(registry, id, PolledMeter.class, c.getClass());
      } else {
        CounterState<T> t = (CounterState<T>) c;
        t.add(obj, f);
        t.schedule(registry, executor, delay);
      }

      return obj;
    }

    /**
     * Map a monotonically increasing long or int value to a counter. Monotonic counters
     * are frequently used as a simple way for exposing the amount of change. In order to be
     * useful, they need to be polled frequently so the change can be measured regularly over
     * time.
     *
     * <p>Poll by executing {@code supplier.getAsLong()} and reporting the returned value. The
     * provided function must be thread safe and cheap to execute. Expensive operations, including
     * any IO or network calls, should not be performed inline unless using a custom executor by
     * calling {@link #scheduleOn(ScheduledExecutorService)}. Assume that the function will be
     * called frequently and may be called concurrently.</p>
     *
     * <p>This helper should only be used with static method references. It will keep a strong
     * reference to any enclosed objects and they will never get garbage collected unless removed
     * explicitly by the user.</p>
     *
     * <p>To explicitly disable polling call {@link #remove(Registry, Id)} with the same id used
     * with this builder.</p>
     *
     * @param supplier
     *     Supplier that will be called to access the value.
     */
    public void monitorStaticMethodMonotonicCounter(LongSupplier supplier) {
      monitorMonotonicCounter(supplier, LongSupplier::getAsLong);
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
      return monitorValue(collection, Collection::size);
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
      return monitorValue(map, Map::size);
    }
  }

  /**
   * Provided for backwards compatibility to support the {@link Registry#register(Meter)}
   * method. Use the builder created with {@link #using(Registry)} instead.
   *
   * @deprecated This method only exists to allow for backwards compatibility and should
   * be considered an internal detail. This method is scheduled for removal in a future release.
   */
  @Deprecated
  public static void monitorMeter(Registry registry, Meter meter) {
    Preconditions.checkNotNull(registry, "registry");
    try {
      Preconditions.checkNotNull(registry, "meter");
      ConcurrentMap<Id, Object> state = registry.state();
      Object c = Utils.computeIfAbsent(state, meter.id(), MeterState::new);
      if (!(c instanceof MeterState)) {
        Utils.propagateTypeError(registry, meter.id(), MeterState.class, c.getClass());
      } else {
        MeterState t = (MeterState) c;
        t.add(meter);
        long delay = registry.config().gaugePollingFrequency().toMillis();
        t.schedule(registry, null, delay);
      }
    } catch (Exception e) {
      registry.propagate(e);
    }
  }

  /** Base class for meter state used for bookkeeping. */
  abstract static class AbstractMeterState {
    private Future<?> future = null;

    /** Return the id for the meter. */
    protected abstract Id id();

    /** Return the true if this meter has expired. */
    protected abstract boolean hasExpired();

    /** Sample the meter and send updates to the registry. */
    protected abstract void update(Registry registry);

    /** Cleanup any state associated with this meter and stop polling. */
    void cleanup(Registry registry) {
      if (future != null) {
        future.cancel(true);
      }
      registry.state().remove(id());
    }

    /**
     * Update the registry if this meter is not expired, otherwise cleanup any state
     * associated with this meter.
     */
    @SuppressWarnings("PMD.AvoidCatchingThrowable")
    void doUpdate(Registry registry) {
      if (hasExpired()) {
        cleanup(registry);
      } else {
        try {
          update(registry);
        } catch (Throwable t) {
          LOGGER.trace("uncaught exception from gauge function for [{}]", id(), t);
          throw t;
        }
      }
    }

    /** Schedule a task to regularly update the registry. */
    void schedule(Registry registry, ScheduledExecutorService executor, long delay) {
      if (future == null) {
        WeakReference<AbstractMeterState> tupleRef = new WeakReference<>(this);
        if (executor == null) {
          future = GaugePoller.schedule(tupleRef, delay, t -> t.doUpdate(registry));
        } else {
          future = GaugePoller.schedule(executor, tupleRef, delay, t -> t.doUpdate(registry));
        }
      }
    }
  }

  /** Keep track of the object reference, counter, and other associated bookkeeping info. */
  static final class ValueState<T> extends AbstractMeterState {
    private final Gauge gauge;
    private final ConcurrentLinkedQueue<ValueEntry<T>> pairs;

    /** Create new instance. */
    ValueState(Gauge gauge) {
      super();
      this.gauge = gauge;
      this.pairs = new ConcurrentLinkedQueue<>();
    }

    private void add(T obj, ToDoubleFunction<T> f) {
      pairs.add(new ValueEntry<>(obj, f));
    }

    @Override protected Id id() {
      return gauge.id();
    }

    @Override protected boolean hasExpired() {
      return pairs.isEmpty();
    }

    @Override protected void update(Registry registry) {
      double sum = Double.NaN;
      Iterator<ValueEntry<T>> iter = pairs.iterator();
      while (iter.hasNext()) {
        final ValueEntry<T> pair = iter.next();
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
      if (pairs.isEmpty()) {
        LOGGER.trace("gauge [{}] has expired", gauge.id());
      }
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace("setting gauge [{}] to {}", gauge.id(), sum);
      }
      gauge.set(sum);
    }
  }

  /**
   * Pair consisting of weak reference to an object and a function to sample a numeric
   * value from the object.
   */
  static final class ValueEntry<T> {
    private final WeakReference<T> ref;
    private final ToDoubleFunction<T> f;

    /** Create new instance. */
    ValueEntry(T obj, ToDoubleFunction<T> f) {
      this.ref = new WeakReference<>(obj);
      this.f = f;
    }
  }

  /** Keep track of a meter and associated metadata. */
  static final class MeterState extends AbstractMeterState {
    private final Id id;
    private final ConcurrentLinkedQueue<Meter> queue;

    /** Create a new instance. */
    MeterState(Id id) {
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
      if (queue.isEmpty()) {
        LOGGER.trace("meter [{}] has expired", id);
      }
      return measurements.values();
    }

    @Override protected void update(Registry registry) {
      for (Measurement m : measure()) {
        if (LOGGER.isTraceEnabled()) {
          LOGGER.trace("setting gauge [{}] to {}", m.id(), m.value());
        }
        registry.gauge(m.id()).set(m.value());
      }
    }
  }

  /** Keep track of the object reference, counter, and other associated bookkeeping info. */
  static final class CounterState<T> extends AbstractMeterState {
    private final Counter counter;
    private final ConcurrentLinkedQueue<CounterEntry<T>> entries;

    /** Create new instance. */
    CounterState(Counter counter) {
      super();
      this.counter = counter;
      this.entries = new ConcurrentLinkedQueue<>();
    }

    private void add(T obj, ToDoubleFunction<T> f) {
      entries.add(new CounterEntry<>(obj, f));
    }

    @Override protected Id id() {
      return counter.id();
    }

    @Override protected boolean hasExpired() {
      return entries.isEmpty();
    }

    @Override protected void update(Registry registry) {
      Iterator<CounterEntry<T>> iter = entries.iterator();
      while (iter.hasNext()) {
        CounterEntry<T> state = iter.next();
        if (state.ref.get() == null) {
          iter.remove();
        } else {
          state.update(counter);
        }
      }
      if (entries.isEmpty()) {
        LOGGER.trace("monotonic counter [{}] has expired", id());
      }
    }
  }

  /** State for counter entry. */
  static final class CounterEntry<T> {
    private final WeakReference<T> ref;
    private final ToDoubleFunction<T> f;
    private double previous;

    /** Create new instance. */
    CounterEntry(T obj, ToDoubleFunction<T> f) {
      this.ref = new WeakReference<>(obj);
      this.f = f;
      this.previous = f.applyAsDouble(obj);
    }

    private void update(Counter counter) {
      T obj = ref.get();
      if (obj != null) {
        double current = f.applyAsDouble(obj);
        if (current > previous) {
          final double delta = current - previous;
          if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("incrementing counter [{}] by {}", counter.id(), delta);
          }
          counter.add(delta);
        } else if (LOGGER.isTraceEnabled()) {
          LOGGER.trace("no update to counter [{}]: previous = {}, current = {}",
              counter.id(), previous, current);
        }
        previous = current;
      }
    }
  }
}
