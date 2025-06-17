/*
 * Copyright 2014-2020 Netflix, Inc.
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
package com.netflix.spectator.atlas;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Utils;
import com.netflix.spectator.atlas.impl.Parser;
import com.netflix.spectator.atlas.impl.Query;
import com.netflix.spectator.atlas.impl.QueryIndex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleBinaryOperator;
import java.util.function.Function;

/**
 * Helper functions for performing rollups.
 */
final class Rollups {

  private Rollups() {
  }

  /**
   * Create a rollup policy based on a list of rules.
   */
  static RollupPolicy fromRules(Map<String, String> commonTags, List<RollupPolicy.Rule> rules) {
    QueryIndex<RollupPolicy.Rule> index = QueryIndex.newInstance(new NoopRegistry());
    for (RollupPolicy.Rule rule : rules) {
      // Apply common tags to simplify the query and avoid needing to merge with the ids
      // before evaluating the query
      Query query = Parser.parseQuery(rule.query()).simplify(commonTags);
      index.add(query, rule);
    }
    return ms -> {
      // Common tags -> aggregated measurements
      Map<Map<String, String>, Map<Id, Aggregator>> aggregates = new HashMap<>();
      for (Measurement m : ms) {
        List<RollupPolicy.Rule> matches = index.findMatches(m.id());
        if (matches.isEmpty()) {
          // No matches for the id, but we still need to treat as an aggregate because
          // rollup on another id could cause a collision
          Map<Id, Aggregator> idMap = aggregates.computeIfAbsent(commonTags, k -> new HashMap<>());
          updateAggregate(idMap, m.id(), m);
        } else {
          // Skip measurement if one of the rules indicates it should be dropped
          if (shouldDrop(matches)) {
            continue;
          }

          // For matching rules, find dimensions from common tags and others that are part
          // of the id
          Set<String> commonDimensions = new HashSet<>();
          Set<String> otherDimensions = new HashSet<>();
          for (RollupPolicy.Rule rule : matches) {
            for (String dimension : rule.rollup()) {
              if (commonTags.containsKey(dimension)) {
                commonDimensions.add(dimension);
              } else {
                otherDimensions.add(dimension);
              }
            }
          }

          // Perform rollup by removing the dimensions
          Map<String, String> tags = commonDimensions.isEmpty()
              ? commonTags
              : rollup(commonTags, commonDimensions);
          Id id = otherDimensions.isEmpty()
              ? m.id()
              : m.id().filterByKey(k -> !otherDimensions.contains(k));
          Map<Id, Aggregator> idMap = aggregates.computeIfAbsent(tags, k -> new HashMap<>());
          updateAggregate(idMap, id, m);
        }
      }

      // Convert to final result type
      List<RollupPolicy.Result> results = new ArrayList<>(aggregates.size());
      for (Map.Entry<Map<String, String>, Map<Id, Aggregator>> entry : aggregates.entrySet()) {
        results.add(new RollupPolicy.Result(entry.getKey(), toMeasurements(entry.getValue())));
      }
      return results;
    };
  }

  private static boolean shouldDrop(List<RollupPolicy.Rule> rules) {
    for (RollupPolicy.Rule rule : rules) {
      if (rule.operation() == RollupPolicy.Operation.DROP) {
        return true;
      }
    }
    return false;
  }

  private static Map<String, String> rollup(Map<String, String> tags, Set<String> dimensions) {
    Map<String, String> tmp = new HashMap<>(tags);
    for (String dimension : dimensions) {
      tmp.remove(dimension);
    }
    return tmp;
  }

  /**
   * Aggregate the measurements after applying the mapping function to the ids. Counters types
   * will use a sum aggregation and gauges will use a max aggregation.
   *
   * @param idMapper
   *     Map an id to a new identifier that will be used for the resulting aggregate measurement.
   * @param measurements
   *     Set of input measurements to aggregate.
   * @return
   *     Aggregated set of measurements.
   */
  static List<Measurement> aggregate(Function<Id, Id> idMapper, List<Measurement> measurements) {
    Map<Id, Aggregator> aggregates = new HashMap<>();
    for (Measurement m : measurements) {
      Id id = idMapper.apply(m.id());
      if (id != null) {
        updateAggregate(aggregates, id, m);
      }
    }
    return toMeasurements(aggregates);
  }

  private static void updateAggregate(Map<Id, Aggregator> aggregates, Id id, Measurement m) {
    Aggregator aggregator = aggregates.get(id);
    if (aggregator == null) {
      aggregator = newAggregator(id, m);
      aggregates.put(id, aggregator);
    } else {
      aggregator.update(m);
    }
  }

  private static List<Measurement> toMeasurements(Map<Id, Aggregator> aggregates) {
    List<Measurement> result = new ArrayList<>(aggregates.size());
    for (Aggregator aggregator : aggregates.values()) {
      result.add(aggregator.toMeasurement());
    }
    return result;
  }

  private static final Set<String> SUM_STATS = new LinkedHashSet<>();
  static {
    SUM_STATS.add("count");
    SUM_STATS.add("totalAmount");
    SUM_STATS.add("totalTime");
    SUM_STATS.add("totalOfSquares");
    SUM_STATS.add("percentile");
  }

  private static final DoubleBinaryOperator SUM = nanAwareOp(Double::sum);
  private static final DoubleBinaryOperator MAX = nanAwareOp(Double::max);

  private static DoubleBinaryOperator nanAwareOp(DoubleBinaryOperator op) {
    return (a, b) -> Double.isNaN(a) ? b : Double.isNaN(b) ? a : op.applyAsDouble(a, b);
  }

  private static Aggregator newAggregator(Id id, Measurement m) {
    String statistic = Utils.getTagValue(id, "statistic");
    if (statistic != null && SUM_STATS.contains(statistic)) {
      return new Aggregator(id.withTag(DsType.sum), m.timestamp(), SUM, m.value());
    } else {
      return new Aggregator(id, m.timestamp(), MAX, m.value());
    }
  }

  private static class Aggregator {
    private final Id id;
    private final long timestamp;
    private final DoubleBinaryOperator af;
    private double value;

    Aggregator(Id id, long timestamp, DoubleBinaryOperator af, double init) {
      this.id = id;
      this.timestamp = timestamp;
      this.af = af;
      this.value = init;
    }

    void update(Measurement m) {
      value = af.applyAsDouble(value, m.value());
    }

    Measurement toMeasurement() {
      return new Measurement(id, timestamp, value);
    }
  }
}
