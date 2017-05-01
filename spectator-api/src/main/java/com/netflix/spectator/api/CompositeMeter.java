/*
 * Copyright 2014-2017 Netflix, Inc.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


/**
 * Base class for composite implementations of core meter types.
 */
class CompositeMeter<T extends Meter> implements Meter {

  /** Identifier for the meter. */
  protected final Id id;

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
  public CompositeMeter(Id id, Collection<T> meters) {
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
