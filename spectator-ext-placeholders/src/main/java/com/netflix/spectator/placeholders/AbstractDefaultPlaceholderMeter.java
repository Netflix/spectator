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
package com.netflix.spectator.placeholders;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Meter;

import java.util.function.Function;

/**
 * Base class for dynamic meters that provides implementations for the core
 * interface methods.
 */
abstract class AbstractDefaultPlaceholderMeter<T extends Meter> implements Meter {
  private final PlaceholderId id;
  private final Function<Id, T> meterResolver;

  /**
   * Creates a new dynamic meter.
   *
   * @param id the dynamic id for the meter
   * @param meterResolver the function to map a resolved id to concrete metric
   */
  AbstractDefaultPlaceholderMeter(PlaceholderId id, Function<Id, T> meterResolver) {
    this.id = id;
    this.meterResolver = meterResolver;
  }

  /**
   * Resolve the dynamic id to the current metric instance.
   */
  protected final T resolveToCurrentMeter() {
    return meterResolver.apply(id());
  }

  @Override
  public final Id id() {
    return id.resolveToId();
  }

  @Override
  public final Iterable<Measurement> measure() {
    return resolveToCurrentMeter().measure();
  }

  @Override
  public final boolean hasExpired() {
    // Without tracking all of the regular meters that are created from this
    // dynamic meter we don't have any way of knowing whether the "master"
    // counter has expired.  Instead of adding the tracking, we choose to
    // rely on the regular expiration mechanism for the underlying meters.
    return false;
  }
}
