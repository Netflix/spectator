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

import java.util.Collection;
import java.util.Iterator;

/** Distribution summary implementation for the composite registry. */
final class CompositeDistributionSummary extends CompositeMeter implements DistributionSummary {

  /** Create a new instance. */
  CompositeDistributionSummary(Id id, Collection<Registry> registries) {
    super(id, registries);
  }

  @Override public void record(long amount) {
    for (Registry r : registries) {
      r.distributionSummary(id).record(amount);
    }
  }

  @Override public long count() {
    Iterator<Registry> it = registries.iterator();
    return it.hasNext() ? it.next().distributionSummary(id).count() : 0L;
  }

  @Override public long totalAmount() {
    Iterator<Registry> it = registries.iterator();
    return it.hasNext() ? it.next().distributionSummary(id).totalAmount() : 0L;
  }
}
