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
import com.netflix.spectator.api.Utils;

import java.util.ArrayList;
import java.util.HashMap;
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
   * Aggregate the measurements after appling the mapping function to the ids. Counters types
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
        Aggregator aggregator = aggregates.get(id);
        if (aggregator == null) {
          aggregator = newAggregator(id, m);
          aggregates.put(id, aggregator);
        } else {
          aggregator.update(m);
        }
      }
    }

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
    DoubleBinaryOperator af = MAX;
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
