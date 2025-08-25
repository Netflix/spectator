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

import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.TagList;
import com.netflix.spectator.api.TagListBuilder;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.infra.Blackhole;

import java.util.HashMap;
import java.util.Map;

/**
 * <pre>
 * Benchmark               Throughput (ops/s)      Error
 * -----------------------------------------------------------
 * append1                 56,794,079.309      ±1,529,009.842
 * append2                 29,813,536.825        ±242,156.195
 * append4                  6,473,803.925        ±915,892.515
 * append4sorted            7,021,400.576        ±251,550.333
 * baseline                49,883,390.694        ±431,626.254
 * emptyAppend1           200,362,500.375      ±1,438,114.615
 * emptyAppend2            78,510,857.178        ±125,060.880
 * emptyAppend4            14,228,870.597        ±350,789.283
 * emptyAppend4sorted      16,585,176.379        ±387,667.119
 * justName               437,797,027.428     ±24,884,978.092
 * unsafeCreate            12,531,161.873      ±4,295,429.889
 * withTag                  3,294,585.839        ±124,723.980
 * withTagsBuilder          3,213,126.689         ±37,382.051
 * withTagsBuilderSorted   10,095,083.206        ±569,033.538
 * withTagsMap              4,088,836.194        ±104,959.027
 * withTagsVararg           2,860,050.248         ±39,131.668
 * withTagsVarargSorted     8,168,124.655        ±154,663.209
 * </pre>
 *
 * <pre>
 * Benchmark               Alloc Rate Norm (B/op)   Error
 * -----------------------------------------------------------
 * append1                 136.008                ±0.001
 * append2                 208.015                ±0.001
 * append4                 256.071                ±0.011
 * append4sorted           256.066                ±0.002
 * baseline                312.009                ±0.001
 * emptyAppend1             72.002                ±0.001
 * emptyAppend2            112.006                ±0.001
 * emptyAppend4            144.032                ±0.001
 * emptyAppend4sorted      144.027                ±0.001
 * justName                 24.001                ±0.001
 * unsafeCreate             48.036                ±0.013
 * withTag               1,416.133                ±0.005
 * withTagsBuilder         184.140                ±0.003
 * withTagsBuilderSorted   184.044                ±0.003
 * withTagsMap             224.115                ±0.002
 * withTagsVararg          296.167                ±0.002
 * withTagsVarargSorted    296.058                ±0.001
 * </pre>
 */
@State(Scope.Thread)
public class Ids {

  private final Registry registry = new DefaultRegistry();

  private final Map<String, String> tags = getTags();

  private final String[] tagsArray = new String[] {
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

  private Map<String, String> getTags() {
    Map<String, String> m = new HashMap<>();
    m.put(    "nf.app", "test_app");
    m.put("nf.cluster", "test_app-main");
    m.put(    "nf.asg", "test_app-main-v042");
    m.put(  "nf.stack", "main");
    m.put(    "nf.ami", "ami-0987654321");
    m.put( "nf.region", "us-east-1");
    m.put(   "nf.zone", "us-east-1e");
    m.put(   "nf.node", "i-1234567890");
    m.put(   "country", "US");
    m.put(    "device", "xbox");
    m.put(    "status", "200");
    m.put(    "client", "ab");
    return m;
  }

  private final Id emptyId = registry.createId("http.req.complete");

  private final Id baseId = emptyId
      .withTag(    "nf.app", "test_app")
      .withTag("nf.cluster", "test_app-main")
      .withTag(    "nf.asg", "test_app-main-v042")
      .withTag(  "nf.stack", "main")
      .withTag(    "nf.ami", "ami-0987654321")
      .withTag( "nf.region", "us-east-1")
      .withTag(   "nf.zone", "us-east-1e")
      .withTag(   "nf.node", "i-1234567890");

  private final TagListBuilder builder = TagListBuilder.create();

  @Threads(1)
  @Benchmark
  public void justName(Blackhole bh) {
    bh.consume(registry.createId("http.req.complete"));
  }

  @Threads(1)
  @Benchmark
  public void unsafeCreate(Blackhole bh) {
    bh.consume(Id.unsafeCreate("http.req.complete", tagsArray, tagsArray.length));
  }

  @Threads(1)
  @Benchmark
  public void baseline(Blackhole bh) {
    PrependId id = new PrependId("http.req.complete", null)
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
    bh.consume(id);
  }

  @Threads(1)
  @Benchmark
  public void withTag(Blackhole bh) {
    Id id = registry.createId("http.req.complete")
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
    bh.consume(id);
  }

  @Threads(1)
  @Benchmark
  public void withTagsVararg(Blackhole bh) {
    Id id = registry.createId("http.req.complete").withTags(
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
            "client", "ab");
    bh.consume(id);
  }

  @Threads(1)
  @Benchmark
  public void withTagsVarargSorted(Blackhole bh) {
    Id id = registry.createId("http.req.complete").withTags(
            "client", "ab",
           "country", "US",
            "device", "xbox",
            "nf.ami", "ami-0987654321",
            "nf.app", "test_app",
            "nf.asg", "test_app-main-v042",
        "nf.cluster", "test_app-main",
           "nf.node", "i-1234567890",
         "nf.region", "us-east-1",
          "nf.stack", "main",
           "nf.zone", "us-east-1e",
            "status", "200"
    );
    bh.consume(id);
  }

  @Threads(1)
  @Benchmark
  public void withTagsBuilder(Blackhole bh) {
    TagList ts = builder
        .add("nf.app", "test_app")
        .add("nf.cluster", "test_app-main")
        .add("nf.asg", "test_app-main-v042")
        .add("nf.stack", "main")
        .add("nf.ami", "ami-0987654321")
        .add("nf.region", "us-east-1")
        .add("nf.zone", "us-east-1e")
        .add("nf.node", "i-1234567890")
        .add("country", "US")
        .add("device", "xbox")
        .add("status", "200")
        .add("client", "ab")
        .buildAndReset();
    Id id = registry.createId("http.req.complete").withTags(ts);
    bh.consume(id);
  }

  @Threads(1)
  @Benchmark
  public void withTagsBuilderSorted(Blackhole bh) {
    TagList ts = builder
        .add("client", "ab")
        .add("country", "US")
        .add("device", "xbox")
        .add("nf.ami", "ami-0987654321")
        .add("nf.app", "test_app")
        .add("nf.asg", "test_app-main-v042")
        .add("nf.cluster", "test_app-main")
        .add("nf.node", "i-1234567890")
        .add("nf.region", "us-east-1")
        .add("nf.stack", "main")
        .add("nf.zone", "us-east-1e")
        .add("status", "200")
        .buildAndReset();
    Id id = registry.createId("http.req.complete").withTags(ts);
    bh.consume(id);
  }

  @Threads(1)
  @Benchmark
  public void withTagsMap(Blackhole bh) {
    Id id = registry.createId("http.req.complete").withTags(tags);
    bh.consume(id);
  }

  @Threads(1)
  @Benchmark
  public void append1(Blackhole bh) {
    Id id = baseId.withTag("country", "US");
    bh.consume(id);
  }

  @Threads(1)
  @Benchmark
  public void append2(Blackhole bh) {
    Id id = baseId.withTags(
        "country", "US",
         "device", "xbox");
    bh.consume(id);
  }

  @Threads(1)
  @Benchmark
  public void append4(Blackhole bh) {
    Id id = baseId.withTags(
        "country", "US",
         "device", "xbox",
         "status", "200",
         "client", "ab");
    bh.consume(id);
  }

  @Threads(1)
  @Benchmark
  public void append4sorted(Blackhole bh) {
    Id id = baseId.withTags(
        "client", "ab",
       "country", "US",
        "device", "xbox",
        "status", "200");
    bh.consume(id);
  }

  @Threads(1)
  @Benchmark
  public void emptyAppend1(Blackhole bh) {
    Id id = emptyId.withTag("country", "US");
    bh.consume(id);
  }

  @Threads(1)
  @Benchmark
  public void emptyAppend2(Blackhole bh) {
    Id id = emptyId.withTags(
        "country", "US",
        "device", "xbox");
    bh.consume(id);
  }

  @Threads(1)
  @Benchmark
  public void emptyAppend4(Blackhole bh) {
    Id id = emptyId.withTags(
        "country", "US",
        "device", "xbox",
        "status", "200",
        "client", "ab");
    bh.consume(id);
  }

  @Threads(1)
  @Benchmark
  public void emptyAppend4sorted(Blackhole bh) {
    Id id = emptyId.withTags(
        "client", "ab",
        "country", "US",
        "device", "xbox",
        "status", "200");
    bh.consume(id);
  }

  public static class PrependId {
    public final String name;
    public final PrependTagList tags;

    public PrependId(String name, PrependTagList tags) {
      this.name = name;
      this.tags = tags;
    }

    public PrependId withTag(String k, String v) {
      return new PrependId(name, new PrependTagList(k, v, tags));
    }
  }

  public static class PrependTagList {
    public final String key;
    public final String value;
    public final PrependTagList next;

    public PrependTagList(String key, String value, PrependTagList next) {
      this.key = key;
      this.value = value;
      this.next = next;
    }
  }
}
