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

import com.netflix.spectator.api.*;

/**
 * Factory for creating instances of activity based meters with placeholders in the
 * identifiers so that the final id can be resolved when the activity takes place.
 */
public final class PlaceholderFactory {

  /**
   * Create a new instance of the factory.
   */
  public static PlaceholderFactory from(Registry registry) {
    return new PlaceholderFactory(registry);
  }

  private final Registry registry;

  private PlaceholderFactory(Registry registry) {
    this.registry = registry;
  }

  /**
   * Creates an identifier with placeholders for a counter, timer, or distribution summary.
   *
   * @param name
   *     Description of the measurement that is being collected.
   * @return
   *     The newly created identifier.
   */
  public DynamicId createId(String name) {
    return new DefaultDynamicId(name);
  }

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
  public DynamicId createId(String name, Iterable<TagFactory> tagFactories) {
    return DefaultDynamicId.createWithFactories(name, tagFactories);
  }

  /**
   * Measures the rate of some activity.  A counter is for continuously incrementing sources like
   * the number of requests that are coming into a server.
   *
   * @param id
   *     Identifier created by a call to {@link #createId}
   */
  public Counter counter(DynamicId id) {
    return new DefaultDynamicCounter(id, registry);
  }

  /**
   * Measures the rate and variation in amount for some activity. For example, it could be used to
   * get insight into the variation in response sizes for requests to a server.
   *
   * @param id
   *     Identifier created by a call to {@link #createId}
   */
  public DistributionSummary distributionSummary(DynamicId id) {
    return new DefaultDynamicDistributionSummary(id, registry);
  }

  /**
   * Measures the rate and time taken for short running tasks.
   *
   * @param id
   *     Identifier created by a call to {@link #createId}
   */
  public Timer timer(DynamicId id) {
    return new DefaultDynamicTimer(id, registry);
  }
}
