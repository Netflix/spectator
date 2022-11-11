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

import com.netflix.spectator.api.Timer;

import java.util.concurrent.TimeUnit;

final class AtlasTimerBatchUpdater implements Timer.BatchUpdater {

  private AtlasTimer timer;
  private final int batchSize;

  private int count;
  private double total;
  private double totalOfSquares;
  private long max;

  AtlasTimerBatchUpdater(AtlasTimer timer, int batchSize) {
    this.timer = timer;
    this.batchSize = batchSize;
    timer.updateRefCount(1);
  }

  @Override
  public void record(long amount, TimeUnit unit) {
    ++count;
    if (amount > 0L) {
      final long nanos = unit.toNanos(amount);
      total += nanos;
      totalOfSquares += (double) nanos * nanos;
      if (nanos > max) {
        max = nanos;
      }
    }
    if (count >= batchSize) {
      flush();
    }
  }

  @Override
  public void flush() {
    if (timer != null) {
      timer.update(count, total, totalOfSquares, max);
      count = 0;
      total = 0.0;
      totalOfSquares = 0.0;
      max = 0L;
    }
  }

  @Override
  public void close() throws Exception {
    if (timer != null) {
      flush();
      timer.updateRefCount(-1);
      timer = null;
    }
  }
}
