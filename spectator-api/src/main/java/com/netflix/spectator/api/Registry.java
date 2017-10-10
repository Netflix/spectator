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
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Registry to manage a set of meters.
 */
public interface Registry extends Iterable<Meter> {

  /**
   * The clock used by the registry for timing events.
   */
  Clock clock();

  /**
   * Configuration settings used for this registry.
   */
  default RegistryConfig config() {
    return Config.defaultConfig();
  }

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
   * Register a passive gauge. In most cases users should not use this method directly. Use
   * one of the helper methods for working with gauges.
   *
   * @see #gauge(Id, Number)
   * @see #gauge(Id, Object, ToDoubleFunction)
   * @see #collectionSize(Id, Collection)
   * @see #mapSize(Id, Map)
   */
  void register(Meter meter);

  /**
   * Returns a map that can be used to associate state with the registry. Users instrumenting
   * their application will most likely never need to use this method.
   *
   * The primary use case is for building custom meter types that need some additional state
   * beyond the core types supported by the registry. This map can be used to store the state
   * so that the lifecycle of the data is connected to the registry. For an example, see some
   * of the built in patterns such as {@link com.netflix.spectator.api.patterns.LongTaskTimer}.
   */
  ConcurrentMap<Id, Object> state();

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
   * Represents a value sampled from another source. For example, the size of queue. The caller
   * is responsible for sampling the value regularly and calling {@link Gauge#set(double)}.
   * Registry implementations are free to expire the gauge if it has not been updated in the
   * last minute. If you do not want to worry about the sampling, then use {@link PolledMeter}
   * instead.
   *
   * @param id
   *     Identifier created by a call to {@link #createId}
   */
  Gauge gauge(Id id);

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
    return createId(name).withTags(tags);
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
   * Represents a value sampled from another source.
   *
   * @param name
   *     Description of the measurement that is being collected.
   * @return
   *     Gauge instance with the corresponding id.
   */
  default Gauge gauge(String name) {
    return gauge(createId(name));
  }

  /**
   * Represents a value sampled from another source.
   *
   * @param name
   *     Description of the measurement that is being collected.
   * @param tags
   *     Other dimensions that can be used to classify the measurement.
   * @return
   *     Gauge instance with the corresponding id.
   */
  default Gauge gauge(String name, Iterable<Tag> tags) {
    return gauge(createId(name, tags));
  }

  /**
   * Represents a value sampled from another source.
   *
   * @param name
   *     Description of the measurement that is being collected.
   * @param tags
   *     Other dimensions that can be used to classify the measurement.
   * @return
   *     Gauge instance with the corresponding id.
   */
  default Gauge gauge(String name, String... tags) {
    return gauge(createId(name, Utils.toIterable(tags)));
  }

  /**
   * Measures the time taken for long tasks.
   *
   * @param id
   *     Identifier for the metric being registered.
   * @return
   *     Timer instance with the corresponding id.
   * @deprecated
   *     Use {@link com.netflix.spectator.api.patterns.LongTaskTimer#get(Registry, Id)}
   *     instead. Scheduled to be removed in 2.0.
   */
  @Deprecated
  default LongTaskTimer longTaskTimer(Id id) {
    // Note: this method is only included in the registry for historical reasons to
    // maintain compatibility. Future patterns should just use the registry not be
    // created by the registry.
    return com.netflix.spectator.api.patterns.LongTaskTimer.get(this, id);
  }

  /**
   * Measures the time taken for long tasks.
   *
   * @param name
   *     Description of the measurement that is being collected.
   * @return
   *     Timer instance with the corresponding id.
   * @deprecated
   *     Use {@link com.netflix.spectator.api.patterns.LongTaskTimer#get(Registry, Id)}
   *     instead. Scheduled to be removed in 2.0.
   */
  @Deprecated
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
   * @deprecated
   *     Use {@link com.netflix.spectator.api.patterns.LongTaskTimer#get(Registry, Id)}
   *     instead. Scheduled to be removed in 2.0.
   */
  @Deprecated
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
   * @deprecated
   *     Use {@link com.netflix.spectator.api.patterns.LongTaskTimer#get(Registry, Id)}
   *     instead. Scheduled to be removed in 2.0.
   */
  @Deprecated
  default LongTaskTimer longTaskTimer(String name, String... tags) {
    return longTaskTimer(createId(name, Utils.toIterable(tags)));
  }

  /**
   * Tells the registry to regularly poll the value of a {@link java.lang.Number} and report
   * it as a gauge. See {@link #gauge(Id, Number)} for more information.
   *
   * @param id
   *     Identifier for the metric being registered.
   * @param number
   *     Thread-safe implementation of {@link Number} used to access the value.
   * @return
   *     The number that was passed in so the registration can be done as part of an assignment
   *     statement.
   * @deprecated
   *     Use {@link PolledMeter} instead. This method has been deprecated to help
   *     reduce confusion between active gauges that are explicitly updated by the
   *     user and passive gauges that are polled in the background. Going forward
   *     the registry methods will only be used for the core types directly updated
   *     by the user. Other patterns such as {@link PolledMeter}s will be handled
   *     separately. Scheduled to be removed in 2.0.
   */
  @Deprecated
  default <T extends Number> T gauge(Id id, T number) {
    return PolledMeter.using(this).withId(id).monitorValue(number);
  }

  /**
   * Tells the registry to regularly poll the value of a {@link java.lang.Number} and report
   * it as a gauge. See {@link PolledMeter.Builder#monitorValue(Number)} for more information.
   *
   * @param name
   *     Name of the metric being registered.
   * @param number
   *     Thread-safe implementation of {@link Number} used to access the value.
   * @return
   *     The number that was passed in so the registration can be done as part of an assignment
   *     statement.
   * @deprecated
   *     Use {@link PolledMeter} instead. This method has been deprecated to help
   *     reduce confusion between active gauges that are explicitly updated by the
   *     user and passive gauges that are polled in the background. Going forward
   *     the registry methods will only be used for the core types directly updated
   *     by the user. Other patterns such as {@link PolledMeter}s will be handled
   *     separately. Scheduled to be removed in 2.0.
   */
  @Deprecated
  default <T extends Number> T gauge(String name, T number) {
    return gauge(createId(name), number);
  }

  /**
   * Tells the registry to regularly poll the value of a {@link java.lang.Number} and report
   * it as a gauge. See {@link #gauge(Id, Number)} for more information.
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
   * @deprecated
   *     Use {@link PolledMeter} instead. This method has been deprecated to help
   *     reduce confusion between active gauges that are explicitly updated by the
   *     user and passive gauges that are polled in the background. Going forward
   *     the registry methods will only be used for the core types directly updated
   *     by the user. Other patterns such as {@link PolledMeter}s will be handled
   *     separately. Scheduled to be removed in 2.0.
   */
  @Deprecated
  default <T extends Number> T gauge(String name, Iterable<Tag> tags, T number) {
    return gauge(createId(name, tags), number);
  }

  /**
   * See {@link #gauge(Id, Object, ToDoubleFunction)} for more information.
   *
   * @param id
   *     Identifier for the metric being registered.
   * @param obj
   *     Object used to compute a value.
   * @param f
   *     Function that is applied on the value for the number.
   * @return
   *     The object that was passed in so the registration can be done as part of an assignment
   *     statement.
   * @deprecated
   *     Use {@link PolledMeter} instead. This method has been deprecated to help
   *     reduce confusion between active gauges that are explicitly updated by the
   *     user and passive gauges that are polled in the background. Going forward
   *     the registry methods will only be used for the core types directly updated
   *     by the user. Other patterns such as {@link PolledMeter}s will be handled
   *     separately. Scheduled to be removed in 2.0.
   */
  @Deprecated
  default <T> T gauge(Id id, T obj, ToDoubleFunction<T> f) {
    return PolledMeter.using(this).withId(id).monitorValue(obj, f);
  }

  /**
   * See {@link #gauge(Id, Object, ToDoubleFunction)} for more information.
   *
   * @param name
   *     Name of the metric being registered.
   * @param obj
   *     Object used to compute a value.
   * @param f
   *     Function that is applied on the value for the number.
   * @return
   *     The object that was passed in so the registration can be done as part of an assignment
   *     statement.
   * @deprecated
   *     Use {@link PolledMeter} instead. This method has been deprecated to help
   *     reduce confusion between active gauges that are explicitly updated by the
   *     user and passive gauges that are polled in the background. Going forward
   *     the registry methods will only be used for the core types directly updated
   *     by the user. Other patterns such as {@link PolledMeter}s will be handled
   *     separately. Scheduled to be removed in 2.0.
   */
  @Deprecated
  default <T> T gauge(String name, T obj, ToDoubleFunction<T> f) {
    return gauge(createId(name), obj, f);
  }

  /**
   * Tells the registry to regularly poll the collection and report its size as a gauge.
   * The registration will keep a weak reference to the collection so it will not prevent
   * garbage collection. The collection implementation used should be thread safe. Note that
   * calling {@link java.util.Collection#size()} can be expensive for some collection
   * implementations and should be considered before registering.
   *
   * For more information see {@link #gauge(Id, Object, ToDoubleFunction)}.
   *
   * @param id
   *     Identifier for the metric being registered.
   * @param collection
   *     Thread-safe implementation of {@link Collection} used to access the value.
   * @return
   *     The collection that was passed in so the registration can be done as part of an assignment
   *     statement.
   * @deprecated
   *     Use {@link PolledMeter} instead. This method has been deprecated to help
   *     reduce confusion between active gauges that are explicitly updated by the
   *     user and passive gauges that are polled in the background. Going forward
   *     the registry methods will only be used for the core types directly updated
   *     by the user. Other patterns such as {@link PolledMeter}s will be handled
   *     separately. Scheduled to be removed in 2.0.
   */
  @Deprecated
  default <T extends Collection<?>> T collectionSize(Id id, T collection) {
    return PolledMeter.using(this).withId(id).monitorSize(collection);
  }

  /**
   * Tells the registry to regularly poll the collection and report its size as a gauge.
   * See {@link #collectionSize(Id, Collection)} for more information.
   *
   * @param name
   *     Name of the metric being registered.
   * @param collection
   *     Thread-safe implementation of {@link Collection} used to access the value.
   * @return
   *     The collection that was passed in so the registration can be done as part of an assignment
   *     statement.
   * @deprecated
   *     Use {@link PolledMeter} instead. This method has been deprecated to help
   *     reduce confusion between active gauges that are explicitly updated by the
   *     user and passive gauges that are polled in the background. Going forward
   *     the registry methods will only be used for the core types directly updated
   *     by the user. Other patterns such as {@link PolledMeter}s will be handled
   *     separately. Scheduled to be removed in 2.0.
   */
  @Deprecated
  default <T extends Collection<?>> T collectionSize(String name, T collection) {
    return collectionSize(createId(name), collection);
  }

  /**
   * Tells the registry to regularly poll the map and report its size as a gauge.
   * The registration will keep a weak reference to the map so it will not prevent
   * garbage collection. The collection implementation used should be thread safe. Note that
   * calling {@link java.util.Map#size()} can be expensive for some map
   * implementations and should be considered before registering.
   *
   * For more information see {@link #gauge(Id, Object, ToDoubleFunction)}.
   *
   * @param id
   *     Identifier for the metric being registered.
   * @param collection
   *     Thread-safe implementation of {@link Map} used to access the value.
   * @return
   *     The map that was passed in so the registration can be done as part of an assignment
   *     statement.
   * @deprecated
   *     Use {@link PolledMeter} instead. This method has been deprecated to help
   *     reduce confusion between active gauges that are explicitly updated by the
   *     user and passive gauges that are polled in the background. Going forward
   *     the registry methods will only be used for the core types directly updated
   *     by the user. Other patterns such as {@link PolledMeter}s will be handled
   *     separately. Scheduled to be removed in 2.0.
   */
  @Deprecated
  default <T extends Map<?, ?>> T mapSize(Id id, T collection) {
    return PolledMeter.using(this).withId(id).monitorSize(collection);
  }

  /**
   * Tells the registry to regularly poll the collection and report its size as a gauge.
   * See {@link #mapSize(Id, Map)} for more information.
   *
   * @param name
   *     Name of the metric being registered.
   * @param collection
   *     Thread-safe implementation of {@link Map} used to access the value.
   * @return
   *     The map that was passed in so the registration can be done as part of an assignment
   *     statement.
   * @deprecated
   *     Use {@link PolledMeter} instead. This method has been deprecated to help
   *     reduce confusion between active gauges that are explicitly updated by the
   *     user and passive gauges that are polled in the background. Going forward
   *     the registry methods will only be used for the core types directly updated
   *     by the user. Other patterns such as {@link PolledMeter}s will be handled
   *     separately. Scheduled to be removed in 2.0.
   */
  @Deprecated
  default <T extends Map<?, ?>> T mapSize(String name, T collection) {
    return mapSize(createId(name), collection);
  }

  /**
   * Tells the registry to regularly poll the object by by using reflection to invoke the named
   * method and report the returned value as a gauge. The registration will keep a weak reference
   * to the object so it will not prevent garbage collection. The registered method should be
   * thread safe and cheap to invoke. <b>Never perform any potentially long running or expensive
   * activity such as IO inline</b>.
   *
   * To get better compile time type safety, {@link #gauge(Id, Object, ToDoubleFunction)}
   * should be preferred. Use this technique only if there access or other restrictions prevent
   * using a proper function reference. However, keep in mind that makes your code more brittle
   * and prone to failure in the future.
   *
   * For more information see {@link #gauge(Id, Object, ToDoubleFunction)}.
   *
   * @param id
   *     Identifier for the metric being registered.
   * @param obj
   *     Object used to compute a value.
   * @param method
   *     Name of the method to invoke on the object.
   * @deprecated
   *     Use {@link PolledMeter} instead. This method has been deprecated to help
   *     reduce confusion between active gauges that are explicitly updated by the
   *     user and passive gauges that are polled in the background. Going forward
   *     the registry methods will only be used for the core types directly updated
   *     by the user. Other patterns such as {@link PolledMeter}s will be handled
   *     separately. Scheduled to be removed in 2.0.
   */
  @Deprecated
  default void methodValue(Id id, Object obj, String method) {
    final Method m = Utils.getGaugeMethod(this, id, obj, method);
    if (m != null) {
      PolledMeter.using(this).withId(id).monitorValue(obj, Functions.invokeMethod(m));
    }
  }

  /**
   * Tells the registry to regularly poll the object by by using reflection to invoke the named
   * method and report the returned value as a gauge. See {@link #methodValue(Id, Object, String)}
   * for more information.
   *
   * @param name
   *     Name of the metric being registered.
   * @param obj
   *     Object used to compute a value.
   * @param method
   *     Name of the method to invoke on the object.
   * @deprecated
   *     Use {@link PolledMeter} instead. This method has been deprecated to help
   *     reduce confusion between active gauges that are explicitly updated by the
   *     user and passive gauges that are polled in the background. Going forward
   *     the registry methods will only be used for the core types directly updated
   *     by the user. Other patterns such as {@link PolledMeter}s will be handled
   *     separately. Scheduled to be removed in 2.0.
   */
  @Deprecated
  default void methodValue(String name, Object obj, String method) {
    methodValue(createId(name), obj, method);
  }

  /** Returns a stream of all registered meters. */
  default Stream<Meter> stream() {
    return StreamSupport.stream(spliterator(), false);
  }

  /**
   * Returns a stream of all registered counters. This operation is mainly used for testing as
   * a convenient way to get an aggregated value. For example, to generate a summary of all
   * counters with name "foo":
   *
   * <pre>
   * LongSummaryStatistics summary = r.counters()
   *   .filter(Functions.nameEquals("foo"))
   *   .collect(Collectors.summarizingLong(Counter::count));
   * </pre>
   */
  default Stream<Counter> counters() {
    return stream().filter(m -> m instanceof Counter).map(m -> (Counter) m);
  }

  /**
   * Returns a stream of all registered distribution summaries. This operation is mainly used for
   * testing as a convenient way to get an aggregated value. For example, to generate a summary of
   * the counts and total amounts for all distribution summaries with name "foo":
   *
   * <pre>
   * LongSummaryStatistics countSummary = r.distributionSummaries()
   *   .filter(Functions.nameEquals("foo"))
   *   .collect(Collectors.summarizingLong(DistributionSummary::count));
   *
   * LongSummaryStatistics totalSummary = r.distributionSummaries()
   *   .filter(Functions.nameEquals("foo"))
   *   .collect(Collectors.summarizingLong(DistributionSummary::totalAmount));
   *
   * double avgAmount = (double) totalSummary.getSum() / countSummary.getSum();
   * </pre>
   */
  default Stream<DistributionSummary> distributionSummaries() {
    return stream().filter(m -> m instanceof DistributionSummary).map(m -> (DistributionSummary) m);
  }

  /**
   * Returns a stream of all registered timers. This operation is mainly used for testing as a
   * convenient way to get an aggregated value. For example, to generate a summary of
   * the counts and total amounts for all timers with name "foo":
   *
   * <pre>
   * LongSummaryStatistics countSummary = r.timers()
   *   .filter(Functions.nameEquals("foo"))
   *   .collect(Collectors.summarizingLong(Timer::count));
   *
   * LongSummaryStatistics totalSummary = r.timers()
   *   .filter(Functions.nameEquals("foo"))
   *   .collect(Collectors.summarizingLong(Timer::totalTime));
   *
   * double avgTime = (double) totalSummary.getSum() / countSummary.getSum();
   * </pre>
   */
  default Stream<Timer> timers() {
    return stream().filter(m -> m instanceof Timer).map(m -> (Timer) m);
  }

  /**
   * Returns a stream of all registered gauges. This operation is mainly used for testing as a
   * convenient way to get an aggregated value. For example, to generate a summary of
   * the values for all gauges with name "foo":
   *
   * <pre>
   * DoubleSummaryStatistics valueSummary = r.gauges()
   *   .filter(Functions.nameEquals("foo"))
   *   .collect(Collectors.summarizingDouble(Gauge::value));
   *
   * double sum = (double) valueSummary.getSum();
   * </pre>
   */
  default Stream<Gauge> gauges() {
    return stream().filter(m -> m instanceof Gauge).map(m -> (Gauge) m);
  }

  /**
   * Log a warning and if enabled propagate the exception {@code t}. As a general rule
   * instrumentation code should degrade gracefully and avoid impacting the core application. If
   * the user makes a mistake and causes something to break, then it should not impact the
   * application unless that mistake triggers a problem outside of the instrumentation code.
   * However, in test code it is often better to throw so that mistakes are caught and corrected.
   *
   * This method is used to handle exceptions internal to the instrumentation code. Propagation
   * is controlled by the {@link RegistryConfig#propagateWarnings()} setting. If the setting
   * is true, then the exception will be propagated. Otherwise the exception will only get logged
   * as a warning.
   *
   * @param msg
   *     Message written out to the log.
   * @param t
   *     Exception to log and optionally propagate.
   */
  default void propagate(String msg, Throwable t) {
    LoggerFactory.getLogger(getClass()).warn(msg, t);
    if (config().propagateWarnings()) {
      if (t instanceof RuntimeException) {
        throw (RuntimeException) t;
      } else {
        throw new RuntimeException(t);
      }
    }
  }
  /**
   * Log a warning using the message from the exception and if enabled propagate the
   * exception {@code t}. For more information see {@link #propagate(String, Throwable)}.
   *
   * @param t
   *     Exception to log and optionally propagate.
   */
  default void propagate(Throwable t) {
    propagate(t.getMessage(), t);
  }
}
