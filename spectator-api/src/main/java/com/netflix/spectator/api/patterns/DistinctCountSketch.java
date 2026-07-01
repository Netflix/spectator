/*
 * Copyright 2014-2026 Netflix, Inc.
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
package com.netflix.spectator.api.patterns;

import com.netflix.spectator.api.BasicTag;
import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Meter;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Statistic;
import com.netflix.spectator.impl.Hash64;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Estimates the number of distinct values seen during a step interval, for example the
 * number of unique users, device ids, or source ips. It is backed by a
 * <a href="https://en.wikipedia.org/wiki/HyperLogLog">HyperLogLog</a> sketch that is
 * decomposed into a fixed set of per-register max-gauges so that:
 *
 * <ul>
 *   <li>recording a value is a constant time, allocation-free operation,</li>
 *   <li>sketches from many instances/nodes/regions merge correctly via the normal
 *   aggregation path (the per-register union is a max),</li>
 *   <li>the cardinality estimate is computed at query time on the backend, and</li>
 *   <li>results can still be sliced and diced by any other dimension on the {@link Id}.</li>
 * </ul>
 *
 * <p>The result is an <b>estimate</b>, not an exact count. With the fixed register count
 * used here the relative standard error is approximately 13%. This is generally good
 * enough for "how many distinct things this interval" style questions at a fixed and
 * predictable publishing cost, independent of the true cardinality (whether it is 10 or
 * 10 million).</p>
 *
 * <p><b>Distinct count sketches are more expensive than a plain counter.</b> Each instance
 * publishes a fixed number of gauges (64). Be diligent about any additional dimensions
 * added to a sketch and ensure they have a small bounded cardinality, the same way you
 * would for a percentile timer.</p>
 */
public final class DistinctCountSketch implements Meter {

  /**
   * Number of HLL registers. This is a fixed global constant: a {@code distinct=R##} tag
   * must mean the same partition of the hash space everywhere or sketches from different
   * sources cannot be merged from the tag alone. There is deliberately no precision knob.
   * Exposed so that consumers reconstructing the sketch (such as the backend estimator) can
   * size the register array consistently.
   */
  public static final int REGISTERS = 64;

  /** Number of low bits of the hash used to select the register ({@code log2(REGISTERS)}). */
  private static final int INDEX_BITS = 6;

  /** Mask to extract the register index from the low bits of the hash. */
  private static final int INDEX_MASK = REGISTERS - 1;

  // Precomputed register tag values (R00..R3F). This avoids repeated String.format calls,
  // which use regex internally to parse the format string, when creating the gauges.
  private static final String[] TAG_VALUES;

  static {
    TAG_VALUES = new String[REGISTERS];
    for (int i = 0; i < REGISTERS; ++i) {
      TAG_VALUES[i] = String.format("R%02X", i);
    }
  }

  /**
   * Only create a new instance of the sketch if there is not a cached copy. The array for
   * keeping track of the gauge per register is large enough to lead to a high allocation
   * rate if the sketch is not reused at a high volume call site.
   */
  private static DistinctCountSketch computeIfAbsent(Registry registry, Id id) {
    Object sketch = registry.state().get(id);
    if (sketch == null) {
      DistinctCountSketch newSketch = new DistinctCountSketch(registry, id);
      sketch = registry.state().putIfAbsent(id, newSketch);
      if (sketch == null) {
        return newSketch;
      }
    }
    return (sketch instanceof DistinctCountSketch)
        ? (DistinctCountSketch) sketch
        : new DistinctCountSketch(registry, id);
  }

  /**
   * Creates a sketch that can be used for estimating the number of distinct values.
   * <b>Distinct count sketches are more expensive than a plain counter.</b> Be diligent
   * about ensuring that any additional dimensions have a small bounded cardinality.
   *
   * @param registry
   *     Registry to use.
   * @param id
   *     Identifier for the metric being registered.
   * @return
   *     Sketch that keeps track of the registers used to estimate the distinct count.
   */
  public static DistinctCountSketch get(Registry registry, Id id) {
    return computeIfAbsent(registry, id);
  }

  /**
   * Return a builder for configuring and retrieving an instance of a distinct count sketch.
   * If the sketch has dynamic dimensions, then the builder can be used with the new
   * dimensions. If the id is the same as an existing sketch, then it will update the same
   * underlying registers in the registry.
   */
  public static IdBuilder<Builder> builder(Registry registry) {
    return new IdBuilder<Builder>(registry) {
      @Override protected Builder createTypeBuilder(Id id) {
        return new Builder(registry, id);
      }
    };
  }

  /**
   * Helper for getting instances of a DistinctCountSketch.
   */
  public static final class Builder extends TagsBuilder<Builder> {

    private final Registry registry;
    private final Id baseId;

    /** Create a new instance. */
    Builder(Registry registry, Id baseId) {
      super();
      this.registry = registry;
      this.baseId = baseId;
    }

    /**
     * Create or get an instance of the distinct count sketch with the specified settings.
     */
    public DistinctCountSketch build() {
      final Id id = baseId.withTags(extraTags);
      return computeIfAbsent(registry, id);
    }
  }

  private final Registry registry;
  private final Id id;
  private final AtomicReferenceArray<Gauge> registers;

  /** Create a new instance. */
  private DistinctCountSketch(Registry registry, Id id) {
    this.registry = registry;
    this.id = id;
    this.registers = new AtomicReferenceArray<>(REGISTERS);
  }

  /** Identifier for the sketch. */
  @Override public Id id() {
    return id;
  }

  /**
   * The sketch publishes its data as the per-register max-gauges, so it has no measurements
   * of its own. This is only implemented so the registry can reclaim the cached sketch from
   * its state once the backing registers have expired (see {@link #hasExpired()}).
   */
  @Override public Iterable<Measurement> measure() {
    return Collections.emptyList();
  }

  /**
   * Returns true once every register that has been created has expired (and so the cached
   * sketch can be dropped from the registry state). A register is created lazily on first
   * use, so a sketch is considered expired until something is recorded into it.
   *
   * <p>This is only called by the registry's periodic state cleanup, not on the recording
   * hot path, so the scan over the registers is inexpensive. It cannot delegate to a single
   * representative register because registers expire independently: a register that receives
   * no value for a while can expire while the sketch is still actively recording into others,
   * so all created registers must be expired before the sketch is reclaimed.</p>
   */
  @Override public boolean hasExpired() {
    for (int i = 0; i < REGISTERS; ++i) {
      Gauge g = registers.get(i);
      if (g != null && !g.hasExpired()) {
        return false;
      }
    }
    return true;
  }

  // Lazily load the gauge for a given register. This avoids the allocation for creating the
  // id and the map lookup after the first time a given register is accessed for a sketch.
  private Gauge registerFor(int i) {
    Gauge g = registers.get(i);
    if (g == null) {
      Id registerId = id.withTags(Statistic.distinct, new BasicTag("distinct", TAG_VALUES[i]));
      g = registry.maxGauge(registerId);
      registers.set(i, g);
    }
    return g;
  }

  // Split the hash into a register index (low INDEX_BITS bits) and rho, the position of the
  // leftmost one bit in the remaining bits (1 + the leading-zero run). The register keeps
  // the max rho seen this step, which is exactly the HLL register value and merges across
  // sources by taking the max.
  private void update(long hash) {
    final int idx = (int) (hash & INDEX_MASK);
    // numberOfLeadingZeros counts the INDEX_BITS bits shifted off the top as zeros, so
    // subtract them back out; the all-zero remainder yields the maximum rho.
    final int rho = Long.numberOfLeadingZeros(hash >>> INDEX_BITS) - INDEX_BITS + 1;
    registerFor(idx).set(rho);
  }

  /** Record a distinct {@code long} value, such as a numeric id. */
  public void record(long value) {
    update(Hash64.hashLong(value));
  }

  /** Record a distinct string value, such as a string id or ip address. */
  public void record(CharSequence value) {
    update(Hash64.hashString(value));
  }

  /** Record a distinct value from its raw bytes. */
  public void record(byte[] value) {
    update(Hash64.hashBytes(value));
  }

  /**
   * Estimate the number of distinct values recorded into this sketch on the local node.
   *
   * <p>This only reflects what was recorded into this instance. The fleet-wide distinct
   * count is computed on the backend, which merges the registers across all sources (by
   * taking the per-register max) before applying the same estimator. The result is an
   * approximation with a relative standard error of roughly {@code 1.04/sqrt(REGISTERS)}.</p>
   */
  public double cardinality() {
    double[] rho = new double[REGISTERS];
    for (int i = 0; i < REGISTERS; ++i) {
      Gauge g = registers.get(i);
      double v = (g == null) ? 0.0 : g.value();
      rho[i] = Double.isNaN(v) ? 0.0 : v;
    }
    return cardinality(rho);
  }

  /**
   * Estimate the number of distinct values from the per-register values of a merged sketch.
   * The input holds the register value (the max rho, or {@code 0} for an unset register) for
   * each of the {@code m} registers; it may be any length, with a relative standard error of
   * roughly {@code 1.04/sqrt(m)}.
   *
   * <p>This is the estimator applied at query time on the backend, after the registers have
   * been merged across the requested space and time aggregation. It is exposed here so that
   * the client and the backend share a single implementation. It uses the standard
   * HyperLogLog estimator with linear counting for the small cardinality range.</p>
   *
   * @param registers
   *     Per-register max rho values for the merged sketch.
   * @return
   *     Estimated number of distinct values.
   */
  public static double cardinality(double[] registers) {
    final int m = registers.length;
    if (m == 0) {
      return 0.0;
    }
    // Bias correction constant; for m >= 128 this is the standard 0.7213 / (1 + 1.079/m),
    // which also gives the commonly cited ~0.709 for the 64 register case used here.
    final double alpha = 0.7213 / (1.0 + 1.079 / m);
    double sum = 0.0;
    int zeros = 0;
    for (int i = 0; i < m; ++i) {
      sum += Math.pow(2.0, -registers[i]);
      if (registers[i] == 0.0) {
        ++zeros;
      }
    }
    double estimate = alpha * m * m / sum;
    if (estimate <= 2.5 * m && zeros != 0) {
      // Small range: linear counting gives a better estimate when registers are still empty.
      return m * Math.log((double) m / zeros);
    }
    return estimate;
  }
}
