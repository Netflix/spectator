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
package com.netflix.spectator.api;

final class CounterBatchUpdater implements Counter.BatchUpdater {

  private final Counter counter;
  private final int batchSize;

  private int count;
  private double sum;

  CounterBatchUpdater(Counter counter, int batchSize) {
    this.counter = counter;
    this.batchSize = batchSize;
    this.count = 0;
    this.sum = 0.0;
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
    counter.add(sum);
    sum = 0.0;
    count = 0;
  }

  @Override
  public void close() throws Exception {
    flush();
  }
}
