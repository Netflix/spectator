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
package com.netflix.spectator.tdigest;

import com.tdunning.math.stats.TDigest;
import com.tdunning.math.stats.TreeDigest;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@State(Scope.Benchmark)
public class Merge {

  private final List<TDigest> values = new ArrayList<TDigest>();
  private final Random random = new Random();

  @Setup(Level.Iteration)
  public void setup() {
    Random random = new Random();
    for (int i = 0; i < 1000; ++i) {
      TDigest digest = new TreeDigest(100.0);
      int num = random.nextInt(1000);
      for (int j = 0; j < num; ++j) {
        digest.add(random.nextDouble());
      }
      values.add(digest);
    }
  }

  @Threads(1)
  @Benchmark
  public void digestMerge_10(Blackhole bh) {
    TDigest merged = TreeDigest.merge(10.0, values, random);
    bh.consume(merged);
  }

  @Threads(1)
  @Benchmark
  public void digestMerge_100(Blackhole bh) {
    TDigest merged = TreeDigest.merge(100.0, values, random);
    bh.consume(merged);
  }

  @Threads(1)
  @Benchmark
  public void digestMerge_1000(Blackhole bh) {
    TDigest merged = TreeDigest.merge(1000.0, values, random);
    bh.consume(merged);
  }
}
