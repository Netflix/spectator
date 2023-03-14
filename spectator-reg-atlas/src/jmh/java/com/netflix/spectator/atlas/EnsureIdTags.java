/*
 * Copyright 2014-2023 Netflix, Inc.
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

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Statistic;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

/**
 * <pre>
 * Benchmark                          Mode  Cnt         Score        Error   Units
 * checkMissing                      thrpt    5   7446459.299 ± 236506.306   ops/s
 * checkMissing:·gc.alloc.rate       thrpt    5      1875.207 ±     59.556  MB/sec
 * checkMissing:·gc.alloc.rate.norm  thrpt    5       264.062 ±      0.003    B/op
 * checkMissing:·gc.count            thrpt    5       313.000               counts
 * checkMissing:·gc.time             thrpt    5       153.000                   ms
 * checkMissing:·stack               thrpt                NaN                  ---
 * checkPresent                      thrpt    5  48437646.563 ± 601766.743   ops/s
 * checkPresent:·gc.alloc.rate       thrpt    5         0.426 ±      0.006  MB/sec
 * checkPresent:·gc.alloc.rate.norm  thrpt    5         0.009 ±      0.001    B/op
 * checkPresent:·gc.count            thrpt    5         1.000               counts
 * checkPresent:·gc.time             thrpt    5         2.000                   ms
 * checkPresent:·stack               thrpt                NaN                  ---
 * newIdMissing                      thrpt    5   6415187.273 ± 188476.396   ops/s
 * newIdMissing:·gc.alloc.rate       thrpt    5      2300.752 ±     67.568  MB/sec
 * newIdMissing:·gc.alloc.rate.norm  thrpt    5       376.071 ±      0.004    B/op
 * newIdMissing:·gc.count            thrpt    5       384.000               counts
 * newIdMissing:·gc.time             thrpt    5       194.000                   ms
 * newIdMissing:·stack               thrpt                NaN                  ---
 * newIdPresent                      thrpt    5   7329062.490 ±  67842.114   ops/s
 * newIdPresent:·gc.alloc.rate       thrpt    5      2740.286 ±     25.401  MB/sec
 * newIdPresent:·gc.alloc.rate.norm  thrpt    5       392.062 ±      0.001    B/op
 * newIdPresent:·gc.count            thrpt    5       373.000               counts
 * newIdPresent:·gc.time             thrpt    5       190.000                   ms
 * newIdPresent:·stack               thrpt                NaN                  ---
 * </pre>
 */
@State(Scope.Thread)
public class EnsureIdTags {

  private static final Id BASE_ID = Id.create("ipc.server.call")
      .withTag("nf.app", "www")
      .withTag("nf.cluster", "www-main")
      .withTag("nf.asg", "www-main-v001")
      .withTag("nf.stack", "main")
      .withTag("nf.node", "i-1234567890")
      .withTag("nf.region", "us-east-1")
      .withTag("nf.zone", "us-east-1c")
      .withTag("nf.vmtype", "m5.xlarge")
      .withTag("ipc.client.app", "db")
      .withTag("ipc.client.cluster", "db-main")
      .withTag("ipc.client.asg", "db-main-v042")
      .withTag("ipc.endpoint", "/query")
      .withTag("ipc.status", "success")
      .withTag("ipc.status.detail", "200")
      .withTag("ipc.result", "success");

  private final Id STAT_ID = BASE_ID.withTag(Statistic.count).withTag(DsType.rate);

  @Benchmark
  public void newIdMissing(Blackhole bh) {
    Id stat = Id.create(BASE_ID.name())
        .withTag(Statistic.count)
        .withTag(DsType.rate)
        .withTags(BASE_ID.tags());
    bh.consume(stat);
  }

  @Benchmark
  public void newIdPresent(Blackhole bh) {
    Id stat = Id.create(STAT_ID.name())
        .withTag(Statistic.count)
        .withTag(DsType.rate)
        .withTags(STAT_ID.tags());
    bh.consume(stat);
  }

  @Benchmark
  public void checkMissing(Blackhole bh) {
    Id stat = AtlasMeter.addIfMissing(BASE_ID, Statistic.count, DsType.rate);
    bh.consume(stat);
  }

  @Benchmark
  public void checkPresent(Blackhole bh) {
    Id stat = AtlasMeter.addIfMissing(STAT_ID, Statistic.count, DsType.rate);
    bh.consume(stat);
  }
}
