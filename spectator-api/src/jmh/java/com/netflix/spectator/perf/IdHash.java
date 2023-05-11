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
package com.netflix.spectator.perf;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.impl.Hash64;
import net.openhft.hashing.LongHashFunction;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Benchmark                             Mode  Cnt        Score        Error   Units
 * IdHash.hash64                        thrpt    5  2379245.474 ±   7379.034   ops/s
 * IdHash.hash64_2                      thrpt    5  4817014.307 ±   5704.027   ops/s
 * IdHash.openhft                       thrpt    5  4910133.221 ± 100296.076   ops/s
 *
 * Benchmark                             Mode  Cnt        Score        Error   Units
 * IdHash.hash64:·gc.alloc.rate.norm    thrpt    5        0.188 ±      0.002    B/op
 * IdHash.hash64_2:·gc.alloc.rate.norm  thrpt    5     1152.095 ±      0.001    B/op
 * IdHash.openhft:·gc.alloc.rate.norm   thrpt    5     1128.095 ±      0.002    B/op
 */
@State(Scope.Thread)
public class IdHash {

  private final Id id = Id.create("http.req.complete")
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

  private final Hash64 h64 = new Hash64();
  private final LongHashFunction xx = LongHashFunction.xx();

  @Benchmark
  public void hash64(Blackhole bh) {
    h64.updateString(id.name());
    for (int i = 1; i < id.size(); ++i) {
      h64.updateChar(':');
      h64.updateString(id.getKey(i));
      h64.updateChar('=');
      h64.updateString(id.getValue(i));
    }
    bh.consume(h64.computeAndReset());
  }

  @Benchmark
  public void hash64_2(Blackhole bh) {
    bh.consume(h64.updateString(id.toString()).computeAndReset());
  }

  @Benchmark
  public void openhft(Blackhole bh) {
    bh.consume(xx.hashChars(id.toString()));
  }
}
