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
package com.netflix.spectator.api;

/**
 * Distribution summary implementation that delegates the value tracking to
 * component distribution summaries based on the current value of the tags
 * associated with the DynamicId when the interface methods are called.
 *
 * @deprecated Use {@code spectator-ext-placeholders} library instead.
 */
@Deprecated
class DefaultDynamicDistributionSummary extends AbstractDefaultDynamicMeter<DistributionSummary>
        implements DistributionSummary {
  /**
   * Constructs a new distribution summary with the specified dynamic id.
   *
   * @param id the dynamic (template) id for generating the individual distribution summaries
   * @param registry the registry to use to instantiate the individual distribution summaries
   */
  DefaultDynamicDistributionSummary(DynamicId id, Registry registry) {
    super(id, registry::distributionSummary);
  }

  @Override
  public void record(long amount) {
    resolveToCurrentMeter().record(amount);
  }

  @Override
  public long count() {
    return resolveToCurrentMeter().count();
  }

  @Override
  public long totalAmount() {
    return resolveToCurrentMeter().totalAmount();
  }
}
