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
package com.netflix.spectator.atlas.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Evaluates all of the expressions for subscriptions associated with a group.
 *
 * <b>Classes in this package are only intended for use internally within spectator. They may
 * change at any time and without notice.</b>
 */
public class Evaluator {

  private static final Logger LOGGER = LoggerFactory.getLogger(Evaluator.class);

  // Subscriptions by group
  private final Map<String, List<Subscription>> subscriptions = new ConcurrentHashMap<>();

  /**
   * Synchronize the internal set of subscriptions with the map that is passed in.
   *
   * @param subs
   *     Complete set of subscriptions for all groups. The keys for the map are the
   *     group names.
   * @return
   *     This instance for chaining of update operations.
   */
  public Evaluator sync(Map<String, List<Subscription>> subs) {
    Set<String> removed = subscriptions.keySet();
    removed.removeAll(subs.keySet());
    removed.forEach(this::removeGroupSubscriptions);
    subs.forEach(this::addGroupSubscriptions);
    return this;
  }

  /**
   * Add subscriptions for a given group.
   *
   * @param group
   *     Name of the group. At Netflix this is typically the cluster that includes
   *     the instance reporting data.
   * @param subs
   *     Set of subscriptions for the group.
   * @return
   *     This instance for chaining of update operations.
   */
  public Evaluator addGroupSubscriptions(String group, List<Subscription> subs) {
    List<Subscription> oldSubs = subscriptions.put(group, subs);
    if (oldSubs == null) {
      LOGGER.debug("added group {} with {} subscriptions", group, subs.size());
    } else {
      LOGGER.debug("updated group {}, {} subscriptions before, {} subscriptions now",
          group, oldSubs.size(), subs.size());
    }
    return this;
  }

  /**
   * Remove subscriptions for a given group.
   *
   * @param group
   *     Name of the group. At Netflix this is typically the cluster that includes
   *     the instance reporting data.
   * @return
   *     This instance for chaining of update operations.
   */
  public Evaluator removeGroupSubscriptions(String group) {
    List<Subscription> oldSubs = subscriptions.remove(group);
    if (oldSubs != null) {
      LOGGER.debug("removed group {} with {} subscriptions", group, oldSubs.size());
    }
    return this;
  }

  /**
   * Evaluate expressions for all subscriptions associated with the specified group using
   * the provided data.
   *
   * @param group
   *     Name of the group. At Netflix this is typically the cluster that includes
   *     the instance reporting data.
   * @param timestamp
   *     Timestamp to use for the payload response.
   * @param vs
   *     Set of values received for the group for the current time period.
   * @return
   *     Payload that can be encoded and sent to Atlas streaming evaluation cluster.
   */
  public EvalPayload eval(String group, long timestamp, List<TagsValuePair> vs) {
    List<Subscription> subs = subscriptions.getOrDefault(group, Collections.emptyList());
    List<EvalPayload.Metric> metrics = new ArrayList<>();
    for (Subscription s : subs) {
      DataExpr expr = s.dataExpr();
      for (TagsValuePair pair : expr.eval(vs)) {
        EvalPayload.Metric m = new EvalPayload.Metric(s.getId(), pair.tags(), pair.value());
        metrics.add(m);
      }
    }
    return new EvalPayload(timestamp, metrics);
  }
}
