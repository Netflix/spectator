/*
 * Copyright 2014-2023 Netflix, Inc.
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
import com.netflix.spectator.api.Utils;
import com.netflix.spectator.impl.Hash64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Evaluates all the expressions for a set of subscriptions.
 *
 * <p><b>Classes in this package are only intended for use internally within spectator. They may
 * change at any time and without notice.</b>
 */
public class Evaluator {

  private static final Logger LOGGER = LoggerFactory.getLogger(Evaluator.class);

  private final Lock lock = new ReentrantLock();
  private final Map<String, String> commonTags;
  private final Function<Id, Map<String, String>> idMapper;
  private final long step;
  private final boolean delayGaugeAggregation;
  private final QueryIndex<SubscriptionEntry> index;
  private final Map<Subscription, SubscriptionEntry> subscriptions;
  private final ThreadLocal<SubscriptionEntryConsumer> consumers;

  /**
   * Create a new instance.
   *
   * @param config
   *     Config settings to tune the evaluation behavior.
   */
  public Evaluator(EvaluatorConfig config) {
    this.commonTags = new TreeMap<>(config.commonTags());
    this.idMapper = config.idMapper();
    this.step = config.evaluatorStepSize();
    this.delayGaugeAggregation = config.delayGaugeAggregation();
    this.index = QueryIndex.newInstance(config.indexCacheSupplier());
    this.subscriptions = new ConcurrentHashMap<>();
    this.consumers = new ThreadLocal<>();
  }

  /**
   * Synchronize the set of subscriptions for this evaluator with the provided set.
   */
  public void sync(List<Subscription> subs) {
    lock.lock();
    try {
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
        Query q = sub.dataExpr().query().simplify(commonTags);
        index.remove(q, entry);
        LOGGER.debug("subscription removed: {}", sub);
      }
    } finally {
      lock.unlock();
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
      final String subId = subEntry.subscription.getId();
      final long step = subEntry.subscription.getFrequency();

      if (timestamp % step == 0) {
        LOGGER.debug("evaluating subscription: {}: {}", timestamp, subEntry.subscription);
        DataExpr expr = subEntry.subscription.dataExpr();
        final boolean delayGaugeAggr = delayGaugeAggregation && expr.isAccumulating();

        DataExpr.Aggregator aggregator = expr.aggregator(false);
        Iterator<Map.Entry<Id, Consolidator>> it = subEntry.measurements.entrySet().iterator();
        while (it.hasNext()) {
          Map.Entry<Id, Consolidator> entry = it.next();
          final Consolidator consolidator = entry.getValue();
          consolidator.update(timestamp, Double.NaN);
          final double v = consolidator.value(timestamp);
          if (!Double.isNaN(v)) {
            Map<String, String> tags = idMapper.apply(entry.getKey());
            tags.putAll(commonTags);
            if (delayGaugeAggr && consolidator.isGauge()) {
              // When performing a group by, datapoints missing tag used for the grouping
              // should be ignored
              Map<String, String> rs = expr.resultTags(tags);
              if (rs != null) {
                Map<String, String> resultTags = new HashMap<>(rs);
                resultTags.put("atlas.aggr", idHash(entry.getKey()));
                double acc = expr.isCount() ? 1.0 : v;
                metrics.add(new EvalPayload.Metric(subId, resultTags, acc));
              }
            } else {
              TagsValuePair p = new TagsValuePair(tags, v);
              aggregator.update(p);
              LOGGER.trace("aggregating: {}: {}", timestamp, p);
            }
          }
          if (consolidator.isEmpty()) {
            it.remove();
          }
        }

        for (TagsValuePair pair : aggregator.result()) {
          LOGGER.trace("result: {}: {}", timestamp, pair);
          metrics.add(new EvalPayload.Metric(subId, pair.tags(), pair.value()));
        }
      }
    });

    return new EvalPayload(timestamp, metrics);
  }

  private String idHash(Id id) {
    Hash64 hasher = new Hash64();
    hasher.updateString(id.name());
    final int size = id.size();
    for (int i = 1; i < size; ++i) {
      hasher.updateByte((byte) ',');
      hasher.updateString(id.getKey(i));
      hasher.updateByte((byte) '=');
      hasher.updateString(id.getValue(i));
    }
    return Long.toHexString(hasher.compute());
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

  /** Used for tests to ensure expected number of subscriptions in the evaluator. */
  int subscriptionCount() {
    return subscriptions.size();
  }

  private static class SubscriptionEntry {
    private final Subscription subscription;
    private final int multiple;
    private final ConcurrentHashMap<Id, Consolidator> measurements;

    SubscriptionEntry(Subscription subscription, int multiple) {
      this.subscription = subscription;
      this.multiple = multiple;
      this.measurements = new ConcurrentHashMap<>();
    }

    void update(Measurement m) {
      update(m.id(), m.timestamp(), m.value());
    }

    void update(Id id, long t, double v) {
      Consolidator consolidator = Utils.computeIfAbsent(
          measurements,
          id,
          k -> Consolidator.create(k, subscription.getFrequency(), multiple)
      );
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
