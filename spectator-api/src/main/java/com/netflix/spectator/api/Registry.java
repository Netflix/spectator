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

import java.util.Iterator;

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
}
