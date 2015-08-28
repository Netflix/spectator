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

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * Registry to manage a set of meters.
 */
public interface Registry extends Iterable<Meter> {

  /**
   * The clock used by the registry for timing events.
   */
  Clock clock();

  /**
   * Creates an identifier for a meter. All ids passed into other calls should be created by the
   * registry.
   *
   * @param name
   *     Description of the measurement that is being collected.
   */
  Id createId(String name);

  /**
   * Creates an identifier for a meter. All ids passed into other calls should be created by the
   * registry.
   *
   * @param name
   *     Description of the measurement that is being collected.
   * @param tags
   *     Other dimensions that can be used to classify the measurement.
   */
  Id createId(String name, Iterable<Tag> tags);

  /**
   * Add a custom meter to the registry.
   */
  void register(Meter meter);

  /**
   * Measures the rate of some activity. A counter is for continuously incrementing sources like
   * the number of requests that are coming into a server.
   *
   * @param id
   *     Identifier created by a call to {@link #createId}
   */
  Counter counter(Id id);

  /**
   * Measures the rate and variation in amount for some activity. For example, it could be used to
   * get insight into the variation in response sizes for requests to a server.
   *
   * @param id
   *     Identifier created by a call to {@link #createId}
   */
  DistributionSummary distributionSummary(Id id);

  /**
   * Measures the rate and time taken for short running tasks.
   *
   * @param id
   *     Identifier created by a call to {@link #createId}
   */
  Timer timer(Id id);

  /**
   * Returns the meter associated with a given id.
   *
   * @param id
   *     Identifier for the meter.
   * @return
   *     Instance of the meter or null if there is no match.
   */
  Meter get(Id id);

  /** Iterator for traversing the set of meters in the registry. */
  Iterator<Meter> iterator();

  /////////////////////////////////////////////////////////////////
  // Additional helper methods below

  /**
   * Returns the first underlying registry that is an instance of {@code c}.
   */
  @SuppressWarnings("unchecked")
  default <T extends Registry> T underlying(Class<T> c) {
    if (c.isAssignableFrom(getClass())) {
      return (T) this;
    } else if (this instanceof CompositeRegistry) {
      return ((CompositeRegistry) this).find(c);
    } else {
      return null;
    }
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
  default Id createId(String name, String... tags) {
    return createId(name, Utils.toIterable(tags));
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
  default Id createId(String name, Map<String, String> tags) {
    return createId(name).withTags(TagList.create(tags));
  }

  /**
   * Measures the rate of some activity.
   *
   * @param name
   *     Description of the measurement that is being collected.
   * @return
   *     Counter instance with the corresponding id.
   */
  default Counter counter(String name) {
    return counter(createId(name));
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
  default Counter counter(String name, Iterable<Tag> tags) {
    return counter(createId(name, tags));
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
  default Counter counter(String name, String... tags) {
    return counter(createId(name, Utils.toIterable(tags)));
  }

  /**
   * Measures the sample distribution of events.
   *
   * @param name
   *     Description of the measurement that is being collected.
   * @return
   *     Summary instance with the corresponding id.
   */
  default DistributionSummary distributionSummary(String name) {
    return distributionSummary(createId(name));
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
  default DistributionSummary distributionSummary(String name, Iterable<Tag> tags) {
    return distributionSummary(createId(name, tags));
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
  default DistributionSummary distributionSummary(String name, String... tags) {
    return distributionSummary(createId(name, Utils.toIterable(tags)));
  }

  /**
   * Measures the time taken for short tasks.
   *
   * @param name
   *     Description of the measurement that is being collected.
   * @return
   *     Timer instance with the corresponding id.
   */
  default Timer timer(String name) {
    return timer(createId(name));
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
  default Timer timer(String name, Iterable<Tag> tags) {
    return timer(createId(name, tags));
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
  default Timer timer(String name, String... tags) {
    return timer(createId(name, Utils.toIterable(tags)));
  }

  /**
   * Measures the time taken for long tasks.
   *
   * @param id
   *     Identifier for the metric being registered.
   * @return
   *     Timer instance with the corresponding id.
   */
  default LongTaskTimer longTaskTimer(Id id) {
    LongTaskTimer taskTimer = new DefaultLongTaskTimer(clock(), id);
    register(taskTimer); // the AggrMeter has the right semantics for these type of timers
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
  default LongTaskTimer longTaskTimer(String name) {
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
  default LongTaskTimer longTaskTimer(String name, Iterable<Tag> tags) {
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
  default LongTaskTimer longTaskTimer(String name, String... tags) {
    return longTaskTimer(createId(name, Utils.toIterable(tags)));
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
  default <T extends Number> T gauge(Id id, T number) {
    return (T) gauge(id, number, (ToDoubleFunction<T>) Functions.IDENTITY);
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
  default <T extends Number> T gauge(String name, T number) {
    return gauge(createId(name), number);
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
  default <T extends Number> T gauge(String name, Iterable<Tag> tags, T number) {
    return gauge(createId(name, tags), number);
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
  default <T> T gauge(Id id, T obj, ToDoubleFunction<T> f) {
    register(new ObjectGauge(clock(), id, obj, f));
    return obj;
  }

  /**
   * Register a gauge that reports the value of the object. See
   * {@link #gauge(Id, Object, ToDoubleFunction)}.
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
  default <T> T gauge(String name, T obj, ToDoubleFunction<T> f) {
    return gauge(createId(name), obj, f);
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
   *
   * @deprecated Use {@link #gauge(Id, Object, ToDoubleFunction)}.
   */
  @Deprecated
  default <T> T gauge(Id id, T obj, ValueFunction<T> f) {
    register(new ObjectGauge(clock(), id, obj, f));
    return obj;
  }

  /**
   * Register a gauge that reports the value of the object. See
   * {@link #gauge(Id, Object, ToDoubleFunction)}.
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
   *
   * @deprecated Use {@link #gauge(String, Object, ToDoubleFunction)}.
   */
  @Deprecated
  default <T> T gauge(String name, T obj, ValueFunction<T> f) {
    return gauge(createId(name), obj, f);
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
  default <T extends Collection<?>> T collectionSize(Id id, T collection) {
    return gauge(id, collection, Collection::size);
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
  default <T extends Collection<?>> T collectionSize(String name, T collection) {
    return collectionSize(createId(name), collection);
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
  default <T extends Map<?, ?>> T mapSize(Id id, T collection) {
    return gauge(id, collection, Map::size);
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
  default <T extends Map<?, ?>> T mapSize(String name, T collection) {
    return mapSize(createId(name), collection);
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
  default void methodValue(Id id, Object obj, String method) {
    final Method m = Utils.getGaugeMethod(id, obj, method);
    if (m != null) {
      gauge(id, obj, Functions.invokeMethod(m));
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
  default void methodValue(String name, Object obj, String method) {
    methodValue(createId(name), obj, method);
  }
}
