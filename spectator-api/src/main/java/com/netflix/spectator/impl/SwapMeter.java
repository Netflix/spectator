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
package com.netflix.spectator.impl;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Meter;
import com.netflix.spectator.api.Registry;

/**
 * Base type for meters that allow the underlying implementation to be replaced with
 * another. This is used by {@link com.netflix.spectator.api.AbstractRegistry} as the
 * basis for expiring types where a user may have a reference in their code.
 *
 * <p><b>This class is an internal implementation detail only intended for use within
 * spectator. It is subject to change without notice.</b></p>
 */
public abstract class SwapMeter<T extends Meter> implements Meter {

  /** Registry used to lookup values after expiration. */
  protected final Registry registry;

  /** Id to use when performing a lookup after expiration. */
  protected final Id id;

  /** Current meter to delegate operations. */
  private volatile T underlying;

  /** Create a new instance. */
  public SwapMeter(Registry registry, Id id, T underlying) {
    this.registry = registry;
    this.id = id;
    this.underlying = unwrap(underlying);
  }

  /**
   * Lookup the meter from the registry.
   */
  public abstract T lookup();

  @Override public Id id() {
    return id;
  }

  @Override public Iterable<Measurement> measure() {
    return get().measure();
  }

  @Override public boolean hasExpired() {
    return underlying.hasExpired();
  }

  /**
   * Set the underlying instance of the meter to use. This can be set to {@code null}
   * to indicate that the meter has expired and is no longer in the registry.
   */
  public void set(T meter) {
    underlying = unwrap(meter);
  }

  /** Return the underlying instance of the meter. */
  public T get() {
    if (underlying.hasExpired()) {
      underlying = unwrap(lookup());
    }
    return underlying;
  }

  /**
   * If the values are nested, then unwrap any that have the same registry instance.
   */
  @SuppressWarnings("unchecked")
  private T unwrap(T meter) {
    T tmp = meter;
    while (tmp instanceof SwapMeter<?> && registry == ((SwapMeter<?>) tmp).registry) {
      tmp = ((SwapMeter<T>) tmp).underlying;
    }
    return tmp;
  }
}
