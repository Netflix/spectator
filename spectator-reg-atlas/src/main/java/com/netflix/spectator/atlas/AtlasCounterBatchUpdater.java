/*
 * Copyright 2014-2025 Netflix, Inc.
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

import com.netflix.spectator.api.Counter;

import java.util.function.Consumer;
import java.util.function.Supplier;

final class AtlasCounterBatchUpdater implements Counter.BatchUpdater, Consumer<Supplier<Counter>> {

  private Supplier<Counter> counterSupplier;
  private final int batchSize;

  private int count;
  private double sum;

  AtlasCounterBatchUpdater(int batchSize) {
    this.batchSize = batchSize;
    this.count = 0;
    this.sum = 0.0;
  }

  @Override
  public void accept(Supplier<Counter> counterSupplier) {
    this.counterSupplier = counterSupplier;
  }

  private AtlasCounter getCounter() {
    if (counterSupplier != null) {
      Counter c = counterSupplier.get();
      return (c instanceof AtlasCounter) ? (AtlasCounter) c : null;
    }
    return null;
  }

  @Override
  public void add(double amount) {
    if (Double.isFinite(amount) && amount > 0.0) {
      sum += amount;
      ++count;
      if (count >= batchSize) {
        flush();
      }
    }
  }

  @Override
  public void flush() {
    AtlasCounter counter = getCounter();
    if (counter != null) {
      counter.add(sum);
      sum = 0.0;
      count = 0;
    }
  }

  @Override
  public void close() throws Exception {
    flush();
  }
}
