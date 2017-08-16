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

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Utils;

import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.ToLongFunction;

/**
 * Helper for mapping a monotonically increasing long value to a counter. Monotonic counters
 * are frequently used as a simple way for exposing the amount of change. In order to be
 * useful, they need to be polled frequently so the change can be measured regularly over
 * time. This class provides helper methods that will regularly poll the source and report
 * the deltas to a {@link Counter}.
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
 *     .monitor(executor, ThreadPoolExecutor::getCompletedTaskCount);
 * </pre>
 */
public final class MonotonicCounter {

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

  /**
   * Explicitly disable polling for the counter registered with {@code id}. This is optional
   * and is mostly used if it is desirable for the gauge to go away immediately. The polling
   * will stop automatically when the referred object is garbage collected. See
   * {@link Builder#monitor(Object, ToLongFunction)} for more information.
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
     * case resource usage for collecting the counter data. Use a custom executor if more
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
     * {@link java.util.concurrent.atomic.AtomicInteger},
     * {@link java.util.concurrent.atomic.AtomicLong}, or
     * {@link java.util.concurrent.atomic.LongAdder}.
     *
     * @param number
     *     Thread-safe implementation of {@link Number} used to access the value.
     * @return
     *     The number that was passed in to allow the builder to be used inline as part
     *     of an assignment.
     */
    public <T extends Number> T monitor(T number) {
      return monitor(number, Number::longValue);
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
    public <T> T monitor(T obj, ToLongFunction<T> f) {
      final Id id = baseId.withTags(extraTags);
      final Counter counter = registry.counter(id);
      final Tuple<T> tuple = new Tuple<>(counter, obj, f);

      ConcurrentMap<Id, Object> state = registry.state();
      Object c = Utils.computeIfAbsent(state, id, i -> tuple);
      if (!(c instanceof Tuple)) {
        Utils.propagateTypeError(registry, id, MonotonicCounter.class, c.getClass());
      } else {
        Tuple<T> t = (Tuple<T>) c;
        t.schedule(registry, executor);
      }

      return obj;
    }
  }

  /** Keep track of the object reference, counter, and other associated bookkeeping info. */
  static final class Tuple<T> {
    private final Counter counter;
    private final WeakReference<T> ref;
    private final ToLongFunction<T> f;
    private long prev;
    private boolean scheduled;

    /** Create new instance. */
    Tuple(Counter counter, T obj, ToLongFunction<T> f) {
      this.counter = counter;
      this.ref = new WeakReference<T>(obj);
      this.f = f;
      this.prev = f.applyAsLong(obj);
      this.scheduled = false;
    }

    private boolean isExpired() {
      return ref.get() == null;
    }

    private void update() {
      final T obj = ref.get();
      if (obj != null) {
        final long value = f.applyAsLong(obj);
        final long delta = value - prev;
        prev = value;
        if (delta > 0L) {
          counter.increment(delta);
        }
      }
    }

    private synchronized void schedule(Registry registry, ScheduledExecutorService executor) {
      if (!scheduled) {
        long delay = registry.config().gaugePollingFrequency().toMillis();
        WeakReference<Tuple<T>> tupleRef = new WeakReference<>(this);
        if (executor == null) {
          GaugePoller.schedule(tupleRef, delay, t -> updateCounter(registry, t));
        } else {
          GaugePoller.schedule(executor, tupleRef, delay, t -> updateCounter(registry, t));
        }
        scheduled = true;
      }
    }
  }

  /** Helper to update the counter with the delta or to remove the state if expired. */
  static void updateCounter(Registry registry, Tuple<?> tuple) {
    if (tuple.isExpired()) {
      registry.state().remove(tuple.counter.id());
    } else {
      tuple.update();
    }
  }

  private MonotonicCounter() {
  }
}
