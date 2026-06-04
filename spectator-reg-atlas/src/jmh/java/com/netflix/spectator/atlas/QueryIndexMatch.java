/*
 * Copyright 2014-2025 Netflix, Inc.
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

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.atlas.impl.Parser;
import com.netflix.spectator.atlas.impl.Query;
import com.netflix.spectator.atlas.impl.QueryIndex;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Exercises the hot matching path used by the LWC bridge: an index of many subscription
 * queries matched against a stream of ids. The dominant cost in profiles is
 * {@link QueryIndex#forEachMatch} walking the sorted keys of each id and comparing them
 * against the keys stored in the index.
 *
 * <pre>
 * Benchmark              Mode  Cnt  Score   Error  Units
 * QueryIndexMatch.match  avgt    5    ...
 * </pre>
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class QueryIndexMatch {

  private static final String[] KEYS = {
      "nf.app", "nf.cluster", "nf.asg", "nf.region", "nf.zone", "nf.stack",
      "device", "country", "status", "statistic"
  };

  private static String val(Random r) {
    char c = (char) ('a' + r.nextInt(8));
    return String.valueOf(c);
  }

  private QueryIndex<Integer> idx;
  private List<Id> ids;

  @Setup
  public void setup() {
    Registry registry = new NoopRegistry();
    Random r = new Random(42);

    // Build a realistic set of subscriptions: each anchored on the name plus a couple of
    // additional dimension clauses (mix of :eq and :re), combined with :and.
    idx = QueryIndex.newInstance(registry);
    int numQueries = 5_000;
    for (int i = 0; i < numQueries; ++i) {
      StringBuilder sb = new StringBuilder();
      sb.append("name,m").append(r.nextInt(50)).append(",:eq");
      int extra = 1 + r.nextInt(3);
      for (int j = 0; j < extra; ++j) {
        String k = KEYS[r.nextInt(KEYS.length)];
        if (r.nextInt(4) == 0) {
          sb.append(',').append(k).append(',').append(val(r)).append(",:re,:and");
        } else {
          sb.append(',').append(k).append(',').append(val(r)).append(",:eq,:and");
        }
      }
      Query q = Parser.parseQuery(sb.toString());
      idx.add(q, i);
    }

    // Build a stream of ids with a name and several sorted tags, matching the shape of
    // datapoints flowing through the bridge.
    ids = new ArrayList<>();
    int numIds = 200;
    for (int i = 0; i < numIds; ++i) {
      Id id = Id.create("m" + r.nextInt(50));
      int numTags = 4 + r.nextInt(5);
      for (int j = 0; j < numTags; ++j) {
        id = id.withTag(KEYS[r.nextInt(KEYS.length)], val(r));
      }
      ids.add(id);
    }
  }

  @Benchmark
  public void match(Blackhole bh) {
    for (int i = 0; i < ids.size(); ++i) {
      idx.forEachMatch(ids.get(i), bh::consume);
    }
  }
}
