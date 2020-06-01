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
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Statistic;
import com.netflix.spectator.api.Tag;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Benchmark different approaches to override the statistic tag if present.
 *
 * <pre>
 * Benchmark                      Mode  Cnt        Score        Error  Units
 * DefaultStat.withIdWithTags    thrpt    9  2693269.613 ± 189599.585  ops/s
 * DefaultStat.withNameWithTags  thrpt    9  3485856.299 ± 116456.216  ops/s
 * </pre>
 */
@State(Scope.Thread)
public class DefaultStat {
  private final Registry registry = new DefaultRegistry();

  private final Id baseId = registry.createId("http.req.complete")
      .withTag("nf.app", "test_app")
      .withTag("nf.cluster", "test_app-main")
      .withTag("nf.asg", "test_app-main-v042")
      .withTag("nf.stack", "main")
      .withTag("nf.ami", "ami-0987654321")
      .withTag("nf.region", "us-east-1")
      .withTag("nf.zone", "us-east-1e")
      .withTag("nf.node", "i-1234567890");

  @Threads(1)
  @Benchmark
  public void withIdWithTags(Blackhole bh) {
    bh.consume(baseId.withTag(Statistic.count).withTags(baseId.tags()).withTag(DsType.rate));
  }

  @Threads(1)
  @Benchmark
  public void withNameWithTags(Blackhole bh) {
    bh.consume(
        registry.createId(
            baseId.name()).withTag(Statistic.count).withTags(baseId.tags()).withTag(DsType.rate));
  }

  static enum DsType implements Tag {
    gauge,
    rate;

    @Override
    public String key() {
      return "atlas.dstype";
    }

    @Override
    public String value() {
      return name();
    }
  }
}
