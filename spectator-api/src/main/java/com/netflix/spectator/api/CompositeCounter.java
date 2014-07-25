/**
 * Copyright 2014 Netflix, Inc.
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

/** Counter implementation for the composite registry. */
final class CompositeCounter extends CompositeMeter implements Counter {

  private final Counter[] counters;

  /** Create a new instance. */
  CompositeCounter(Id id, Counter[] counters) {
    super(id);
    this.counters = counters;
  }

  @Override protected Meter[] meters() {
    return counters;
  }

  @Override public void increment() {
    for (Counter c : counters) {
      c.increment();
    }
  }

  @Override public void increment(long amount) {
    for (Counter c : counters) {
      c.increment(amount);
    }
  }

  @Override public long count() {
    return (counters.length == 0) ? 0L : counters[0].count();
  }
}
