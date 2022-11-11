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

import java.util.concurrent.TimeUnit;

final class TimerBatchUpdater implements Timer.BatchUpdater {

  private final Timer timer;
  private final int batchSize;

  private int count;
  private final long[] amounts;

  TimerBatchUpdater(Timer timer, int batchSize) {
    this.timer = timer;
    this.batchSize = batchSize;
    this.count = 0;
    this.amounts = new long[batchSize];
  }

  @Override
  public void record(long amount, TimeUnit unit) {
    if (amount >= 0L) {
      amounts[count++] = unit.toNanos(amount);
      if (count >= batchSize) {
        flush();
      }
    }
  }

  @Override
  public void flush() {
    timer.record(amounts, count, TimeUnit.NANOSECONDS);
    count = 0;
  }

  @Override
  public void close() throws Exception {
    flush();
  }
}
