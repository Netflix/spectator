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
package com.netflix.spectator.atlas;

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Meter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Base class for core meter types used by AtlasRegistry. */
abstract class AtlasMeter implements Meter {

  /** Base identifier for all measurements supplied by this meter. */
  protected final Id id;

  /** Time source for checking if the meter has expired. */
  protected final Clock clock;

  /** TTL value for an inactive meter. */
  private final long ttl;

  /** Last time this meter was updated. */
  private volatile long lastUpdated;

  /**
   * Reference count for batch updaters. The meter cannot be expired while a batch updater
   * instance is around.
   */
  private AtomicInteger batchUpdaterRefCount;

  /** Create a new instance. */
  AtlasMeter(Id id, Clock clock, long ttl) {
    this.id = id;
    this.clock = clock;
    this.ttl = ttl;
    lastUpdated = clock.wallTime();
    batchUpdaterRefCount = new AtomicInteger(0);
  }

  /**
   * Updates the last updated timestamp for the meter to indicate it is active and should
   * not be considered expired.
   */
  void updateLastModTime(long now) {
    lastUpdated = now;
  }

  /**
   * Updates the reference count for the number of batch updaters.
   */
  void updateRefCount(int amount) {
    batchUpdaterRefCount.addAndGet(amount);
  }

  @Override public Id id() {
    return id;
  }

  @Override public boolean hasExpired() {
    return clock.wallTime() - lastUpdated > ttl && batchUpdaterRefCount.get() <= 0;
  }

  @Override public Iterable<Measurement> measure() {
    long now = clock.wallTime();
    List<Measurement> ms = new ArrayList<>();
    measure(now, (id, timestamp, value) -> ms.add(new Measurement(id, timestamp, value)));
    return ms;
  }

  abstract void measure(long now, MeasurementConsumer consumer);
}
