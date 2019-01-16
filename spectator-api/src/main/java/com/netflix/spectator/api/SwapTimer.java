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

import com.netflix.spectator.impl.SwapMeter;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/** Wraps another timer allowing the underlying type to be swapped. */
final class SwapTimer extends SwapMeter<Timer> implements Timer {

  /** Create a new instance. */
  SwapTimer(Registry registry, Id id, Timer underlying) {
    super(registry, id, underlying);
  }

  @Override public Timer lookup() {
    return registry.timer(id);
  }

  @Override public void record(long amount, TimeUnit unit) {
    get().record(amount, unit);
  }

  @Override public <T> T record(Callable<T> f) throws Exception {
    final long s = registry.clock().monotonicTime();
    try {
      return f.call();
    } finally {
      final long e = registry.clock().monotonicTime();
      record(e - s, TimeUnit.NANOSECONDS);
    }
  }

  @Override public void record(Runnable f) {
    final long s = registry.clock().monotonicTime();
    try {
      f.run();
    } finally {
      final long e = registry.clock().monotonicTime();
      record(e - s, TimeUnit.NANOSECONDS);
    }
  }

  @Override public long count() {
    return get().count();
  }

  @Override public long totalTime() {
    return get().totalTime();
  }
}
