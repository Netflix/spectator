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
package com.netflix.spectator.ipc;

import com.netflix.frigga.Names;
import com.netflix.frigga.conventions.sharding.Shard;
import com.netflix.frigga.conventions.sharding.ShardingNamingConvention;
import com.netflix.frigga.conventions.sharding.ShardingNamingResult;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Map;


/**
 * <h3>Throughput</h3>
 *
 * <pre>
 * Benchmark                Mode  Cnt        Score        Error   Units
 * string                  thrpt   10  2390746.885 ± 267046.809   ops/s
 * frigga                  thrpt   10    49131.886 ±   1776.454   ops/s
 * </pre>
 *
 * <h3>Allocations</h3>
 *
 * <pre>
 * Benchmark                Mode  Cnt        Score        Error   Units
 * string     gc.alloc.rate.norm   10      512.164 ±      0.026    B/op
 * frigga     gc.alloc.rate.norm   10    13120.752 ±      1.047    B/op
 * </pre>
 */
@State(Scope.Thread)
public class ShardParsing {

  private final String[] asgs = {
      "application_name",
      "application_name-stack",
      "application_name-stack-x1foo-detail",
      "application_name-stack-x1foo-x2bar-detail_1-detail_2",
      "application_name--x1foo-x1bar-detail",
      "application_name-v001",
      "application_name-stack-v001",
      "application_name-stack-detail-v001",
      "application_name-stack-detail_1-detail_2-v001",
      "application_name--detail-v001"
  };

  private final ShardingNamingConvention convention = new ShardingNamingConvention();

  @Benchmark
  public void string(Blackhole bh) {
    for (String asg : asgs) {
      ServerGroup group = ServerGroup.parse(asg);
      bh.consume(group.shard1());
      bh.consume(group.shard2());
    }
  }

  @Benchmark
  public void frigga(Blackhole bh) {
    for (String asg : asgs) {
      Names group = Names.parseName(asg);
      String detail = group.getDetail();
      if (detail != null) {
        ShardingNamingResult result = convention.extractNamingConvention(group.getDetail());
        if (result.getResult().isPresent()) {
          Map<Integer, Shard> shards = result.getResult().get();
          Shard s1 = shards.get(1);
          if (s1 != null) {
            bh.consume(s1.getShardValue());
          }
          Shard s2 = shards.get(2);
          if (s2 != null) {
            bh.consume(s2.getShardValue());
          }
        }
      }
    }
  }

}
