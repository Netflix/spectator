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
import com.netflix.spectator.impl.GaugePoller;

import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentMap;
import java.util.function.ToLongFunction;

/**
 * Helper for mapping a monotonically increasing long value to a counter. Monotonic counters
 * are frequently used as a simple way for exposing the amount of change. In order to be
 * useful, they need to be polled frequently so the change can be measured regularly over
 * time. This class provides helper methods that will regularly poll the source and report
 * the deltas to a {@link Counter}.
 *
 * Example monotonic counters provided by the JDK:
 *
 * <ul>
 *   <li>{@link java.util.concurrent.ThreadPoolExecutor#getCompletedTaskCount()}</li>
 *   <li>{@link com.sun.management.OperatingSystemMXBean#getProcessCpuTime()}</li>
 * </ul>
 */
public final class MonotonicCounter {

  /**
   * Tells the registry to regularly poll the number and report the delta to a counter with
   * the given id. The registry will keep a weak reference to the number so it will not prevent
   * garbage collection. The number implementation used should be thread safe. Common examples
   * are {@link java.util.concurrent.atomic.AtomicLong} and
   * {@link java.util.concurrent.atomic.LongAdder}. For more information, see
   * {@link #monitorValue(Registry, Id, Object, ToLongFunction)}.
   *
   * @param registry
   *     Registry to use for maintaining state.
   * @param id
   *     Identifier for the metric being registered.
   * @param number
   *     Thread-safe implementation of {@link Number} used to access the value.
   * @return
   *     The number that was passed in so the registration can be done as part of an assignment
   *     statement.
   */
  public static <T extends Number> T monitorNumber(Registry registry, Id id, T number) {
    return monitorValue(registry, id, number, Number::longValue);
  }

  /**
   * Tells the registry to regularly poll the object using the provided function and report
   * the delta to a counger with the given id. The registry will keep a weak reference to the
   * object so it will not prevent garbage collection.
   *
   * Applying {@code f} on the object should be thread safe and cheap to execute. <b>Never
   * perform computationally expensive or potentially long running tasks such as disk or network
   * calls inline.</b>
   *
   * @param registry
   *     Registry to use for maintaining state.
   * @param id
   *     Identifier for the metric being registered.
   * @param obj
   *     Object used to compute a value.
   * @param f
   *     Function that is applied on the value for the number.
   * @return
   *     The number that was passed in so the registration can be done as part of an assignment
   *     statement.
   */
  public static <T> T monitorValue(Registry registry, Id id, T obj, ToLongFunction<T> f) {
    Tuple<T> tuple = new Tuple<>(registry.counter(id), obj, f);
    return monitor(registry, tuple, id, obj);
  }

  private static <T> T monitor(Registry registry, Tuple<T> tuple, Id id, T value) {
    ConcurrentMap<Id, Object> state = registry.state();
    Object c = Utils.computeIfAbsent(state, id, i -> tuple);
    if (!(c instanceof Tuple)) {
      Utils.propagateTypeError(registry, id, MonotonicCounter.class, c.getClass());
    } else {
      ((Tuple<?>) c).schedule(registry);
    }
    return value;
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

    private synchronized void schedule(Registry registry) {
      if (!scheduled) {
        long delay = registry.config().gaugePollingFrequency().toMillis();
        WeakReference<Tuple<T>> tupleRef = new WeakReference<>(this);
        GaugePoller.schedule(tupleRef, delay, t -> updateCounter(registry, t));
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
