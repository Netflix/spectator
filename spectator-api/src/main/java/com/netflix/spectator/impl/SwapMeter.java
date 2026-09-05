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

import java.util.function.LongSupplier;

/**
 * Base type for meters that allow the underlying implementation to be replaced with
 * another. This is used by {@link com.netflix.spectator.api.AbstractRegistry} as the
 * basis for expiring types where a user may have a reference in their code.
 *
 * <p><b>This class is an internal implementation detail only intended for use within
 * spectator. It is subject to change without notice.</b></p>
 */
public abstract class SwapMeter<T extends Meter> implements RemovableMeter {

  /** Registry used to lookup values after expiration. */
  protected final Registry registry;

  private final LongSupplier versionSupplier;
  private volatile long currentVersion;

  /** Id to use when performing a lookup after expiration. */
  protected final Id id;

  /** Current meter to delegate operations. */
  private volatile T underlying;

  /** Create a new instance. */
  public SwapMeter(Registry registry, LongSupplier versionSupplier, Id id, T underlying) {
    this.registry = registry;
    this.versionSupplier = versionSupplier;
    this.currentVersion = versionSupplier.getAsLong();
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
    return currentVersion < versionSupplier.getAsLong() || underlying.hasExpired();
  }

  /**
   * {@inheritDoc}
   *
   * <p>Answers for the meter this is wrapping, so a wrapper nested inside another one, as
   * {@code CompositeRegistry} hands out, passes the cheap flag through rather than forcing the
   * outer wrapper onto a wall clock read. This wrapper's own version is part of the answer: a
   * shape change means the outer wrapper has to resolve again too.</p>
   */
  @Override public boolean isRemoved() {
    return isStale(underlying) || currentVersion < versionSupplier.getAsLong();
  }

  /**
   * {@inheritDoc} A registry stores the meters it creates, never the wrappers it hands out, so
   * nothing ever marks one of these: the answer comes from what it wraps.
   */
  @Override public void markRemoved() {
  }

  /**
   * Set the underlying instance of the meter to use. This can be set to {@code null}
   * to indicate that the meter has expired and is no longer in the registry.
   */
  public void set(T meter) {
    underlying = unwrap(meter);
  }

  /**
   * Return the underlying instance of the meter, resolving a new one if the registry no longer
   * holds the one this is wrapping.
   *
   * <p>This runs on every update through a reference the caller holds, so it asks the meter
   * whether it is still registered rather than whether it has expired. For a meter with a TTL
   * the second question costs a wall clock read; the first is a flag the registry sets on the
   * cleanup pass. A meter type that cannot answer it falls back to expiry, which is a safe
   * over-approximation: one past its TTL but still registered resolves back to itself.</p>
   */
  public T get() {
    T meter = underlying;
    // Read once and reuse: the value stored has to be the one that was compared, otherwise a
    // bump landing between the two reads is recorded as already seen.
    final long version = versionSupplier.getAsLong();
    if (isStale(meter) || currentVersion < version) {
      currentVersion = version;
      meter = unwrap(lookup());
      underlying = meter;
    }
    return meter;
  }

  /** Whether the meter has to be looked up again before it is used. */
  private static boolean isStale(Meter meter) {
    if (meter instanceof RemovableMeter) {
      return ((RemovableMeter) meter).isRemoved();
    }
    // The null check is on this branch rather than in front of the type test so it costs
    // nothing for a meter that can answer. A null underlying is what set() documents as the
    // meter being gone from the registry, so it has to resolve rather than be dereferenced.
    return meter == null || meter.hasExpired();
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
