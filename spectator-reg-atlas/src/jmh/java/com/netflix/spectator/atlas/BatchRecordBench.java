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
   * Benchmark                                (batchSize)    (clock)   Mode  Cnt         Score       Error  Units
   * BatchRecordBench.distributionBatch                 1  fastclock  thrpt   25  10653757.154 ± 28278.785  ops/s
   * BatchRecordBench.distributionBatch                 1     system  thrpt   25  10698329.757 ± 32951.571  ops/s
   * BatchRecordBench.distributionBatch                10  fastclock  thrpt   25   9522092.321 ± 28014.036  ops/s
   * BatchRecordBench.distributionBatch                10     system  thrpt   25   9559843.855 ±  6779.719  ops/s
   * BatchRecordBench.distributionBatch               100  fastclock  thrpt   25   2897853.712 ±  8733.913  ops/s
   * BatchRecordBench.distributionBatch               100     system  thrpt   25   2893252.879 ±  7187.279  ops/s
   * BatchRecordBench.distributionBatch              1000  fastclock  thrpt   25    366835.739 ±   208.298  ops/s
   * BatchRecordBench.distributionBatch              1000     system  thrpt   25    367808.344 ±   781.413  ops/s
   * BatchRecordBench.distributionBatch             10000  fastclock  thrpt   25     38245.067 ±    33.743  ops/s
   * BatchRecordBench.distributionBatch             10000     system  thrpt   25     38303.265 ±    33.823  ops/s
   * BatchRecordBench.distributionBatch            100000  fastclock  thrpt   25      3846.227 ±     3.359  ops/s
   * BatchRecordBench.distributionBatch            100000     system  thrpt   25      3847.573 ±     2.944  ops/s
   * BatchRecordBench.distributionOneAtATime            1  fastclock  thrpt   25  11087902.444 ± 18214.743  ops/s
   * BatchRecordBench.distributionOneAtATime            1     system  thrpt   25  11132286.157 ± 33009.169  ops/s
   * BatchRecordBench.distributionOneAtATime           10  fastclock  thrpt   25   1089841.847 ±  1166.172  ops/s
   * BatchRecordBench.distributionOneAtATime           10     system  thrpt   25   1090766.388 ±  1124.590  ops/s
   * BatchRecordBench.distributionOneAtATime          100  fastclock  thrpt   25    109084.262 ±   498.868  ops/s
   * BatchRecordBench.distributionOneAtATime          100     system  thrpt   25    108806.769 ±   109.836  ops/s
   * BatchRecordBench.distributionOneAtATime         1000  fastclock  thrpt   25     11547.783 ±    28.136  ops/s
   * BatchRecordBench.distributionOneAtATime         1000     system  thrpt   25     11548.784 ±    37.187  ops/s
   * BatchRecordBench.distributionOneAtATime        10000  fastclock  thrpt   25      1051.379 ±     1.347  ops/s
   * BatchRecordBench.distributionOneAtATime        10000     system  thrpt   25      1054.978 ±     2.069  ops/s
   * BatchRecordBench.distributionOneAtATime       100000  fastclock  thrpt   25       105.588 ±     0.366  ops/s
   * BatchRecordBench.distributionOneAtATime       100000     system  thrpt   25       105.453 ±     0.186  ops/s
   * 
   * =======
   * For an r5.xlarge
   * Base AMI: bionic-classicbase-x86_64-202201262157-ebs
   *
   * The baseline result as of commit 0d09722b8adc5403f767e0d1bbd827c76c2794e0:
   * BatchRecordBench.distributionOneAtATime  thrpt   25  40.099 ± 0.539  ops/s
   *
   * As of this commit:
   * Benchmark                                (batchSize)    (clock)   Mode  Cnt        Score        Error  Units
   * BatchRecordBench.distributionBatch                 1  fastclock  thrpt   25  9385655.567 ± 340778.340  ops/s
   * BatchRecordBench.distributionBatch                 1     system  thrpt   25  9668568.661 ± 289469.212  ops/s
   * BatchRecordBench.distributionBatch                10  fastclock  thrpt   25  8364336.008 ± 379818.726  ops/s
   * BatchRecordBench.distributionBatch                10     system  thrpt   25  8291598.971 ± 335957.214  ops/s
   * BatchRecordBench.distributionBatch               100  fastclock  thrpt   25  2690204.344 ±  71589.264  ops/s
   * BatchRecordBench.distributionBatch               100     system  thrpt   25  2655837.607 ±  84584.223  ops/s
   * BatchRecordBench.distributionBatch              1000  fastclock  thrpt   25   337184.218 ±  10589.541  ops/s
   * BatchRecordBench.distributionBatch              1000     system  thrpt   25   338195.706 ±   8983.223  ops/s
   * BatchRecordBench.distributionBatch             10000  fastclock  thrpt   25    35384.994 ±    940.235  ops/s
   * BatchRecordBench.distributionBatch             10000     system  thrpt   25    35098.662 ±    898.807  ops/s
   * BatchRecordBench.distributionBatch            100000  fastclock  thrpt   25     3544.591 ±    106.967  ops/s
   * BatchRecordBench.distributionBatch            100000     system  thrpt   25     3481.677 ±     92.268  ops/s
   * BatchRecordBench.distributionOneAtATime            1  fastclock  thrpt   25  9101177.222 ± 327727.423  ops/s
   * BatchRecordBench.distributionOneAtATime            1     system  thrpt   25  9215129.213 ± 450134.957  ops/s
   * BatchRecordBench.distributionOneAtATime           10  fastclock  thrpt   25   898688.979 ±  47922.252  ops/s
   * BatchRecordBench.distributionOneAtATime           10     system  thrpt   25   928435.778 ±  29301.506  ops/s
   * BatchRecordBench.distributionOneAtATime          100  fastclock  thrpt   25    96247.965 ±   3416.122  ops/s
   * BatchRecordBench.distributionOneAtATime          100     system  thrpt   25    95073.152 ±   3232.757  ops/s
   * BatchRecordBench.distributionOneAtATime         1000  fastclock  thrpt   25     9977.180 ±    263.313  ops/s
   * BatchRecordBench.distributionOneAtATime         1000     system  thrpt   25    10139.411 ±    351.895  ops/s
   * BatchRecordBench.distributionOneAtATime        10000  fastclock  thrpt   25      921.888 ±     28.627  ops/s
   * BatchRecordBench.distributionOneAtATime        10000     system  thrpt   25      893.432 ±     27.858  ops/s
   * BatchRecordBench.distributionOneAtATime       100000  fastclock  thrpt   25       91.880 ±      3.205  ops/s
   * BatchRecordBench.distributionOneAtATime       100000     system  thrpt   25       92.206 ±      3.617  ops/s
   * =======
   */

  private AtlasRegistry registry;
  private AtlasDistributionSummary dist;

  private long[] amounts;

  @Param({ "fastclock", "system" })
  public String clock;

  @Param({ "1", "10", "100", "1000", "10000", "100000" })
  public String batchSize;

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

    amounts = new long[Integer.parseInt(batchSize)];
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
