/**
 * Copyright 2015 Netflix, Inc.
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
import com.netflix.spectator.api.Registry;

/**
 * Counter implementation that delegates the value tracking to component counters
 * based on the current value of the tags associated with the PlaceholderId when the
 * increment methods are called.
 */
class DefaultPlaceholderCounter extends AbstractDefaultPlaceholderMeter<Counter> implements Counter {
  /**
   * Constructs a new counter with the specified dynamic id.
   *
   * @param id the dynamic (template) id for generating the individual counters
   * @param registry the registry to use to instantiate the individual counters
   */
  DefaultPlaceholderCounter(PlaceholderId id, Registry registry) {
    super(id, registry, registry::counter);
  }

  @Override
  public void increment() {
    resolveToCurrentMeter().increment();
  }

  @Override
  public void increment(long amount) {
    resolveToCurrentMeter().increment(amount);
  }

  @Override
  public long count() {
    return resolveToCurrentMeter().count();
  }
}
