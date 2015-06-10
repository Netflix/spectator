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
package com.netflix.spectator.tdigest;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Timer;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Timer that updates a T-Digest with recorded values.
 */
class TDigestTimer implements TDigestMeter, Timer {

  private final StepDigest digest;
  private final Timer underlying;

  /** Create a new instance. */
  TDigestTimer(StepDigest digest, Timer underlying) {
    this.digest = digest;
    this.underlying = underlying;
  }

  @Override public void record(long amount, TimeUnit unit) {
    if (amount >= 0L) {
      final long nanos = unit.toNanos(amount);
      digest.add(nanos / 1e9);
      underlying.record(amount, unit);
    }
  }

  @Override public <T> T record(Callable<T> f) throws Exception {
    final long start = digest.clock().monotonicTime();
    try {
      return f.call();
    } finally {
      record(digest.clock().monotonicTime() - start, TimeUnit.NANOSECONDS);
    }
  }

  @Override public void record(Runnable f) {
    final long start = digest.clock().monotonicTime();
    try {
      f.run();
    } finally {
      record(digest.clock().monotonicTime() - start, TimeUnit.NANOSECONDS);
    }
  }

  /**
   * Return a percentile from the last complete digest.
   *
   * @param p
   *     Percentile to retrieve. The value should be in the interval [0.0, 100.0].
   * @return
   *     The {@code p}th percentile of the last complete digest.
   */
  public double percentile(double p) {
    if (p < 0.0 || p > 100.0) {
      throw new IllegalArgumentException("p should be in [0.0, 100.0], got " + p);
    }
    final double q = p / 100.0;
    return digest.poll().quantile(q);
  }

  @Override public long count() {
    return underlying.count();
  }

  @Override public long totalTime() {
    return underlying.totalTime();
  }

  @Override public Id id() {
    return digest.id();
  }

  @Override public Iterable<Measurement> measure() {
    return underlying.measure();
  }

  @Override public boolean hasExpired() {
    return false;
  }

  @Override public TDigestMeasurement measureDigest() {
    return digest.measure();
  }
}
