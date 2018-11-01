/*
 * Copyright 2014-2018 Netflix, Inc.
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
package com.netflix.spectator.micrometer;

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Meter;

import java.util.ArrayList;
import java.util.List;

/** Base class for core meter types used by {@link MicrometerRegistry}. */
abstract class MicrometerMeter implements Meter {

  /** Base identifier for all measurements supplied by this meter. */
  private final Id id;

  /** Create a new instance. */
  MicrometerMeter(Id id) {
    this.id = id;
  }

  @Override public Id id() {
    return id;
  }

  @Override public boolean hasExpired() {
    return false;
  }

  /** Helper for converting Micrometer measurements to Spectator measurements. */
  Iterable<Measurement> convert(Iterable<io.micrometer.core.instrument.Measurement> ms) {
    long now = Clock.SYSTEM.wallTime();
    List<Measurement> measurements = new ArrayList<>();
    for (io.micrometer.core.instrument.Measurement m : ms) {
      Id measurementId = id.withTag("statistic", m.getStatistic().getTagValueRepresentation());
      measurements.add(new Measurement(measurementId, now, m.getValue()));
    }
    return measurements;
  }
}
