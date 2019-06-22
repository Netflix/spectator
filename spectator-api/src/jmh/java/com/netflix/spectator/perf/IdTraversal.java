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

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Tag;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

/**
 * <pre>
 * Benchmark                             Mode  Cnt        Score        Error   Units
 * IdTraversal.forEach                  thrpt   10  9804067.543 ±  70757.222   ops/s
 * IdTraversal.iterator                 thrpt   10  6431905.789 ± 814667.475   ops/s
 * </pre>
 *
 * In an actual app we measured a big difference in allocations for the Tag objects. For
 * this test hotspot seems to be able to avoid most of them:
 *
 * https://shipilev.net/jvm/anatomy-quarks/18-scalar-replacement/
 *
 * <pre>
 * Benchmark                             Mode  Cnt        Score        Error   Units
 * IdTraversal.forEach     gc.alloc.rate.norm   10        0.039 ±      0.001    B/op
 * IdTraversal.iterator    gc.alloc.rate.norm   10        0.059 ±      0.006    B/op
 * </pre>
 */
@State(Scope.Thread)
public class IdTraversal {

  private final Id baseId = Id.create("http.req.complete")
      .withTag(    "nf.app", "test_app")
      .withTag("nf.cluster", "test_app-main")
      .withTag(    "nf.asg", "test_app-main-v042")
      .withTag(  "nf.stack", "main")
      .withTag(    "nf.ami", "ami-0987654321")
      .withTag( "nf.region", "us-east-1")
      .withTag(   "nf.zone", "us-east-1e")
      .withTag(   "nf.node", "i-1234567890")
      .withTag(   "country", "US")
      .withTag(    "device", "xbox")
      .withTag(    "status", "200")
      .withTag(    "client", "ab");

  @Benchmark
  public void iterator(Blackhole bh) {
    for (Tag t : baseId) {
      bh.consume(t.key());
      bh.consume(t.value());
    }
  }

  @Benchmark
  public void forEach(Blackhole bh) {
    baseId.forEach((k, v) -> {
      bh.consume(k);
      bh.consume(v);
    });
  }
}
