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

import com.netflix.spectator.impl.RemovableMeter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


/**
 * Base class for composite implementations of core meter types.
 */
class CompositeMeter<T extends Meter> implements RemovableMeter {

  /** Identifier for the meter. */
  protected final Id id;

  /** Set if the registry ever tells this composite it was removed. See {@link #markRemoved()}. */
  private volatile boolean removed;

  /** Underlying meters that are keeping the data. */
  protected final Collection<T> meters;

  /**
   * Create a new instance.
   *
   * @param id
   *     Identifier for the meter.
   * @param meters
   *     Set of meters that make up the composite.
   */
  CompositeMeter(Id id, Collection<T> meters) {
    this.id = id;
    this.meters = meters;
  }

  @Override public Id id() {
    return this.id;
  }

  @Override public boolean hasExpired() {
    for (Meter m : meters) {
      if (m != null && !m.hasExpired()) return false;
    }
    return true;
  }

  /**
   * {@inheritDoc}
   *
   * <p>This is what {@link CompositeRegistry} installs underneath the wrapper it hands out once
   * it holds more than one registry. Answering here rather than leaving {@code SwapMeter} to
   * fall back on {@link #hasExpired()} keeps the wall clock off the update path for that shape,
   * to the extent the members can answer without it. A member is only that cheap when its
   * registry hands out wrappers over meters that carry the flag; one whose meter derives expiry
   * from a clock still reads it. Since the loop stops at the first member that is not removed,
   * which members are cheap depends on the order the registries were added.</p>
   *
   * <p>Mirrors {@link #hasExpired()} in only reporting when every member does. A member that was
   * removed on its own resolves through its own wrapper, so there is nothing for the composite to
   * rebuild until they all have.</p>
   */
  @Override public boolean isRemoved() {
    if (removed) {
      return true;
    }
    for (Meter m : meters) {
      if (m instanceof RemovableMeter) {
        if (!((RemovableMeter) m).isRemoved()) return false;
      } else if (m != null && !m.hasExpired()) {
        return false;
      }
    }
    return true;
  }

  /**
   * {@inheritDoc} A composite is built for the wrapper that hands it out and is never stored in
   * a registry, so nothing marks one today and the answer comes from the members. Recorded
   * anyway rather than dropped: a mark that went nowhere would leave the wrapper holding this
   * for good, because the flag is preferred over {@link #hasExpired()} and there would be no
   * other way for the answer to change.
   */
  @Override public void markRemoved() {
    removed = true;
  }

  @Override public Iterable<Measurement> measure() {
    final List<Measurement> ms = new ArrayList<>();
    for (Meter m : meters) {
      if (m != null) {
        for (Measurement measurement : m.measure()) {
          ms.add(measurement);
        }
      }
    }
    return ms;
  }
}
