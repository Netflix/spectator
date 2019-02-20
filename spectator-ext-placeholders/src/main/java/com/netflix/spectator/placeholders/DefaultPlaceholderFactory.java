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
package com.netflix.spectator.placeholders;

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DistributionSummary;
import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Timer;

/**
 * Factory for creating instances of activity based meters with placeholders in the
 * identifiers so that the final id can be resolved when the activity takes place.
 */
final class DefaultPlaceholderFactory implements PlaceholderFactory {

  private final Registry registry;

  /**
   * Create a new instance of the factory.
   *
   * @param registry
   *      the registry to use when creating ids
   */
  DefaultPlaceholderFactory(Registry registry) {
    this.registry = registry;
  }

  @Override
  public PlaceholderId createId(String name) {
    return new DefaultPlaceholderId(name, registry);
  }

  @Override
  public PlaceholderId createId(String name, Iterable<TagFactory> tagFactories) {
    return DefaultPlaceholderId.createWithFactories(name, tagFactories, registry);
  }

  @Override public Gauge gauge(PlaceholderId id) {
    return new DefaultPlaceholderGauge(id, registry);
  }

  @Override
  public Counter counter(PlaceholderId id) {
    return new DefaultPlaceholderCounter(id, registry);
  }

  @Override
  public DistributionSummary distributionSummary(PlaceholderId id) {
    return new DefaultPlaceholderDistributionSummary(id, registry);
  }

  @Override
  public Timer timer(PlaceholderId id) {
    return new DefaultPlaceholderTimer(id, registry);
  }
}
