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

import com.netflix.spectator.impl.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;


/**
 * Wraps a registry and provides additional helper methods to make it easier to use.
 */
public final class ExtendedRegistry implements Registry {

  private static final Logger LOGGER = LoggerFactory.getLogger(ExtendedRegistry.class);

  private final Registry impl;

  /** Create a new instance. */
  public ExtendedRegistry(Registry impl) {
    this.impl = Preconditions.checkNotNull(impl, "impl");
  }

  /** Returns the underlying registry implementation that is being wrapped. */
  public Registry underlying() {
    return impl;
  }

  /** {@inheritDoc} */
  @Override
  public Clock clock() {
    return impl.clock();
  }

  /** {@inheritDoc} */
  @Override
  public Id createId(String name) {
    return impl.createId(name);
  }

  /** {@inheritDoc} */
  @Override
  public Id createId(String name, Iterable<Tag> tags) {
    return impl.createId(name, tags);
  }

  /** {@inheritDoc} */
  @Override
  public void register(Meter meter) {
    impl.register(meter);
  }

  /** {@inheritDoc} */
  @Override
  public Counter counter(Id id) {
    return impl.counter(id);
  }

  /** {@inheritDoc} */
  @Override
  public DistributionSummary distributionSummary(Id id) {
    return impl.distributionSummary(id);
  }

  /** {@inheritDoc} */
  @Override
  public Timer timer(Id id) {
    return impl.timer(id);
  }

  /** {@inheritDoc} */
  @Override
  public Meter get(Id id) {
    return impl.get(id);
  }

  /** {@inheritDoc} */
  @Override
  public Iterator<Meter> iterator() {
    return impl.iterator();
  }

  /** {@inheritDoc} */
  @Override
  public void addListener(RegistryListener listener) {
    impl.addListener(listener);
  }

  /** {@inheritDoc} */
  @Override
  public void removeListener(RegistryListener listener) {
    impl.removeListener(listener);
  }

  /////////////////////////////////////////////////////////////////
  // Additional helper methods below

  private Iterable<Tag> toIterable(String[] tags) {
    if (tags.length % 2 == 1) {
      throw new IllegalArgumentException("size must be even, it is a set of key=value pairs");
    }
    TagList ts = TagList.EMPTY;
    for (int i = 0; i < tags.length; i += 2) {
      ts = new TagList(tags[i], tags[i + 1], ts);
    }
    return ts;
  }

  /**
   * Creates an identifier for a meter.
   *
   * @param name
   *     Description of the measurement that is being collected.
   * @param tags
   *     Other dimensions that can be used to classify the measurement.
   * @return
   *     Identifier for a meter.
   */
  public Id createId(String name, String... tags) {
    return impl.createId(name, toIterable(tags));
  }

  /**
   * Measures the rate of some activity.
   *
   * @param name
   *     Description of the measurement that is being collected.
   * @return
   *     Counter instance with the corresponding id.
   */
  public Counter counter(String name) {
    return impl.counter(impl.createId(name));
  }

  /**
   * Measures the rate of some activity.
   *
   * @param name
   *     Description of the measurement that is being collected.
   * @param tags
   *     Other dimensions that can be used to classify the measurement.
   * @return
   *     Counter instance with the corresponding id.
   */
  public Counter counter(String name, Iterable<Tag> tags) {
    return impl.counter(impl.createId(name, tags));
  }

  /**
   * Measures the rate of some activity.
   *
   * @param name
   *     Description of the measurement that is being collected.
   * @param tags
   *     Other dimensions that can be used to classify the measurement.
   * @return
   *     Counter instance with the corresponding id.
   */
  public Counter counter(String name, String... tags) {
    return impl.counter(impl.createId(name, toIterable(tags)));
  }

  /**
   * Measures the sample distribution of events.
   *
   * @param name
   *     Description of the measurement that is being collected.
   * @return
   *     Summary instance with the corresponding id.
   */
  public DistributionSummary distributionSummary(String name) {
    return impl.distributionSummary(impl.createId(name));
  }

  /**
   * Measures the sample distribution of events.
   *
   * @param name
   *     Description of the measurement that is being collected.
   * @param tags
   *     Other dimensions that can be used to classify the measurement.
   * @return
   *     Summary instance with the corresponding id.
   */
  public DistributionSummary distributionSummary(String name, Iterable<Tag> tags) {
    return impl.distributionSummary(impl.createId(name, tags));
  }

  /**
   * Measures the sample distribution of events.
   *
   * @param name
   *     Description of the measurement that is being collected.
   * @param tags
   *     Other dimensions that can be used to classify the measurement.
   * @return
   *     Summary instance with the corresponding id.
   */
  public DistributionSummary distributionSummary(String name, String... tags) {
    return impl.distributionSummary(impl.createId(name, toIterable(tags)));
  }

  /**
   * Measures the time taken for short tasks.
   *
   * @param name
   *     Description of the measurement that is being collected.
   * @return
   *     Timer instance with the corresponding id.
   */
  public Timer timer(String name) {
    return impl.timer(impl.createId(name));
  }

  /**
   * Measures the time taken for short tasks.
   *
   * @param name
   *     Description of the measurement that is being collected.
   * @param tags
   *     Other dimensions that can be used to classify the measurement.
   * @return
   *     Timer instance with the corresponding id.
   */
  public Timer timer(String name, Iterable<Tag> tags) {
    return impl.timer(impl.createId(name, tags));
  }

  /**
   * Measures the time taken for short tasks.
   *
   * @param name
   *     Description of the measurement that is being collected.
   * @param tags
   *     Other dimensions that can be used to classify the measurement.
   * @return
   *     Timer instance with the corresponding id.
   */
  public Timer timer(String name, String... tags) {
    return impl.timer(impl.createId(name, toIterable(tags)));
  }

  /**
   * Measures the time taken for long tasks.
   *
   * @param id
   *     Identifier for the metric being registered.
   * @return
   *     Timer instance with the corresponding id.
   */
  public LongTaskTimer longTaskTimer(Id id) {
    LongTaskTimer taskTimer = new DefaultLongTaskTimer(clock(), id);
    impl.register(taskTimer); // the AggrMeter has the right semantics for these type of timers
    return taskTimer;
  }

  /**
   * Measures the time taken for long tasks.
   *
   * @param name
   *     Description of the measurement that is being collected.
   * @return
   *     Timer instance with the corresponding id.
   */
  public LongTaskTimer longTaskTimer(String name) {
    return longTaskTimer(createId(name));
  }

  /**
   * Measures the time taken for long tasks.
   *
   * @param name
   *     Description of the measurement that is being collected.
   * @param tags
   *     Other dimensions that can be used to classify the measurement.
   * @return
   *     Timer instance with the corresponding id.
   */
  public LongTaskTimer longTaskTimer(String name, Iterable<Tag> tags) {
    return longTaskTimer(createId(name, tags));
  }

  /**
   * Measures the time taken for long tasks.
   *
   * @param name
   *     Description of the measurement that is being collected.
   * @param tags
   *     Other dimensions that can be used to classify the measurement.
   * @return
   *     Timer instance with the corresponding id.
   */
  public LongTaskTimer longTaskTimer(String name, String... tags) {
    return longTaskTimer(createId(name, toIterable(tags)));
  }

  /**
   * Register a gauge that reports the value of the {@link java.lang.Number}. The registration
   * will keep a weak reference to the number so it will not prevent garbage collection.
   * The number implementation used should be thread safe.
   *
   * @param id
   *     Identifier for the metric being registered.
   * @param number
   *     Thread-safe implementation of {@link Number} used to access the value.
   * @return
   *     The number that was passed in so the registration can be done as part of an assignment
   *     statement.
   */
  public <T extends Number> T gauge(Id id, T number) {
    return gauge(id, number, Functions.IDENTITY);
  }

  /**
   * Register a gauge that reports the value of the {@link java.lang.Number}. See
   * {@link #gauge(Id, Number)}.
   *
   * @param name
   *     Name of the metric being registered.
   * @param number
   *     Thread-safe implementation of {@link Number} used to access the value.
   * @return
   *     The number that was passed in so the registration can be done as part of an assignment
   *     statement.
   */
  public <T extends Number> T gauge(String name, T number) {
    return gauge(impl.createId(name), number);
  }

  /**
   * Register a gauge that reports the value of the {@link java.lang.Number}. See
   * {@link #gauge(Id, Number)}.
   *
   * @param name
   *     Name of the metric being registered.
   * @param tags
   *     Sequence of dimensions for breaking down the name.
   * @param number
   *     Thread-safe implementation of {@link Number} used to access the value.
   * @return
   *     The number that was passed in so the registration can be done as part of an assignment
   *     statement.
   */
  public <T extends Number> T gauge(String name, Iterable<Tag> tags, T number) {
    return gauge(impl.createId(name, tags), number);
  }

  /**
   * Register a gauge that reports the value of the object after the function
   * {@code f} is applied. The registration will keep a weak reference to the number so it will
   * not prevent garbage collection. The number implementation used should be thread safe.
   *
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
  public <T> T gauge(Id id, T obj, ValueFunction f) {
    impl.register(new ObjectGauge(clock(), id, obj, f));
    return obj;
  }

  /**
   * Register a gauge that reports the value of the object. See
   * {@link #gauge(Id, Object, ValueFunction)}.
   *
   * @param name
   *     Name of the metric being registered.
   * @param obj
   *     Object used to compute a value.
   * @param f
   *     Function that is applied on the value for the number.
   * @return
   *     The number that was passed in so the registration can be done as part of an assignment
   *     statement.
   */
  public <T> T gauge(String name, T obj, ValueFunction f) {
    return gauge(impl.createId(name), obj, f);
  }

  /**
   * Register a gauge that reports the size of the {@link java.util.Collection}. The registration
   * will keep a weak reference to the collection so it will not prevent garbage collection.
   * The collection implementation used should be thread safe. Note that calling
   * {@link java.util.Collection#size()} can be expensive for some collection implementations
   * and should be considered before registering.
   *
   * @param id
   *     Identifier for the metric being registered.
   * @param collection
   *     Thread-safe implementation of {@link Collection} used to access the value.
   * @return
   *     The number that was passed in so the registration can be done as part of an assignment
   *     statement.
   */
  public <T extends Collection<?>> T collectionSize(Id id, T collection) {
    return gauge(id, collection, Functions.COLLECTION_SIZE);
  }

  /**
   * Register a gauge that reports the size of the {@link java.util.Collection}. The registration
   * will keep a weak reference to the collection so it will not prevent garbage collection.
   * The collection implementation used should be thread safe. Note that calling
   * {@link java.util.Collection#size()} can be expensive for some collection implementations
   * and should be considered before registering.
   *
   * @param name
   *     Name of the metric being registered.
   * @param collection
   *     Thread-safe implementation of {@link Collection} used to access the value.
   * @return
   *     The number that was passed in so the registration can be done as part of an assignment
   *     statement.
   */
  public <T extends Collection<?>> T collectionSize(String name, T collection) {
    return collectionSize(impl.createId(name), collection);
  }

  /**
   * Register a gauge that reports the size of the {@link java.util.Map}. The registration
   * will keep a weak reference to the collection so it will not prevent garbage collection.
   * The collection implementation used should be thread safe. Note that calling
   * {@link java.util.Map#size()} can be expensive for some collection implementations
   * and should be considered before registering.
   *
   * @param id
   *     Identifier for the metric being registered.
   * @param collection
   *     Thread-safe implementation of {@link Map} used to access the value.
   * @return
   *     The number that was passed in so the registration can be done as part of an assignment
   *     statement.
   */
  public <T extends Map<?, ?>> T mapSize(Id id, T collection) {
    return gauge(id, collection, Functions.MAP_SIZE);
  }

  /**
   * Register a gauge that reports the size of the {@link java.util.Map}. The registration
   * will keep a weak reference to the collection so it will not prevent garbage collection.
   * The collection implementation used should be thread safe. Note that calling
   * {@link java.util.Map#size()} can be expensive for some collection implementations
   * and should be considered before registering.
   *
   * @param name
   *     Name of the metric being registered.
   * @param collection
   *     Thread-safe implementation of {@link Map} used to access the value.
   * @return
   *     The number that was passed in so the registration can be done as part of an assignment
   *     statement.
   */
  public <T extends Map<?, ?>> T mapSize(String name, T collection) {
    return mapSize(impl.createId(name), collection);
  }

  /**
   * Register a gauge that reports the return value of invoking the method on the object. The
   * registration will keep a weak reference to the object so it will not prevent garbage
   * collection. The registered method should be thread safe and cheap to invoke. Any potentially
   * long running or expensive activity such as IO should not be performed inline.
   *
   * @param id
   *     Identifier for the metric being registered.
   * @param obj
   *     Object used to compute a value.
   * @param method
   *     Name of the method to invoke on the object.
   */
  public void methodValue(Id id, Object obj, String method) {
    try {
      final Method m = getMethod(obj.getClass(), method);
      try {
        // Make sure we can cast the response to a Number
        final Number n = (Number) m.invoke(obj);
        LOGGER.debug("registering gauge {}, using method [{}], with initial value {}", id, m, n);
        gauge(id, obj, Functions.invokeMethod(m));
      } catch (Exception e) {
        final String msg = "exception thrown invoking method [" + m
          + "], skipping registration of gauge " + id;
        Throwables.propagate(msg, e);
      }
    } catch (NoSuchMethodException e) {
      final String mname = obj.getClass().getName() + "." + method;
      final String msg = "invalid method [" + mname + "], skipping registration of gauge " + id;
      Throwables.propagate(msg, e);
    }
  }

  /**
   * Register a gauge that reports the return value of invoking the method on the object. The
   * registration will keep a weak reference to the object so it will not prevent garbage
   * collection. The registered method should be thread safe and cheap to invoke. Any potentially
   * long running or expensive activity such as IO should not be performed inline.
   *
   * @param name
   *     Name of the metric being registered.
   * @param obj
   *     Object used to compute a value.
   * @param method
   *     Name of the method to invoke on the object.
   */
  public void methodValue(String name, Object obj, String method) {
    methodValue(impl.createId(name), obj, method);
  }

  /** Search for a method in the class and all superclasses. */
  Method getMethod(Class<?> cls, String name) throws NoSuchMethodException {
    NoSuchMethodException firstExc = null;
    for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
      try {
        return c.getDeclaredMethod(name);
      } catch (NoSuchMethodException e) {
        if (firstExc == null) {
          firstExc = e;
        }
      }
    }
    throw firstExc;
  }

  @Override
  public String toString() {
    return "ExtendedRegistry(impl=" + impl + ')';
  }
}
