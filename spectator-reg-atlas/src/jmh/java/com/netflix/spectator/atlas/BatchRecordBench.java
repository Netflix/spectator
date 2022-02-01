/*
 * Copyright 2014-2022 Netflix, Inc.
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
package com.netflix.spectator.atlas;

import com.netflix.spectator.api.Clock;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@State(Scope.Thread)
public class BatchRecordBench {

  /**
   * This benchmark captures the relative difference between the batch and the iterative
   * record() loop when using something like the DistributionSummary.
   *
   * This also contains a prototype FastClock to compare this against the system
   * clocksource for timing performance. Results below.
   *
   * For both of these tests, the TSC clocksource is used, verified with:
   * (nfsuper) ~ $ cat /sys/devices/system/clocksource/clocksource0/current_clocksource
   * tsc
   *
   * Sample results as follows:
   * =======
   * For an m5.xlarge
   * Base AMI: bionic-classicbase-x86_64-202112020555-ebs
   *
   * The baseline result as of commit 0d09722b8adc5403f767e0d1bbd827c76c2794e0 on m5.xlarge:
   * BatchRecordBench.distributionOneAtATime  thrpt   25  43.896 ± 0.789  ops/s
   *
   * As of this commit:
   * Benchmark                                  (clock)   Mode  Cnt     Score   Error  Units
   * BatchRecordBench.distributionBatch       fastclock  thrpt   25  3849.495 ± 2.079  ops/s
   * BatchRecordBench.distributionBatch          system  thrpt   25  3847.196 ± 3.770  ops/s
   * BatchRecordBench.distributionOneAtATime  fastclock  thrpt   25   105.432 ± 0.187  ops/s
   * BatchRecordBench.distributionOneAtATime     system  thrpt   25   105.436 ± 0.111  ops/s
   *
   * =======
   * For an r5.xlarge
   * Base AMI: bionic-classicbase-x86_64-202201262157-ebs
   *
   * The baseline result as of commit 0d09722b8adc5403f767e0d1bbd827c76c2794e0:
   * BatchRecordBench.distributionOneAtATime  thrpt   25  40.099 ± 0.539  ops/s
   *
   * As of this commit:
   * Benchmark                                  (clock)   Mode  Cnt     Score    Error  Units
   * BatchRecordBench.distributionBatch       fastclock  thrpt   25  3484.762 ± 23.742  ops/s
   * BatchRecordBench.distributionBatch          system  thrpt   25  3531.986 ± 20.345  ops/s
   * BatchRecordBench.distributionOneAtATime  fastclock  thrpt   25    91.483 ±  0.985  ops/s
   * BatchRecordBench.distributionOneAtATime     system  thrpt   25    92.787 ±  1.798  ops/s
   * =======
   */

  private AtlasRegistry registry;
  private AtlasDistributionSummary dist;

  private long[] amounts;

  @Param({ "fastclock", "system" })
  public String clock;

  private Clock clockInstance;

  void selectClock() {
    switch (clock) {
      case "fastclock": clockInstance = new FastClock(); return;
      case "system": clockInstance = Clock.SYSTEM; return;
      default: throw new UnsupportedOperationException("invalid clock type selected, should be 'fastclock' or 'system'");
    }
  }

  @Setup
  public void setup() {
    selectClock();
    registry = new AtlasRegistry(clockInstance, System::getProperty);
    dist = new AtlasDistributionSummary(registry.createId("test"), Clock.SYSTEM, 10_000, 10_000);

    amounts = new long[100_000];
    Random r = new Random(42);
    for (int i = 0; i < amounts.length; i++) {
      amounts[i] = r.nextInt(2000);
    }
  }

  @TearDown
  public void tearDown() throws Exception {
    if (clockInstance instanceof FastClock) {
      ((AutoCloseable) clockInstance).close();
    }
  }

  @Benchmark
  public void distributionOneAtATime(Blackhole bh) {
    for (long amount : amounts) {
      dist.record(amount);
    }
    bh.consume(dist);
  }

  @Benchmark
  public void distributionBatch(Blackhole bh) {
    dist.record(amounts, amounts.length);
    bh.consume(dist);
  }

  public final class FastClock implements Clock, AutoCloseable {
    private final AtomicLong now;
    private final ScheduledExecutorService exec;
    private ScheduledFuture future;

    public FastClock() {
      now = new AtomicLong(System.currentTimeMillis());
      exec = Executors.newSingleThreadScheduledExecutor();
      future = exec.scheduleWithFixedDelay(this::updateWallTime, 1, 1, TimeUnit.MILLISECONDS);
    }

    private void updateWallTime() {
      now.set(System.currentTimeMillis());
    }

    @Override
    public long wallTime() {
      return now.get();
    }

    @Override
    public long monotonicTime() {
      return System.nanoTime();
    }

    @Override
    public void close() throws Exception {
      future.cancel(true);
      exec.shutdownNow();
    }
  }

}
