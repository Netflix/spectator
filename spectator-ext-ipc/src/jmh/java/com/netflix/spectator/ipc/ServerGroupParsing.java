/*
 * Copyright 2014-2018 Netflix, Inc.
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
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;


/**
 * <h3>Throughput</h3>
 *
 * <pre>
 * Benchmark                Mode  Cnt        Score       Error   Units
 * charSequence            thrpt   10  3216353.588 ± 87966.348   ops/s
 * string                  thrpt   10  2476131.141 ± 32765.943   ops/s
 * frigga                  thrpt   10    11745.242 ±  2367.298   ops/s
 * </pre>
 *
 * <h3>Allocations</h3>
 *
 * <pre>
 * Benchmark                Mode  Cnt        Score       Error   Units
 * charSequence            thrpt   10      784.171 ±     0.006    B/op
 * string                  thrpt   10     1120.221 ±     0.005    B/op
 * frigga                  thrpt   10   124987.101 ±    11.368    B/op
 * </pre>
 */
@State(Scope.Thread)
public class ServerGroupParsing {

  private final String[] asgs = {
      "application_name",
      "application_name-stack",
      "application_name-stack-detail",
      "application_name-stack-detail_1-detail_2",
      "application_name--detail",
      "application_name-v001",
      "application_name-stack-v001",
      "application_name-stack-detail-v001",
      "application_name-stack-detail_1-detail_2-v001",
      "application_name--detail-v001"
  };

  @Benchmark
  public void string(Blackhole bh) {
    for (String asg : asgs) {
      StringServerGroup group = StringServerGroup.parse(asg);
      bh.consume(group.app());
      bh.consume(group.cluster());
    }
  }

  @Benchmark
  public void charSequence(Blackhole bh) {
    for (String asg : asgs) {
      ServerGroup group = ServerGroup.parse(asg);
      bh.consume(group.app());
      bh.consume(group.cluster());
    }
  }

  @Benchmark
  public void frigga(Blackhole bh) {
    for (String asg : asgs) {
      Names group = Names.parseName(asg);
      bh.consume(group.getApp());
      bh.consume(group.getCluster());
    }
  }

}
