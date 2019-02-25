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
package com.netflix.spectator.perf;

import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Timer;
import com.netflix.spectator.api.histogram.PercentileTimer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;

import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
public class PercentileTimers {

  private final Registry registry = new DefaultRegistry();

  private final Timer defaultTimerCached = registry.timer("default-cached");

  private final PercentileTimer percentileTimerCached =
      PercentileTimer.get(registry, registry.createId("percentile-cached"));

  private final Id dfltId = registry.createId("default");

  private final Id pctId = registry.createId("percentile");

  @Threads(1)
  @Benchmark
  public void defaultTimerGet() {
    registry.timer(dfltId).record(31, TimeUnit.MILLISECONDS);
  }

  @Threads(1)
  @Benchmark
  public void percentileTimerGet() {
    PercentileTimer.get(registry, pctId).record(31, TimeUnit.MILLISECONDS);
  }

  @Threads(1)
  @Benchmark
  public void percentileTimerBuilder() {
    PercentileTimer.builder(registry)
        .withId(pctId)
        .withRange(10, 10000, TimeUnit.MILLISECONDS)
        .build()
        .record(31, TimeUnit.MILLISECONDS);
  }

  @Threads(1)
  @Benchmark
  public void defaultTimerReuse() {
    defaultTimerCached.record(31, TimeUnit.MILLISECONDS);
  }

  @Threads(1)
  @Benchmark
  public void percentileTimerReuse() {
    percentileTimerCached.record(31, TimeUnit.MILLISECONDS);
  }

}
