/*
 * Copyright 2014-2016 Netflix, Inc.
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

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.impl.StepLong;

import java.util.Collections;

/**
 * Counter that reports a rate per second to Atlas. Note that {@link #count()} will
 * report the number events in the last complete interval rather than the total for
 * the life of the process.
 */
class AtlasCounter implements Counter {

  private final Id id;
  private final StepLong value;
  private final Id stat;

  /** Create a new instance. */
  AtlasCounter(Id id, Clock clock, long step) {
    this.id = id;
    this.value = new StepLong(0L, clock, step);
    this.stat = id.withTag(DsType.rate);
  }

  @Override public Id id() {
    return id;
  }

  @Override public boolean hasExpired() {
    return false;
  }

  @Override public Iterable<Measurement> measure() {
    final double rate = value.pollAsRate();
    final Measurement m = new Measurement(stat, value.timestamp(), rate);
    return Collections.singletonList(m);
  }

  @Override public void increment() {
    value.getCurrent().incrementAndGet();
  }

  @Override public void increment(long amount) {
    value.getCurrent().addAndGet(amount);
  }

  @Override public long count() {
    return value.poll();
  }
}
