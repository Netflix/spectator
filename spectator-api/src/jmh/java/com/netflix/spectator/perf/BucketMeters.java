/*
 * Copyright 2014-2020 Netflix, Inc.
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
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.histogram.BucketCounter;
import com.netflix.spectator.api.histogram.BucketDistributionSummary;
import com.netflix.spectator.api.histogram.BucketFunctions;
import com.netflix.spectator.api.histogram.BucketTimer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;

import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
public class BucketMeters {

  private final Registry registry = new DefaultRegistry();

  private final BucketCounter counter = BucketCounter.get(
      registry,
      registry.createId("test.counter"),
      BucketFunctions.ageBiasOld(60, TimeUnit.MILLISECONDS)
  );

  private final BucketTimer timer = BucketTimer.get(
      registry,
      registry.createId("test.timer"),
      BucketFunctions.ageBiasOld(60, TimeUnit.MILLISECONDS)
  );

  private final BucketDistributionSummary dist= BucketDistributionSummary.get(
      registry,
      registry.createId("test.dist"),
      BucketFunctions.bytes(60)
  );

  @Threads(1)
  @Benchmark
  public void counterRecord() {
    counter.record(47000L);
  }

  @Threads(1)
  @Benchmark
  public void timerRecord() {
    timer.record(47000L, TimeUnit.MILLISECONDS);
  }

  @Threads(1)
  @Benchmark
  public void distRecord() {
    dist.record(47L);
  }
}
