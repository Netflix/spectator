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

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Arrays;

/**
 * <pre>
 * sort1_single                     thrpt    5  231987417.092 ± 1461766.430   ops/s
 * sort2_single                     thrpt    5  231390816.374 ± 2900091.223   ops/s
 *
 * sort1_two                        thrpt    5  127862169.706 ± 3004299.720   ops/s
 * sort2_two                        thrpt    5  107286992.610 ±  499836.589   ops/s
 *
 * sort1_four                       thrpt    5   45448061.771 ±  214688.930   ops/s
 * sort2_four                       thrpt    5   45801768.604 ±  399120.395   ops/s
 *
 * sort1_many                       thrpt    5    7013914.451 ±  476174.932   ops/s
 * sort2_many                       thrpt    5    7093001.872 ±  136273.382   ops/s
 * </pre>
 */
@State(Scope.Thread)
public class ArrayTagSetSort {

  private static void insertionSort1(String[] ts, int length) {
    if (length == 4) {
      // Two key/value pairs, swap if needed
      if (ts[0].compareTo(ts[2]) > 0) {
        // Swap key
        String tmp = ts[0];
        ts[0] = ts[2];
        ts[2] = tmp;

        // Swap value
        tmp = ts[1];
        ts[1] = ts[3];
        ts[3] = tmp;
      }
    } else if (length > 4) {
      // One entry is already sorted. Two entries handled above, for larger arrays
      // use insertion sort.
      for (int i = 2; i < length; i += 2) {
        String k = ts[i];
        String v = ts[i + 1];
        int j = i - 2;
        for (; j >= 0 && ts[j].compareTo(k) > 0; j -= 2) {
          ts[j + 2] = ts[j];
          ts[j + 3] = ts[j + 1];
        }
        ts[j + 2] = k;
        ts[j + 3] = v;
      }
    }
  }

  private static void insertionSort2(String[] ts, int length) {
    for (int i = 2; i < length; i += 2) {
      String k = ts[i];
      String v = ts[i + 1];
      int j = i - 2;
      for (; j >= 0 && ts[j].compareTo(k) > 0; j -= 2) {
        ts[j + 2] = ts[j];
        ts[j + 3] = ts[j + 1];
      }
      ts[j + 2] = k;
      ts[j + 3] = v;
    }
  }

  private final String[] tagsArraySingle = new String[] {
        "country", "US"
  };

  private final String[] tagsArrayTwo = new String[] {
         "status", "200",
         "client", "ab"
  };

  private final String[] tagsArrayFour = new String[] {
        "country", "US",
         "device", "xbox",
         "status", "200",
         "client", "ab"
  };

  private final String[] tagsArrayMany = new String[] {
          "nf.app", "test_app",
      "nf.cluster", "test_app-main",
          "nf.asg", "test_app-main-v042",
        "nf.stack", "main",
          "nf.ami", "ami-0987654321",
       "nf.region", "us-east-1",
         "nf.zone", "us-east-1e",
         "nf.node", "i-1234567890",
         "country", "US",
          "device", "xbox",
          "status", "200",
          "client", "ab"
  };

  @Benchmark
  public void sort1_single(Blackhole bh) {
    String[] tags = Arrays.copyOf(tagsArraySingle, tagsArraySingle.length);
    insertionSort1(tags, tags.length);
    bh.consume(tags);
  }

  @Benchmark
  public void sort2_single(Blackhole bh) {
    String[] tags = Arrays.copyOf(tagsArraySingle, tagsArraySingle.length);
    insertionSort2(tags, tags.length);
    bh.consume(tags);
  }

  @Benchmark
  public void sort1_two(Blackhole bh) {
    String[] tags = Arrays.copyOf(tagsArrayTwo, tagsArrayTwo.length);
    insertionSort1(tags, tags.length);
    bh.consume(tags);
  }

  @Benchmark
  public void sort2_two(Blackhole bh) {
    String[] tags = Arrays.copyOf(tagsArrayTwo, tagsArrayTwo.length);
    insertionSort2(tags, tags.length);
    bh.consume(tags);
  }

  @Benchmark
  public void sort1_four(Blackhole bh) {
    String[] tags = Arrays.copyOf(tagsArrayFour, tagsArrayFour.length);
    insertionSort1(tags, tags.length);
    bh.consume(tags);
  }

  @Benchmark
  public void sort2_four(Blackhole bh) {
    String[] tags = Arrays.copyOf(tagsArrayFour, tagsArrayFour.length);
    insertionSort2(tags, tags.length);
    bh.consume(tags);
  }

  @Benchmark
  public void sort1_many(Blackhole bh) {
    String[] tags = Arrays.copyOf(tagsArrayMany, tagsArrayMany.length);
    insertionSort1(tags, tags.length);
    bh.consume(tags);
  }

  @Benchmark
  public void sort2_many(Blackhole bh) {
    String[] tags = Arrays.copyOf(tagsArrayMany, tagsArrayMany.length);
    insertionSort2(tags, tags.length);
    bh.consume(tags);
  }
}
