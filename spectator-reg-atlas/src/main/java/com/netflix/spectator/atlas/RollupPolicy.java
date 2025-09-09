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
import com.netflix.spectator.impl.Preconditions;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Policy for performing a rollup on a set of measurements. This typically involves
 * removing some dimensions from the ids and combining the results into an aggregate
 * measurement.
 */
@FunctionalInterface
public interface RollupPolicy extends Function<List<Measurement>, List<RollupPolicy.Result>> {

  /** Does nothing, returns the input list without modification. */
  static RollupPolicy noop(Map<String, String> commonTags) {
    return ms -> Collections.singletonList(new Result(commonTags, ms));
  }

  /**
   * Create a new policy that will aggregate ids based on the statistic tag. Counter types
   * will use a sum aggregation and gauges will use max.
   *
   * @param commonTags
   *     Common tags that are applied to all measurements.
   * @param idMapper
   *     Map an id to a new identifier that will be used for the resulting aggregate measurement.
   * @return
   *     A rollup policy that will apply the mapping function to the ids of input measurements
   *     and aggregate the results.
   */
  static RollupPolicy fromIdMapper(Map<String, String> commonTags, Function<Id, Id> idMapper) {
    Function<Id, Id> mapper = commonTags.isEmpty()
        ? idMapper
        : id -> idMapper.apply(id.withTags(commonTags));
    return ms -> Collections.singletonList(new Result(Rollups.aggregate(mapper, ms)));
  }

  /**
   * Create a new policy based on a list of rules. A rule consists of an Atlas query expression
   * and a set of dimensions that should be removed for matching measurements.
   *
   * @param commonTags
   *     Set of common tags that are applied to all measurements.
   * @param rules
   *     List of rules for specifying what dimensions should be removed.
   * @return
   *     A rollup policy that will apply the rules on the input measurements and aggregate the
   *     results.
   */
  static RollupPolicy fromRules(Map<String, String> commonTags, List<Rule> rules) {
    return Rollups.fromRules(commonTags, rules);
  }

  /** Operation associated with a rule. */
  enum Operation {
    /** Rollup data by removing specified dimensions. */
    ROLLUP,

    /** Drop the data that matches the query. */
    DROP
  }

  /**
   * Rule for matching a set of measurements and removing specified dimensions.
   */
  final class Rule {
    private final String query;
    private final List<String> rollup;
    private final Operation operation;

    /**
     * Create a new instance.
     *
     * @param query
     *     Atlas query expression that indicates the set of measurements matching this rule.
     * @param rollup
     *     Set of dimensions to remove from the matching measurements.
     * @param operation
     *     Operation to perform if there is a match to the query.
     */
    public Rule(String query, List<String> rollup, Operation operation) {
      this.query = Preconditions.checkNotNull(query, "query");
      this.rollup = Preconditions.checkNotNull(rollup, "rollup");
      this.operation = Preconditions.checkNotNull(operation, "operation");
    }

    /**
     * Create a new instance.
     *
     * @param query
     *     Atlas query expression that indicates the set of measurements matching this rule.
     * @param rollup
     *     Set of dimensions to remove from the matching measurements.
     */
    public Rule(String query, List<String> rollup) {
      this(query, rollup, Operation.ROLLUP);
    }

    /** Return the query expression string. */
    public String query() {
      return query;
    }

    /** Return the set of dimensions to remove. */
    public List<String> rollup() {
      return rollup;
    }

    /** Return the operation to perform if the query matches. */
    public Operation operation() {
      return operation;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Rule)) return false;
      Rule rule = (Rule) o;
      return query.equals(rule.query)
          && rollup.equals(rule.rollup)
          && operation == rule.operation;
    }

    @Override
    public int hashCode() {
      return Objects.hash(query, rollup, operation);
    }
  }

  /** Result of applying the rollup policy. */
  final class Result {
    private final Map<String, String> commonTags;
    private final List<Measurement> measurements;

    /** Create a new instance. */
    public Result(List<Measurement> measurements) {
      this(Collections.emptyMap(), measurements);
    }

    /**
     * Create a new instance.
     *
     * @param commonTags
     *     Common tags that should be applied to all measurements in this result.
     * @param measurements
     *     Measurments aggregated according to the policy.
     */
    public Result(Map<String, String> commonTags, List<Measurement> measurements) {
      this.commonTags = commonTags;
      this.measurements = measurements;
    }

    /** Return the common tags for this result. */
    public Map<String, String> commonTags() {
      return commonTags;
    }

    /** Return the measurements for this result. */
    public List<Measurement> measurements() {
      return measurements;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Result)) return false;
      Result result = (Result) o;
      return commonTags.equals(result.commonTags)
          && measurements.equals(result.measurements);
    }

    @Override
    public int hashCode() {
      return Objects.hash(commonTags, measurements);
    }
  }
}
