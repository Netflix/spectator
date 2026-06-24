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
package com.netflix.spectator.perf;

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.patterns.DistinctCountSketch;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;

import java.nio.charset.StandardCharsets;

/**
 * Hot path cost of recording into a {@link DistinctCountSketch} relative to a plain counter.
 * The {@code record} overloads measure the hash plus register update (the per-observation
 * cost); the {@code get} variant additionally pays the cache lookup that a call site avoids
 * by holding onto the sketch instance.
 *
 * <p>The recorded values are fixed so the benchmark isolates the recording cost rather than
 * the allocation of the input. The {@code long} and {@code byte[]} overloads are allocation
 * free; the {@code CharSequence} overload routes through {@link com.netflix.spectator.impl.Hash64}
 * which is non-allocating for short strings (see {@code IdHash} for the string hashing
 * numbers).</p>
 */
@State(Scope.Thread)
public class DistinctCountSketches {

  private final Registry registry = new DefaultRegistry();

  private final Counter counterCached = registry.counter("default-cached");

  private final DistinctCountSketch sketchCached =
      DistinctCountSketch.get(registry, registry.createId("sketch-cached"));

  private final Id sketchId = registry.createId("sketch");

  private final long longValue = 0x0123456789ABCDEFL;

  // Short ASCII id, the common string key case (user id, ip, session id).
  private final String stringValue = "i-0123456789abcdef";

  private final byte[] bytesValue = "i-0123456789abcdef".getBytes(StandardCharsets.UTF_8);

  @Threads(1)
  @Benchmark
  public void counterReuse() {
    counterCached.increment();
  }

  @Threads(1)
  @Benchmark
  public void sketchRecordLongReuse() {
    sketchCached.record(longValue);
  }

  @Threads(1)
  @Benchmark
  public void sketchRecordStringReuse() {
    sketchCached.record(stringValue);
  }

  @Threads(1)
  @Benchmark
  public void sketchRecordBytesReuse() {
    sketchCached.record(bytesValue);
  }

  @Threads(1)
  @Benchmark
  public void sketchRecordLongGet() {
    DistinctCountSketch.get(registry, sketchId).record(longValue);
  }
}
