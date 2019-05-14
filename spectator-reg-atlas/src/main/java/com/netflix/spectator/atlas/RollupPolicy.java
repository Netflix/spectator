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
package com.netflix.spectator.atlas;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;

import java.util.List;
import java.util.function.Function;

/**
 * Policy for performing a rollup on a set of measurements. This typically involves
 * removing some dimensions from the ids and combining the results into an aggregate
 * measurement.
 */
public interface RollupPolicy extends Function<List<Measurement>, List<Measurement>> {

  /** Does nothing, returns the input list without modification. */
  static RollupPolicy noop() {
    return ms -> ms;
  }

  /**
   * Create a new policy that will aggregate ids based on the statistic tag. Counter types
   * will use a sum aggregation and gauges will use max.
   *
   * @param idMapper
   *     Map an id to a new identifier that will be used for the resulting aggregate measurement.
   * @return
   *     A rollup policy that will apply the mapping function to the ids of input measurements
   *     and aggregate the results.
   */
  static RollupPolicy fromIdMapper(Function<Id, Id> idMapper) {
    return ms -> Rollups.aggregate(idMapper, ms);
  }
}
