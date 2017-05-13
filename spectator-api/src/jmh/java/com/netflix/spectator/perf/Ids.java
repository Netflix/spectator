/*
 * Copyright 2014-2017 Netflix, Inc.
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

import java.util.HashMap;
import java.util.Map;

/**
 * <pre>
 * Benchmark                         Mode  Cnt          Score         Error  Units
 * Ids.append1                      thrpt   10   24,832,015.561 ±  775914.547  ops/s
 * Ids.append2                      thrpt   10   15,115,152.153 ±  500615.433  ops/s
 * Ids.append4                      thrpt   10    5,082,342.853 ±  118696.678  ops/s
 * Ids.append4sorted                thrpt   10    6,243,473.482 ±  203665.016  ops/s
 * Ids.baseline                     thrpt   10   17,854,792.417 ±  663244.744  ops/s
 * Ids.justName                     thrpt   10  160,126,776.622 ± 8846354.045  ops/s
 * Ids.withTag                      thrpt   10    1,696,380.648 ±   35774.917  ops/s
 * Ids.withTagsMap                  thrpt   10    2,080,491.447 ±   35497.396  ops/s
 * Ids.withTagsVararg               thrpt   10    2,142,907.363 ±   53384.402  ops/s
 * Ids.withTagsVarargSorted         thrpt   10    4,234,550.555 ±  130956.068  ops/s
 * </pre>
 */
@State(Scope.Thread)
public class Ids {

  private final Registry registry = new DefaultRegistry();

  private final Map<String, String> tags = getTags();

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

  private final Id baseId = registry.createId("http.req.complete")
      .withTag(    "nf.app", "test_app")
      .withTag("nf.cluster", "test_app-main")
      .withTag(    "nf.asg", "test_app-main-v042")
      .withTag(  "nf.stack", "main")
      .withTag(    "nf.ami", "ami-0987654321")
      .withTag( "nf.region", "us-east-1")
      .withTag(   "nf.zone", "us-east-1e")
      .withTag(   "nf.node", "i-1234567890");

  @Threads(1)
  @Benchmark
  public void justName(Blackhole bh) {
    bh.consume(registry.createId("http.req.complete"));
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
