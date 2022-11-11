/*
 * Copyright 2014-2022 Netflix, Inc.
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

import com.netflix.spectator.api.DistributionSummary;

import java.util.function.Consumer;
import java.util.function.Supplier;

final class AtlasDistSummaryBatchUpdater
    implements DistributionSummary.BatchUpdater, Consumer<Supplier<DistributionSummary>> {

  private Supplier<DistributionSummary> distSummarySupplier;
  private final int batchSize;

  private int count;
  private long total;
  private double totalOfSquares;
  private long max;

  AtlasDistSummaryBatchUpdater(int batchSize) {
    this.batchSize = batchSize;
  }

  @Override
  public void accept(Supplier<DistributionSummary> distSummarySupplier) {
    this.distSummarySupplier = distSummarySupplier;
  }

  private AtlasDistributionSummary getDistributionSummary() {
    if (distSummarySupplier != null) {
      DistributionSummary d = distSummarySupplier.get();
      return (d instanceof AtlasDistributionSummary) ? (AtlasDistributionSummary) d : null;
    }
    return null;
  }

  @Override
  public void record(long amount) {
    ++count;
    if (amount > 0L) {
      total += amount;
      totalOfSquares += (double) amount * amount;
      if (amount > max) {
        max = amount;
      }
    }
    if (count >= batchSize) {
      flush();
    }
  }

  @Override
  public void flush() {
    AtlasDistributionSummary distSummary = getDistributionSummary();
    if (distSummary != null) {
      distSummary.update(count, total, totalOfSquares, max);
      count = 0;
      total = 0L;
      totalOfSquares = 0.0;
      max = 0L;
    }
  }

  @Override
  public void close() throws Exception {
    flush();
  }
}
