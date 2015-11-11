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

import com.tdunning.math.stats.AVLTreeDigest;
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

/**
 * Benchmark                               Mode  Samples    Score    Error  Units
 * c.n.s.t.Merge.avlMerge_10              thrpt       50  339.736 ± 91.386  ops/s
 * c.n.s.t.Merge.avlMerge_100             thrpt       50  229.965 ± 61.328  ops/s
 * c.n.s.t.Merge.avlMerge_1000            thrpt       50  230.850 ± 72.407  ops/s
 * c.n.s.t.Merge.treeMerge_10             thrpt       50  304.664 ± 80.990  ops/s
 * c.n.s.t.Merge.treeMerge_100            thrpt       50  203.731 ± 48.526  ops/s
 * c.n.s.t.Merge.treeMerge_1000           thrpt       50  249.626 ± 86.817  ops/s
 */
@State(Scope.Benchmark)
public class Merge {

  private final List<TDigest> treeValues = new ArrayList<>();
  private final List<TDigest> avlValues = new ArrayList<>();
  private final Random random = new Random();

  @Setup(Level.Iteration)
  public void setup() {
    treeValues.clear();
    avlValues.clear();
    Random random = new Random();
    for (int i = 0; i < 2; ++i) {
      TDigest tree = new TreeDigest(100.0);
      TDigest avl = new AVLTreeDigest(100.0);
      for (int j = 0; j < 1000; ++j) {
        double v = random.nextDouble();
        tree.add(v);
        avl.add(v);
      }
      treeValues.add(tree);
      avlValues.add(avl);
    }
  }

  @Threads(1)
  @Benchmark
  public void treeMerge_10(Blackhole bh) {
    TDigest merged = TreeDigest.merge(10.0, treeValues, random);
    bh.consume(merged);
  }

  @Threads(1)
  @Benchmark
  public void treeMerge_100(Blackhole bh) {
    TDigest merged = TreeDigest.merge(100.0, treeValues, random);
    bh.consume(merged);
  }

  @Threads(1)
  @Benchmark
  public void treeMerge_1000(Blackhole bh) {
    TDigest merged = TreeDigest.merge(1000.0, treeValues, random);
    bh.consume(merged);
  }

  @Threads(1)
  @Benchmark
  public void avlMerge_02(Blackhole bh) {
    TDigest merged = TreeDigest.merge(2.0, avlValues, random);
    bh.consume(merged);
  }

  @Threads(1)
  @Benchmark
  public void avlMerge_05(Blackhole bh) {
    TDigest merged = TreeDigest.merge(5.0, avlValues, random);
    bh.consume(merged);
  }

  @Threads(1)
  @Benchmark
  public void avlMerge_10(Blackhole bh) {
    TDigest merged = TreeDigest.merge(10.0, avlValues, random);
    bh.consume(merged);
  }

  @Threads(1)
  @Benchmark
  public void avlMerge_100(Blackhole bh) {
    TDigest merged = TreeDigest.merge(100.0, avlValues, random);
    bh.consume(merged);
  }

  @Threads(1)
  @Benchmark
  public void avlMerge_1000(Blackhole bh) {
    TDigest merged = TreeDigest.merge(1000.0, avlValues, random);
    bh.consume(merged);
  }
}
