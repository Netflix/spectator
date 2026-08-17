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

  /**
   * Signals that the shape of the registry changed, for example a registry being added to a
   * composite. Participates in {@link #hasExpired()}: a change means callers should stop using
   * this wrapper and obtain a fresh one.
   */
  private final LongSupplier versionSupplier;
  private volatile long currentVersion;

  /**
   * Signals that a meter was removed from the registry, so this wrapper may be holding one that
   * is no longer registered. Kept separate from {@code versionSupplier} on purpose. Meter removal
   * happens on every cleanup pass, whereas a registry level change is rare, and
   * {@link #hasExpired()} is acted on destructively by callers such as {@code PolledMeter}, which
   * drops the meter from the set it aggregates. Folding routine removals into that signal would
   * report healthy meters as expired once per cleanup pass. This one therefore feeds
   * {@link #get()} only.
   */
  private final LongSupplier resolveSupplier;
  private volatile long currentResolveVersion;

  /** Id to use when performing a lookup after expiration. */
  protected final Id id;

  /** Current meter to delegate operations. */
  private volatile T underlying;

  /** Create a new instance. */
  public SwapMeter(Registry registry, LongSupplier versionSupplier, Id id, T underlying) {
    this(registry, versionSupplier, versionSupplier, versionSupplier.getAsLong(), id, underlying);
  }

  /**
   * Create a new instance with a dedicated signal for meter removal, and the value of that signal
   * as observed <i>before</i> {@code underlying} was resolved.
   *
   * <p>{@code resolveVersion} must be sampled before resolving {@code underlying}: sampled
   * afterwards it would already account for a removal racing with the resolution, and this
   * wrapper would stay bound to a meter that is no longer registered, silently dropping every
   * later update. Sampling early can only cause one redundant re-resolution.
   *
   * @param registry
   *     Registry used to lookup the meter after expiration.
   * @param versionSupplier
   *     Supplies the registry level version, used by {@link #hasExpired()}.
   * @param resolveSupplier
   *     Supplies a counter that changes whenever a meter is removed, used by {@link #get()}.
   * @param resolveVersion
   *     Value of {@code resolveSupplier} sampled before {@code underlying} was looked up.
   * @param id
   *     Id to use when performing a lookup after expiration.
   * @param underlying
   *     Meter to delegate operations to.
   */
  public SwapMeter(
      Registry registry,
      LongSupplier versionSupplier,
      LongSupplier resolveSupplier,
      long resolveVersion,
      Id id,
      T underlying) {
    this.registry = registry;
    this.versionSupplier = versionSupplier;
    this.currentVersion = versionSupplier.getAsLong();
    this.resolveSupplier = resolveSupplier;
    this.currentResolveVersion = resolveVersion;
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

  /**
   * {@inheritDoc}
   *
   * <p>Routine meter removal is deliberately not part of this signal; see {@link #resolveSupplier}
   * for why.
   */
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
   * Return the underlying instance of the meter, resolving a new one if the registry may have
   * removed the current instance.
   *
   * <p>This runs on the update path of every meter operation, so it checks a counter rather than
   * {@code underlying.hasExpired()}: the counter is a plain volatile read, whereas the expiry
   * check costs a wall clock read on every update (for {@code AtlasMeter}, a {@code clock_gettime}
   * to evaluate a TTL measured in minutes). This does not change which meter updates land on — a
   * meter that is past its TTL but still registered resolves back to the same instance either
   * way — and once cleanup actually removes the meter the counter moves, so the next call here
   * resolves a fresh one.
   */
  public T get() {
    // Sampled once: re-reading for the assignment could store a value newer than what lookup()
    // actually observed, which would then swallow a subsequent removal.
    final long resolveVersion = resolveSupplier.getAsLong();
    if (currentResolveVersion < resolveVersion) {
      currentResolveVersion = resolveVersion;
      // Resolving also clears the registry level staleness reported by hasExpired(), since the
      // meter being delegated to has just been looked up afresh.
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
