/**
 * Copyright 2016 Netflix, Inc.
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
package com.netflix.spectator.placeholders;

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DistributionSummary;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Timer;

/**
 * Factory for creating instances of activity based meters with placeholders in the
 * identifiers so that the final id can be resolved when the activity takes place.
 */
public interface PlaceholderFactory {

  /**
   * Create a new instance of the factory.
   *
   * @param registry
   *      the registry to use when creating ids
   */
  static PlaceholderFactory from(Registry registry) {
    return new DefaultPlaceholderFactory(registry);
  }

  /**
   * Creates an identifier with placeholders for a counter, timer, or distribution summary.
   *
   * @param name
   *     Description of the measurement that is being collected.
   * @return
   *     The newly created identifier.
   */
  PlaceholderId createId(String name);

  /**
   * Creates an identifier with placeholders for a counter, timer, or distribution summary.
   *
   * @param name
   *     Description of the measurement that is being collected.
   * @param tagFactories
   *     Other factories that can generate other dimensions that can be used to classify
   *     the measurement.
   * @return
   *     The newly created identifier.
   */
  PlaceholderId createId(String name, Iterable<TagFactory> tagFactories);

  /**
   * Measures the rate of some activity.  A counter is for continuously incrementing sources like
   * the number of requests that are coming into a server.
   *
   * @param id
   *     Identifier created by a call to {@link #createId}
   */
  Counter counter(PlaceholderId id);

  /**
   * Measures the rate and variation in amount for some activity. For example, it could be used to
   * get insight into the variation in response sizes for requests to a server.
   *
   * @param id
   *     Identifier created by a call to {@link #createId}
   */
  DistributionSummary distributionSummary(PlaceholderId id);

  /**
   * Measures the rate and time taken for short running tasks.
   *
   * @param id
   *     Identifier created by a call to {@link #createId}
   */
  Timer timer(PlaceholderId id);
}
