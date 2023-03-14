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
import com.netflix.spectator.api.Tag;

import java.util.ArrayList;
import java.util.List;

/** Base class for core meter types used by AtlasRegistry. */
abstract class AtlasMeter implements Meter {

  /**
   * Add the new tags to the id if they are not already present. Tries to minimize the number
   * of allocations by checking if they are present first.
   */
  static Id addIfMissing(Id id, Tag t1, Tag t2) {
    String k1 = t1.key();
    String k2 = t2.key();
    boolean hasT1 = false;
    boolean hasT2 = false;
    for (int i = 1; i < id.size(); ++i) {
      hasT1 = hasT1 || k1.equals(id.getKey(i));
      hasT2 = hasT2 || k2.equals(id.getKey(i));
      if (hasT1 && hasT2) {
        break;
      }
    }

    if (hasT1 && hasT2) {
      return id;
    } else if (!hasT1 && !hasT2) {
      return id.withTags(t1.key(), t1.value(), t2.key(), t2.value());
    } else if (!hasT1) {
      return id.withTag(t1);
    } else {
      return id.withTag(t2);
    }
  }

  /** Base identifier for all measurements supplied by this meter. */
  protected final Id id;

  /** Time source for checking if the meter has expired. */
  protected final Clock clock;

  /** TTL value for an inactive meter. */
  private final long ttl;

  /** Last time this meter was updated. */
  private volatile long lastUpdated;

  /** Create a new instance. */
  AtlasMeter(Id id, Clock clock, long ttl) {
    this.id = id;
    this.clock = clock;
    this.ttl = ttl;
    lastUpdated = clock.wallTime();
  }

  /**
   * Updates the last updated timestamp for the meter to indicate it is active and should
   * not be considered expired.
   */
  void updateLastModTime(long now) {
    lastUpdated = now;
  }

  @Override public Id id() {
    return id;
  }

  @Override public boolean hasExpired() {
    return hasExpired(clock.wallTime());
  }

  boolean hasExpired(long now) {
    return now - lastUpdated > ttl;
  }

  @Override public Iterable<Measurement> measure() {
    long now = clock.wallTime();
    List<Measurement> ms = new ArrayList<>();
    measure(now, (id, timestamp, value) -> ms.add(new Measurement(id, timestamp, value)));
    return ms;
  }

  abstract void measure(long now, MeasurementConsumer consumer);
}
