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
package com.netflix.spectator.spark;

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Timer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Timer that keeps track of all samples since the last measurement to forward to the sidecar.
 */
class SidecarTimer implements Timer {

  private static final int MAX_VALUES = 10000;

  private final Clock clock;
  private final Id id;
  private final LinkedBlockingQueue<Measurement> values = new LinkedBlockingQueue<>(MAX_VALUES);

  /** Create a new instance. */
  SidecarTimer(Clock clock, Id id) {
    this.clock = clock;
    this.id = id.withTag(DataType.TIMER);
  }

  @Override public Id id() {
    return id;
  }

  @Override public Clock clock() {
    return clock;
  }

  @Override public void record(long amount, TimeUnit unit) {
    Measurement m = new Measurement(id, clock.wallTime(), unit.toMillis(amount));
    values.offer(m);
  }

  @Override public long count() {
    return 0L;
  }

  @Override public long totalTime() {
    return 0L;
  }

  @Override public Iterable<Measurement> measure() {
    List<Measurement> ms = new ArrayList<>();
    values.drainTo(ms);
    return ms;
  }

  @Override public boolean hasExpired() {
    return false;
  }
}
