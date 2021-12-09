/*
 * Copyright 2014-2021 Netflix, Inc.
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

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.List;

@State(Scope.Thread)
public class ListIteration {

  private List<Integer> data;

  @Setup
  public void setup() {
    data = new ArrayList<>(100);
    for (int i = 0; i < 100; ++i) {
      data.add(i);
    }
  }

  @Benchmark
  public void forEach(Blackhole bh) {
    for (Integer i : data) {
      bh.consume(i);
    }
  }

  @Benchmark
  public void forUsingGet(Blackhole bh) {
    int n = data.size();
    for (int i = 0; i < n; ++i) {
      bh.consume(data.get(i));
    }
  }
}
