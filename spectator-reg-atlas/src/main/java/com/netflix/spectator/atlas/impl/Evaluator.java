/*
 * Copyright 2014-2019 Netflix, Inc.
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
package com.netflix.spectator.atlas.impl;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.NoopRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Evaluates all of the expressions for a set of subscriptions.
 *
 * <b>Classes in this package are only intended for use internally within spectator. They may
 * change at any time and without notice.</b>
 */
public class Evaluator {

  private static final Logger LOGGER = LoggerFactory.getLogger(Evaluator.class);

  private final Map<String, String> commonTags;
  private final Function<Id, Map<String, String>> idMapper;
  private final long step;
  private final QueryIndex<SubscriptionEntry> index;
  private final Map<Subscription, SubscriptionEntry> subscriptions;
  private final ThreadLocal<SubscriptionEntryConsumer> consumers;

  /**
   * Create a new instance.
   *
   * @param commonTags
   *     Common tags that should be applied to all datapoints.
   * @param idMapper
   *     Function to convert an id to a map of key/value pairs.
   * @param step
   *     Step size used for the raw measurements.
   */
  public Evaluator(Map<String, String> commonTags, Function<Id, Map<String, String>> idMapper, long step) {
    this.commonTags = commonTags;
    this.idMapper = idMapper;
    this.step = step;
    this.index = QueryIndex.newInstance(new NoopRegistry());
    this.subscriptions = new ConcurrentHashMap<>();
    this.consumers = new ThreadLocal<>();
  }

  /**
   * Synchronize the set of subscriptions for this evaluator with the provided set.
   */
  public synchronized void sync(List<Subscription> subs) {
    Set<Subscription> removed = new HashSet<>(subscriptions.keySet());
    for (Subscription sub : subs) {
      boolean alreadyPresent = removed.remove(sub);
      if (!alreadyPresent) {
        try {
          // Parse and simplify query
          Query q = sub.dataExpr().query().simplify(commonTags);
          LOGGER.trace("query pre-eval: original [{}], simplified [{}], common tags {}",
              sub.dataExpr().query(), q, commonTags);

          // Update index
          int multiple = (int) (sub.getFrequency() / step);
          SubscriptionEntry entry = new SubscriptionEntry(sub, multiple);
          subscriptions.put(sub, entry);
          index.add(q, entry);
          LOGGER.debug("subscription added: {}", sub);
        } catch (Exception e) {
          LOGGER.warn("failed to add subscription: {}", sub, e);
        }
      } else {
        LOGGER.trace("subscription already present: {}", sub);
      }
    }

    for (Subscription sub : removed) {
      SubscriptionEntry entry = subscriptions.remove(sub);
      index.remove(entry);
      LOGGER.debug("subscription removed: {}", sub);
    }
  }

  /**
   * Update the state. See {@link #update(Id, long, double)} for more information.
   */
  public void update(Measurement m) {
    index.forEachMatch(m.id(), entry -> entry.update(m));
  }

  /**
   * Update the state for the expressions to be evaluated with the provided datapoint.
   *
   * @param id
   *     Id for the datapoint. The value will be collected for each expression where the
   *     id satisfies the query constraints.
   * @param t
   *     Timestamp for the datapoint. It should be on a boundary for the step interval.
   * @param v
   *     Value for the datapoint.
   */
  public void update(Id id, long t, double v) {
    // Using a simple lambda with forEachMatch results in a lot of allocations. The
    // SubscriptionEntryConsumer can be updated with the datapoint and reused across
    // invocations
    SubscriptionEntryConsumer consumer = consumers.get();
    if (consumer == null) {
      consumer = new SubscriptionEntryConsumer();
      consumers.set(consumer);
    }
    consumer.updateMeasurement(id, t, v);
    index.forEachMatch(id, consumer);
  }

  /**
   * Evaluate the expressions for all subscriptions against the data available for the provided
   * timestamp. The data must be populated by calling {@link #update(Id, long, double)} prior to
   * performing the evaluation.
   *
   * @param timestamp
   *     Timestamp for the interval to evaluate.
   * @return
   *     Payload representing the results of the evaluation.
   */
  public EvalPayload eval(long timestamp) {
    List<EvalPayload.Metric> metrics = new ArrayList<>();
    subscriptions.values().forEach(subEntry -> {
      long step = subEntry.subscription.getFrequency();
      if (timestamp % step == 0) {
        LOGGER.debug("evaluating subscription: {}: {}", timestamp, subEntry.subscription);
        DataExpr expr = subEntry.subscription.dataExpr();
        DataExpr.Aggregator aggregator = expr.aggregator(expr.query().exactTags(), false);
        Iterator<Map.Entry<Id, Consolidator>> it = subEntry.measurements.entrySet().iterator();
        while (it.hasNext()) {
          Map.Entry<Id, Consolidator> entry = it.next();
          Consolidator consolidator = entry.getValue();
          consolidator.update(timestamp, Double.NaN);
          double v = consolidator.value(timestamp);
          if (!Double.isNaN(v)) {
            Map<String, String> tags = idMapper.apply(entry.getKey());
            tags.putAll(commonTags);
            TagsValuePair p = new TagsValuePair(tags, v);
            aggregator.update(p);
            LOGGER.trace("aggregating: {}: {}", timestamp, p);
          }
          if (consolidator.isEmpty()) {
            it.remove();
          }
        }

        String subId = subEntry.subscription.getId();
        for (TagsValuePair pair : aggregator.result()) {
          LOGGER.trace("result: {}: {}", timestamp, pair);
          metrics.add(new EvalPayload.Metric(subId, pair.tags(), pair.value()));
        }
      }
    });

    return new EvalPayload(timestamp, metrics);
  }

  /**
   * Helper function that evaluates the data for a given time after updating with the
   * provided list of measurements.
   *
   * @param t
   *     Timestamp for the interval to evaluate.
   * @param ms
   *     List of measurements to include before performing the evaluation.
   * @return
   *     Payload representing the results of the evaluation.
   */
  public EvalPayload eval(long t, List<Measurement> ms) {
    ms.forEach(this::update);
    return eval(t);
  }

  /** Used for tests to ensure expected number of subcriptions in the evaluator. */
  int subscriptionCount() {
    return subscriptions.size();
  }

  private static class SubscriptionEntry {
    private final Subscription subscription;
    private final int multiple;
    private final Map<Id, Consolidator> measurements;

    SubscriptionEntry(Subscription subscription, int multiple) {
      this.subscription = subscription;
      this.multiple = multiple;
      this.measurements = new HashMap<>();
    }

    void update(Measurement m) {
      update(m.id(), m.timestamp(), m.value());
    }

    void update(Id id, long t, double v) {
      Consolidator consolidator = measurements.get(id);
      if (consolidator == null) {
        consolidator = Consolidator.create(id, subscription.getFrequency(), multiple);
        measurements.put(id, consolidator);
      }
      consolidator.update(t, v);
    }
  }

  /**
   * Consumer that allows the measurement data to be mutated. This can be used to avoid
   * allocating instances of the closure when calling forEachMatch on the query index.
   */
  private static class SubscriptionEntryConsumer implements Consumer<SubscriptionEntry> {
    private Id id;
    private long timestamp;
    private double value;

    public void updateMeasurement(Id id, long timestamp, double value) {
      this.id = id;
      this.timestamp = timestamp;
      this.value = value;
    }

    @Override
    public void accept(SubscriptionEntry entry) {
      entry.update(id, timestamp, value);
    }
  }
}
