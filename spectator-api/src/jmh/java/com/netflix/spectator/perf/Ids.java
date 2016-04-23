/**
 * Copyright 2015 Netflix, Inc.
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
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@State(Scope.Thread)
public class Ids {

  private final Registry registry = new DefaultRegistry();

  @Threads(1)
  @Benchmark
  public void justName(Blackhole bh) {
    bh.consume(registry.createId("http.req.complete"));
  }

  @Threads(1)
  @Benchmark
  public void withTags(Blackhole bh) {
    Id id = registry.createId("http.req.complete")
        .withTag("nf.app", "test_app")
        .withTag("nf.cluster", "test_app-main")
        .withTag("nf.asg", "test_app-main-v042")
        .withTag("nf.stack", "main")
        .withTag("nf.ami", "ami-0987654321")
        .withTag("nf.region", "us-east-1")
        .withTag("nf.zone", "us-east-1e")
        .withTag("nf.node", "i-1234567890")
        .withTag("country", "US")
        .withTag("device", "xbox")
        .withTag("status", "200")
        .withTag("client", "ab");
    bh.consume(id);
  }

  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
        .include(".*")
        .forks(1)
        .build();
    new Runner(opt).run();
  }
}
