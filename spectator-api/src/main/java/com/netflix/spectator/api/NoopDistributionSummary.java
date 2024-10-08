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
package com.netflix.spectator.api;

import java.util.Collections;

/** Distribution summary implementation for the no-op registry. */
enum NoopDistributionSummary implements DistributionSummary {

  /** Singleton instance. */
  INSTANCE;

  @Override public Id id() {
    return NoopId.INSTANCE;
  }

  @Override public boolean hasExpired() {
    return false;
  }

  @Override public void record(long amount) {
  }

  @Override public void record(long[] amounts, int n) {
  }

  @Override public Iterable<Measurement> measure() {
    return Collections.emptyList();
  }

  @Override public long count() {
    return 0L;
  }

  @Override public long totalAmount() {
    return 0L;
  }

  @Override
  public BatchUpdater batchUpdater(int batchSize) {
    return NoopBatchUpdater.INSTANCE;
  }

  private enum NoopBatchUpdater implements BatchUpdater {
    INSTANCE;

    @Override public void flush() {
    }

    @Override public void record(long amount) {
    }

    @Override public void close() {
    }
  }
}
