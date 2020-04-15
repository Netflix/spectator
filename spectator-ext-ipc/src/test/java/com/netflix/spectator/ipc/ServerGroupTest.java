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
package com.netflix.spectator.ipc;

import com.netflix.frigga.Names;
import com.netflix.frigga.conventions.sharding.Shard;
import com.netflix.frigga.conventions.sharding.ShardingNamingConvention;
import com.netflix.frigga.conventions.sharding.ShardingNamingResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Random;
import java.util.function.BiFunction;

public class ServerGroupTest {

  @Test
  public void parseApp() {
    String asg = "app";
    ServerGroup expected = new ServerGroup(asg, -1, -1, asg.length());
    Assertions.assertEquals(expected, ServerGroup.parse(asg));
  }

  @Test
  public void getAppForApp() {
    String asg = "app";
    Assertions.assertEquals("app", ServerGroup.parse(asg).app());
  }

  @Test
  public void getClusterForApp() {
    String asg = "app";
    Assertions.assertEquals("app", ServerGroup.parse(asg).cluster());
  }

  @Test
  public void getAsgForApp() {
    String asg = "app";
    Assertions.assertEquals("app", ServerGroup.parse(asg).asg());
  }

  @Test
  public void getStackForApp() {
    String asg = "app";
    Assertions.assertNull(ServerGroup.parse(asg).stack());
  }

  @Test
  public void getDetailForApp() {
    String asg = "app";
    Assertions.assertNull(ServerGroup.parse(asg).detail());
  }

  @Test
  public void getShardsForApp() {
    String asg = "app";
    Assertions.assertNull(ServerGroup.parse(asg).shard1());
    Assertions.assertNull(ServerGroup.parse(asg).shard2());
  }

  @Test
  public void getSequenceForApp() {
    String asg = "app";
    Assertions.assertNull(ServerGroup.parse(asg).sequence());
  }

  @Test
  public void parseAppStack() {
    String asg = "app-stack";
    ServerGroup expected = new ServerGroup(asg, 3, -1, asg.length());
    Assertions.assertEquals(expected, ServerGroup.parse(asg));
  }

  @Test
  public void getAppForAppStack() {
    String asg = "app-stack";
    Assertions.assertEquals("app", ServerGroup.parse(asg).app());
  }

  @Test
  public void getClusterForAppStack() {
    String asg = "app-stack";
    Assertions.assertEquals("app-stack", ServerGroup.parse(asg).cluster());
  }

  @Test
  public void getAsgForAppStack() {
    String asg = "app-stack";
    Assertions.assertEquals("app-stack", ServerGroup.parse(asg).asg());
  }

  @Test
  public void getStackForAppStack() {
    String asg = "app-stack";
    Assertions.assertEquals("stack", ServerGroup.parse(asg).stack());
  }

  @Test
  public void getDetailForAppStack() {
    String asg = "app-stack";
    Assertions.assertNull(ServerGroup.parse(asg).detail());
  }

  @Test
  public void getShardsForAppStack() {
    String asg = "app-stack";
    Assertions.assertNull(ServerGroup.parse(asg).shard1());
    Assertions.assertNull(ServerGroup.parse(asg).shard2());
  }

  @Test
  public void getSequenceForAppStack() {
    String asg = "app-stack";
    Assertions.assertNull(ServerGroup.parse(asg).sequence());
  }

  @Test
  public void parseAppStackDetail() {
    String asg = "app-stack-detail";
    ServerGroup expected = new ServerGroup(asg, 3, 9, asg.length());
    Assertions.assertEquals(expected, ServerGroup.parse(asg));
  }

  @Test
  public void getAppForAppStackDetail() {
    String asg = "app-stack-detail";
    Assertions.assertEquals("app", ServerGroup.parse(asg).app());
  }

  @Test
  public void getClusterForAppStackDetail() {
    String asg = "app-stack-detail";
    Assertions.assertEquals("app-stack-detail", ServerGroup.parse(asg).cluster());
  }

  @Test
  public void getAsgForAppStackDetail() {
    String asg = "app-stack-detail";
    Assertions.assertEquals("app-stack-detail", ServerGroup.parse(asg).asg());
  }

  @Test
  public void getStackForAppStackDetail() {
    String asg = "app-stack-detail";
    Assertions.assertEquals("stack", ServerGroup.parse(asg).stack());
  }

  @Test
  public void getDetailForAppStackDetail() {
    String asg = "app-stack-detail";
    Assertions.assertEquals("detail", ServerGroup.parse(asg).detail());
  }

  @Test
  public void getShardsForAppStackDetail() {
    String asg = "app-stack-detail";
    Assertions.assertNull(ServerGroup.parse(asg).shard1());
    Assertions.assertNull(ServerGroup.parse(asg).shard2());
  }

  @Test
  public void getSequenceForAppStackDetail() {
    String asg = "app-stack-detail";
    Assertions.assertNull(ServerGroup.parse(asg).sequence());
  }

  @Test
  public void parseAppStackDetails() {
    String asg = "app-stack-detail_1-detail_2";
    ServerGroup expected = new ServerGroup(asg, 3, 9, asg.length());
    Assertions.assertEquals(expected, ServerGroup.parse(asg));
  }

  @Test
  public void getAppForAppStackDetails() {
    String asg = "app-stack-detail_1-detail_2";
    Assertions.assertEquals("app", ServerGroup.parse(asg).app());
  }

  @Test
  public void getClusterForAppStackDetails() {
    String asg = "app-stack-detail_1-detail_2";
    Assertions.assertEquals("app-stack-detail_1-detail_2", ServerGroup.parse(asg).cluster());
  }

  @Test
  public void getAsgForAppStackDetails() {
    String asg = "app-stack-detail_1-detail_2";
    Assertions.assertEquals("app-stack-detail_1-detail_2", ServerGroup.parse(asg).asg());
  }

  @Test
  public void getStackForAppStackDetails() {
    String asg = "app-stack-detail_1-detail_2";
    Assertions.assertEquals("stack", ServerGroup.parse(asg).stack());
  }

  @Test
  public void getDetailForAppStackDetails() {
    String asg = "app-stack-detail_1-detail_2";
    Assertions.assertEquals("detail_1-detail_2", ServerGroup.parse(asg).detail());
  }

  @Test
  public void getShardsForAppStackDetails() {
    String asg = "app-stack-detail_1-detail_2";
    Assertions.assertNull(ServerGroup.parse(asg).shard1());
    Assertions.assertNull(ServerGroup.parse(asg).shard2());
  }

  @Test
  public void getSequenceForAppStackDetails() {
    String asg = "app-stack-detail_1-detail_2";
    Assertions.assertNull(ServerGroup.parse(asg).sequence());
  }

  @Test
  public void parseAppDetail() {
    String asg = "app--detail";
    ServerGroup expected = new ServerGroup(asg, 3, 4, asg.length());
    Assertions.assertEquals(expected, ServerGroup.parse(asg));
  }

  @Test
  public void getAppForAppDetail() {
    String asg = "app--detail";
    Assertions.assertEquals("app", ServerGroup.parse(asg).app());
  }

  @Test
  public void getClusterForAppDetail() {
    String asg = "app--detail";
    Assertions.assertEquals("app--detail", ServerGroup.parse(asg).cluster());
  }

  @Test
  public void getAsgForAppDetail() {
    String asg = "app--detail";
    Assertions.assertEquals("app--detail", ServerGroup.parse(asg).asg());
  }

  @Test
  public void getStackForAppDetail() {
    String asg = "app--detail";
    Assertions.assertNull(ServerGroup.parse(asg).stack());
  }

  @Test
  public void getDetailForAppDetail() {
    String asg = "app--detail";
    Assertions.assertEquals("detail", ServerGroup.parse(asg).detail());
  }

  @Test
  public void getShardsForAppDetail() {
    String asg = "app--detail";
    Assertions.assertNull(ServerGroup.parse(asg).shard1());
    Assertions.assertNull(ServerGroup.parse(asg).shard2());
  }

  @Test
  public void getSequenceForAppDetail() {
    String asg = "app--detail";
    Assertions.assertNull(ServerGroup.parse(asg).sequence());
  }

  @Test
  public void parseAppSeq() {
    String asg = "app-v001";
    ServerGroup expected = new ServerGroup(asg, 3, -1, asg.length() - 5);
    Assertions.assertEquals(expected, ServerGroup.parse(asg));
  }

  @Test
  public void getAppForAppSeq() {
    String asg = "app-v001";
    Assertions.assertEquals("app", ServerGroup.parse(asg).app());
  }

  @Test
  public void getClusterForAppSeq() {
    String asg = "app-v001";
    Assertions.assertEquals("app", ServerGroup.parse(asg).cluster());
  }

  @Test
  public void getAsgForAppSeq() {
    String asg = "app-v001";
    Assertions.assertEquals("app-v001", ServerGroup.parse(asg).asg());
  }

  @Test
  public void getStackForAppSeq() {
    String asg = "app-v001";
    Assertions.assertNull(ServerGroup.parse(asg).stack());
  }

  @Test
  public void getDetailForAppSeq() {
    String asg = "app-v001";
    Assertions.assertNull(ServerGroup.parse(asg).detail());
  }

  @Test
  public void getShardsForAppSeq() {
    String asg = "app-v001";
    Assertions.assertNull(ServerGroup.parse(asg).shard1());
    Assertions.assertNull(ServerGroup.parse(asg).shard2());
  }

  @Test
  public void getSequenceForAppSeq() {
    String asg = "app-v001";
    Assertions.assertEquals("v001", ServerGroup.parse(asg).sequence());
  }

  @Test
  public void parseAppStackSeq() {
    String asg = "app-stack-v001";
    ServerGroup expected = new ServerGroup(asg, 3, 9, asg.length() - 5);
    Assertions.assertEquals(expected, ServerGroup.parse(asg));
  }

  @Test
  public void getAppForAppStackSeq() {
    String asg = "app-stack-v001";
    Assertions.assertEquals("app", ServerGroup.parse(asg).app());
  }

  @Test
  public void getClusterForAppStackSeq() {
    String asg = "app-stack-v001";
    Assertions.assertEquals("app-stack", ServerGroup.parse(asg).cluster());
  }

  @Test
  public void getAsgForAppStackSeq() {
    String asg = "app-stack-v001";
    Assertions.assertEquals("app-stack-v001", ServerGroup.parse(asg).asg());
  }

  @Test
  public void getStackForAppStackSeq() {
    String asg = "app-stack-v001";
    Assertions.assertEquals("stack", ServerGroup.parse(asg).stack());
  }

  @Test
  public void getDetailForAppStackSeq() {
    String asg = "app-stack-v001";
    Assertions.assertNull(ServerGroup.parse(asg).detail());
  }

  @Test
  public void getShardsForAppStackSeq() {
    String asg = "app-stack-v001";
    Assertions.assertNull(ServerGroup.parse(asg).shard1());
    Assertions.assertNull(ServerGroup.parse(asg).shard2());
  }

  @Test
  public void getSequenceForAppStackSeq() {
    String asg = "app-stack-v001";
    Assertions.assertEquals("v001", ServerGroup.parse(asg).sequence());
  }

  @Test
  public void parseAppStackDetailSeq() {
    String asg = "app-stack-detail-v001";
    ServerGroup expected = new ServerGroup(asg, 3, 9, asg.length() - 5);
    Assertions.assertEquals(expected, ServerGroup.parse(asg));
  }

  @Test
  public void getAppForAppStackDetailSeq() {
    String asg = "app-stack-detail-v001";
    Assertions.assertEquals("app", ServerGroup.parse(asg).app());
  }

  @Test
  public void getClusterForAppStackDetailSeq() {
    String asg = "app-stack-detail-v001";
    Assertions.assertEquals("app-stack-detail", ServerGroup.parse(asg).cluster());
  }

  @Test
  public void getAsgForAppStackDetailSeq() {
    String asg = "app-stack-detail-v001";
    Assertions.assertEquals("app-stack-detail-v001", ServerGroup.parse(asg).asg());
  }

  @Test
  public void getStackForAppStackDetailSeq() {
    String asg = "app-stack-detail-v001";
    Assertions.assertEquals("stack", ServerGroup.parse(asg).stack());
  }

  @Test
  public void getDetailForAppStackDetailSeq() {
    String asg = "app-stack-detail-v001";
    Assertions.assertEquals("detail", ServerGroup.parse(asg).detail());
  }

  @Test
  public void getShardsForAppStackDetailSeq() {
    String asg = "app-stack-detail-v001";
    Assertions.assertNull(ServerGroup.parse(asg).shard1());
    Assertions.assertNull(ServerGroup.parse(asg).shard2());
  }

  @Test
  public void getSequenceForAppStackDetailSeq() {
    String asg = "app-stack-detail-v001";
    Assertions.assertEquals("v001", ServerGroup.parse(asg).sequence());
  }

  @Test
  public void parseAppStackDetailsSeq() {
    String asg = "app-stack-detail_1-detail_2-v001";
    ServerGroup expected = new ServerGroup(asg, 3, 9, asg.length() - 5);
    Assertions.assertEquals(expected, ServerGroup.parse(asg));
  }

  @Test
  public void getAppForAppStackDetailsSeq() {
    String asg = "app-stack-detail_1-detail_2-v001";
    Assertions.assertEquals("app", ServerGroup.parse(asg).app());
  }

  @Test
  public void getClusterForAppStackDetailsSeq() {
    String asg = "app-stack-detail_1-detail_2-v001";
    Assertions.assertEquals("app-stack-detail_1-detail_2", ServerGroup.parse(asg).cluster());
  }

  @Test
  public void getAsgForAppStackDetailsSeq() {
    String asg = "app-stack-detail_1-detail_2-v001";
    Assertions.assertEquals("app-stack-detail_1-detail_2-v001", ServerGroup.parse(asg).asg());
  }

  @Test
  public void getStackForAppStackDetailsSeq() {
    String asg = "app-stack-detail_1-detail_2-v001";
    Assertions.assertEquals("stack", ServerGroup.parse(asg).stack());
  }

  @Test
  public void getDetailForAppStackDetailsSeq() {
    String asg = "app-stack-detail_1-detail_2-v001";
    Assertions.assertEquals("detail_1-detail_2", ServerGroup.parse(asg).detail());
  }

  @Test
  public void getShardsForAppStackDetailsSeq() {
    String asg = "app-stack-detail_1-detail_2-v001";
    Assertions.assertNull(ServerGroup.parse(asg).shard1());
    Assertions.assertNull(ServerGroup.parse(asg).shard2());
  }

  @Test
  public void getSequenceForAppStackDetailsSeq() {
    String asg = "app-stack-detail_1-detail_2-v001";
    Assertions.assertEquals("v001", ServerGroup.parse(asg).sequence());
  }

  @Test
  public void parseAppDetailSeq() {
    String asg = "app--detail-v001";
    ServerGroup expected = new ServerGroup(asg, 3, 4, asg.length() - 5);
    Assertions.assertEquals(expected, ServerGroup.parse(asg));
  }

  @Test
  public void getAppForAppDetailSeq() {
    String asg = "app--detail-v001";
    Assertions.assertEquals("app", ServerGroup.parse(asg).app());
  }

  @Test
  public void getClusterForAppDetailSeq() {
    String asg = "app--detail-v001";
    Assertions.assertEquals("app--detail", ServerGroup.parse(asg).cluster());
  }

  @Test
  public void getAsgForAppDetailSeq() {
    String asg = "app--detail-v001";
    Assertions.assertEquals("app--detail-v001", ServerGroup.parse(asg).asg());
  }

  @Test
  public void getStackForAppDetailSeq() {
    String asg = "app--detail-v001";
    Assertions.assertNull(ServerGroup.parse(asg).stack());
  }

  @Test
  public void getDetailForAppDetailSeq() {
    String asg = "app--detail-v001";
    Assertions.assertEquals("detail", ServerGroup.parse(asg).detail());
  }

  @Test
  public void getShardsForAppDetailSeq() {
    String asg = "app--detail-v001";
    Assertions.assertNull(ServerGroup.parse(asg).shard1());
    Assertions.assertNull(ServerGroup.parse(asg).shard2());
  }

  @Test
  public void getSequenceForAppDetailSeq() {
    String asg = "app--detail-v001";
    Assertions.assertEquals("v001", ServerGroup.parse(asg).sequence());
  }

  @Test
  public void parseEmptyApp() {
    String asg = "-v1698";
    ServerGroup expected = new ServerGroup(asg, 0, -1, 0);
    Assertions.assertEquals(expected, ServerGroup.parse(asg));
  }

  @Test
  public void getAppEmptyApp() {
    String asg = "-v1698";
    Assertions.assertNull(ServerGroup.parse(asg).app());
  }

  @Test
  public void getClusterEmptyApp() {
    String asg = "-v1698";
    Assertions.assertNull(ServerGroup.parse(asg).cluster());
  }

  @Test
  public void parseEmptyString() {
    String asg = "";
    ServerGroup expected = new ServerGroup(asg, -1, -1, 0);
    Assertions.assertEquals(expected, ServerGroup.parse(asg));
  }

  @Test
  public void getAppForEmptyString() {
    String asg = "";
    Assertions.assertNull(ServerGroup.parse(asg).app());
  }

  @Test
  public void getClusterForEmptyString() {
    String asg = "";
    Assertions.assertNull(ServerGroup.parse(asg).cluster());
  }

  @Test
  public void getAsgForEmptyString() {
    String asg = "";
    Assertions.assertNull(ServerGroup.parse(asg).asg());
  }

  @Test
  public void getStackForEmptyString() {
    String asg = "";
    Assertions.assertNull(ServerGroup.parse(asg).stack());
  }

  @Test
  public void getDetailForEmptyString() {
    String asg = "";
    Assertions.assertNull(ServerGroup.parse(asg).detail());
  }

  @Test
  public void getShardsForEmptyString() {
    String asg = "";
    Assertions.assertNull(ServerGroup.parse(asg).shard1());
    Assertions.assertNull(ServerGroup.parse(asg).shard2());
  }

  @Test
  public void getSequenceForEmptyString() {
    String asg = "";
    Assertions.assertNull(ServerGroup.parse(asg).sequence());
  }

  @Test
  public void getShardsOnlyShard1() {
    String asg = "app-stack-x1shard1-detail-v000";
    Assertions.assertEquals("shard1", ServerGroup.parse(asg).shard1());
    Assertions.assertNull(ServerGroup.parse(asg).shard2());
  }

  @Test
  public void getShardsOnlyShard2() {
    String asg = "app-stack-x2shard2-detail-v000";
    Assertions.assertNull(ServerGroup.parse(asg).shard1());
    Assertions.assertEquals("shard2", ServerGroup.parse(asg).shard2());
  }

  @Test
  public void getShardsBoth() {
    String asg = "app-stack-x1shard1-x2shard2-detail-v000";
    Assertions.assertEquals("shard1", ServerGroup.parse(asg).shard1());
    Assertions.assertEquals("shard2", ServerGroup.parse(asg).shard2());
  }

  @Test
  public void getShardsThree() {
    String asg = "app-stack-x1shard1-x2shard2-x3shard3-detail-v000";
    Assertions.assertEquals("shard1", ServerGroup.parse(asg).shard1());
    Assertions.assertEquals("shard2", ServerGroup.parse(asg).shard2());
  }

  @Test
  public void getShardsOutOfOrder() {
    String asg = "app-stack-x2shard2-x1shard1-detail-v000";
    Assertions.assertEquals("shard1", ServerGroup.parse(asg).shard1());
    Assertions.assertEquals("shard2", ServerGroup.parse(asg).shard2());
  }

  @Test
  public void getShardsLeadingDigits() {
    String asg = "app-stack-x10shard1-detail-v000";
    Assertions.assertNull(ServerGroup.parse(asg).shard1());
    Assertions.assertNull(ServerGroup.parse(asg).shard2());
  }

  @Test
  public void getShardsGap() {
    String asg = "app-stack-x1shard1-foo-x2shard2-detail-v000";
    Assertions.assertEquals("shard1", ServerGroup.parse(asg).shard1());
    Assertions.assertNull(ServerGroup.parse(asg).shard2());
  }

  @Test
  public void getShardsEndOfDetail() {
    String asg = "app-stack-detail-x1shard1-x2shard2-v000";
    Assertions.assertNull(ServerGroup.parse(asg).shard1());
    Assertions.assertNull(ServerGroup.parse(asg).shard2());
  }

  @Test
  public void getShardsDuplicate() {
    String asg = "app-stack-x1a1-x2a2-x1b1-x2b2-v000";
    Assertions.assertEquals("b1", ServerGroup.parse(asg).shard1());
    Assertions.assertEquals("b2", ServerGroup.parse(asg).shard2());
  }

  @Test
  public void getShardsEmpty() {
    String asg = "app-stack-x1-x2-v000";
    Assertions.assertNull(ServerGroup.parse(asg).shard1());
    Assertions.assertNull(ServerGroup.parse(asg).shard2());
  }

  private void appendRandomString(Random r, StringBuilder builder) {
    int length = r.nextInt(20) + 1;
    for (int i = 0; i < length; ++i) {
      char c = (char) (r.nextInt(26) + 'a');
      builder.append(c);
    }
  }

  private void appendRandomPart(Random r, StringBuilder builder) {
    // https://github.com/cfieber/frigga/blob/master/src/main/java/com/netflix/frigga/NameConstants.java#L29
    String[] prefixes = {"x0", "x1", "x2", "x3", "c0", "d0", "h0", "p0", "r0", "u0", "w0", "z0"};
    if (r.nextBoolean()) {
      builder.append(prefixes[r.nextInt(prefixes.length)]);
    }
    appendRandomString(r, builder);
  }

  private String randomServerGroup(Random r) {
    StringBuilder builder = new StringBuilder();
    int parts = r.nextInt(6) + 1;
    for (int i = 0; i < parts; ++i) {
      if (r.nextBoolean()) {
        appendRandomPart(r, builder);
      } else {
        builder.append('v');
        builder.append(r.nextInt(10_000_000));
      }
      if (i != parts - 1) {
        builder.append('-');
      }
    }
    return builder.toString();
  }

  @Test
  public void compatibleWithFrigga() {
    // Seed the RNG so that each run is deterministic. In this case we are just using it to
    // generate a bunch of patterns to try
    ShardingNamingConvention convention = new ShardingNamingConvention();
    BiFunction<Map<Integer, Shard>, Integer, String> getShard = (shards, i) -> {
      Shard s = shards.get(i);
      return s == null ? null : s.getShardValue();
    };
    Random r = new Random(42);
    for (int i = 0; i < 50_000; ++i) {
      String asg = randomServerGroup(r);
      ServerGroup sg = ServerGroup.parse(asg);
      Names frigga = Names.parseName(asg);
      Assertions.assertEquals(frigga.getApp(), sg.app(), "app: " + asg);
      Assertions.assertEquals(frigga.getCluster(), sg.cluster(), "cluster: " + asg);
      Assertions.assertEquals(frigga.getGroup(), sg.asg(), "asg: " + asg);
      Assertions.assertEquals(frigga.getStack(), sg.stack(), "stack: " + asg);
      Assertions.assertEquals(frigga.getDetail(), sg.detail(), "detail: " + asg);

      if (frigga.getDetail() == null) {
        continue;
      }

      try {
        ShardingNamingResult result = convention.extractNamingConvention(frigga.getDetail());
        if (result.getResult().isPresent()) {
          Map<Integer, Shard> shards = result.getResult().get();
          Assertions.assertEquals(getShard.apply(shards, 1), sg.shard1(), "shard1: " + asg);
          Assertions.assertEquals(getShard.apply(shards, 2), sg.shard2(), "shard2: " + asg);
        } else {
          Assertions.assertNull(sg.shard1(), "shard1: " + asg);
          Assertions.assertNull(sg.shard2(), "shard2: " + asg);
        }
      } catch (Exception e) {
        throw new RuntimeException("parsing shards failed: " + asg, e);
      }
    }
  }
}
