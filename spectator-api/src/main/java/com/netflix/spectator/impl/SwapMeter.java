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
public abstract class SwapMeter<T extends Meter> implements Meter {

  /** Registry used to lookup values after expiration. */
  protected final Registry registry;

  // Changes when the shape of the registry changes, for example a registry being added to a
  // composite. Feeds hasExpired().
  private final LongSupplier versionSupplier;
  private volatile long currentVersion;

  // Changes when a meter is removed, so this wrapper may be holding one that is no longer
  // registered. Feeds get() only, never hasExpired(): callers such as PolledMeter treat expiry as
  // licence to discard the meter, and removal happens on every cleanup pass.
  private final LongSupplier resolveSupplier;
  private volatile long currentResolveVersion;

  // Set only by the constructor with no removal signal, so registries without one, such as
  // CompositeRegistry, keep the original trigger.
  private final boolean resolveOnUnderlyingExpiry;

  /** Id to use when performing a lookup after expiration. */
  protected final Id id;

  /** Current meter to delegate operations. */
  private volatile T underlying;

  /** Create a new instance. */
  public SwapMeter(Registry registry, LongSupplier versionSupplier, Id id, T underlying) {
    this(registry, versionSupplier, versionSupplier, versionSupplier.getAsLong(), id, underlying,
        true);
  }

  /**
   * Create a new instance with a dedicated signal for meter removal. {@code resolveVersion} must
   * be sampled from {@code resolveSupplier} <i>before</i> {@code underlying} is resolved: sampled
   * afterwards it would already account for a removal racing the resolution, leaving this wrapper
   * bound to a meter that is no longer registered and silently dropping every later update.
   * Sampling early can only cost one redundant re-resolution.
   */
  public SwapMeter(
      Registry registry,
      LongSupplier versionSupplier,
      LongSupplier resolveSupplier,
      long resolveVersion,
      Id id,
      T underlying) {
    this(registry, versionSupplier, resolveSupplier, resolveVersion, id, underlying, false);
  }

  private SwapMeter(
      Registry registry,
      LongSupplier versionSupplier,
      LongSupplier resolveSupplier,
      long resolveVersion,
      Id id,
      T underlying,
      boolean resolveOnUnderlyingExpiry) {
    this.registry = registry;
    this.versionSupplier = versionSupplier;
    this.currentVersion = versionSupplier.getAsLong();
    this.resolveSupplier = resolveSupplier;
    this.currentResolveVersion = resolveVersion;
    this.resolveOnUnderlyingExpiry = resolveOnUnderlyingExpiry;
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

  /** {@inheritDoc} Routine meter removal is deliberately not part of this signal. */
  @Override public boolean hasExpired() {
    return currentVersion < versionSupplier.getAsLong() || underlying.hasExpired();
  }

  /**
   * Set the underlying instance of the meter to use. This can be set to {@code null}
   * to indicate that the meter has expired and is no longer in the registry.
   */
  public void set(T meter) {
    underlying = unwrap(meter);
  }

  /**
   * Return the underlying meter, resolving a new one if the registry may have removed it.
   *
   * <p>This runs on every meter update, so it checks a counter rather than
   * {@code underlying.hasExpired()}, which for {@code AtlasMeter} costs a wall clock read. Which
   * meter an update lands on is unchanged: one past its TTL but still registered resolves back to
   * the same instance either way, and once cleanup removes it the counter moves.</p>
   */
  public T get() {
    // Sampled once: re-reading for the assignment could store a value newer than what lookup()
    // observed, which would swallow a subsequent removal.
    final long resolveVersion = resolveSupplier.getAsLong();
    if (currentResolveVersion < resolveVersion) {
      currentResolveVersion = resolveVersion;
      // Resolving also clears the staleness hasExpired() reports, since the meter just came from
      // a fresh lookup.
      currentVersion = versionSupplier.getAsLong();
      underlying = unwrap(lookup());
    } else if (resolveOnUnderlyingExpiry && underlying.hasExpired()) {
      currentVersion = versionSupplier.getAsLong();
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
