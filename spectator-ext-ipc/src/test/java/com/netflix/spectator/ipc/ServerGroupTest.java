/*
 * Copyright 2014-2018 Netflix, Inc.
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
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Random;

@RunWith(JUnit4.class)
public class ServerGroupTest {

  @Test
  public void parseApp() {
    String asg = "app";
    ServerGroup expected = new ServerGroup(asg, -1, -1, asg.length());
    Assert.assertEquals(expected, ServerGroup.parse(asg));
  }

  @Test
  public void getAppForApp() {
    String asg = "app";
    Assert.assertEquals("app", ServerGroup.parse(asg).app());
  }

  @Test
  public void getClusterForApp() {
    String asg = "app";
    Assert.assertEquals("app", ServerGroup.parse(asg).cluster());
  }

  @Test
  public void getAsgForApp() {
    String asg = "app";
    Assert.assertEquals("app", ServerGroup.parse(asg).asg());
  }

  @Test
  public void getStackForApp() {
    String asg = "app";
    Assert.assertNull(ServerGroup.parse(asg).stack());
  }

  @Test
  public void getDetailForApp() {
    String asg = "app";
    Assert.assertNull(ServerGroup.parse(asg).detail());
  }

  @Test
  public void parseAppStack() {
    String asg = "app-stack";
    ServerGroup expected = new ServerGroup(asg, 3, -1, asg.length());
    Assert.assertEquals(expected, ServerGroup.parse(asg));
  }

  @Test
  public void getAppForAppStack() {
    String asg = "app-stack";
    Assert.assertEquals("app", ServerGroup.parse(asg).app());
  }

  @Test
  public void getClusterForAppStack() {
    String asg = "app-stack";
    Assert.assertEquals("app-stack", ServerGroup.parse(asg).cluster());
  }

  @Test
  public void getAsgForAppStack() {
    String asg = "app-stack";
    Assert.assertEquals("app-stack", ServerGroup.parse(asg).asg());
  }

  @Test
  public void getStackForAppStack() {
    String asg = "app-stack";
    Assert.assertEquals("stack", ServerGroup.parse(asg).stack());
  }

  @Test
  public void getDetailForAppStack() {
    String asg = "app-stack";
    Assert.assertNull(ServerGroup.parse(asg).detail());
  }

  @Test
  public void parseAppStackDetail() {
    String asg = "app-stack-detail";
    ServerGroup expected = new ServerGroup(asg, 3, 9, asg.length());
    Assert.assertEquals(expected, ServerGroup.parse(asg));
  }

  @Test
  public void getAppForAppStackDetail() {
    String asg = "app-stack-detail";
    Assert.assertEquals("app", ServerGroup.parse(asg).app());
  }

  @Test
  public void getClusterForAppStackDetail() {
    String asg = "app-stack-detail";
    Assert.assertEquals("app-stack-detail", ServerGroup.parse(asg).cluster());
  }

  @Test
  public void getAsgForAppStackDetail() {
    String asg = "app-stack-detail";
    Assert.assertEquals("app-stack-detail", ServerGroup.parse(asg).asg());
  }

  @Test
  public void getStackForAppStackDetail() {
    String asg = "app-stack-detail";
    Assert.assertEquals("stack", ServerGroup.parse(asg).stack());
  }

  @Test
  public void getDetailForAppStackDetail() {
    String asg = "app-stack-detail";
    Assert.assertEquals("detail", ServerGroup.parse(asg).detail());
  }

  @Test
  public void parseAppStackDetails() {
    String asg = "app-stack-detail_1-detail_2";
    ServerGroup expected = new ServerGroup(asg, 3, 9, asg.length());
    Assert.assertEquals(expected, ServerGroup.parse(asg));
  }

  @Test
  public void getAppForAppStackDetails() {
    String asg = "app-stack-detail_1-detail_2";
    Assert.assertEquals("app", ServerGroup.parse(asg).app());
  }

  @Test
  public void getClusterForAppStackDetails() {
    String asg = "app-stack-detail_1-detail_2";
    Assert.assertEquals("app-stack-detail_1-detail_2", ServerGroup.parse(asg).cluster());
  }

  @Test
  public void getAsgForAppStackDetails() {
    String asg = "app-stack-detail_1-detail_2";
    Assert.assertEquals("app-stack-detail_1-detail_2", ServerGroup.parse(asg).asg());
  }

  @Test
  public void getStackForAppStackDetails() {
    String asg = "app-stack-detail_1-detail_2";
    Assert.assertEquals("stack", ServerGroup.parse(asg).stack());
  }

  @Test
  public void getDetailForAppStackDetails() {
    String asg = "app-stack-detail_1-detail_2";
    Assert.assertEquals("detail_1-detail_2", ServerGroup.parse(asg).detail());
  }

  @Test
  public void parseAppDetail() {
    String asg = "app--detail";
    ServerGroup expected = new ServerGroup(asg, 3, 4, asg.length());
    Assert.assertEquals(expected, ServerGroup.parse(asg));
  }

  @Test
  public void getAppForAppDetail() {
    String asg = "app--detail";
    Assert.assertEquals("app", ServerGroup.parse(asg).app());
  }

  @Test
  public void getClusterForAppDetail() {
    String asg = "app--detail";
    Assert.assertEquals("app--detail", ServerGroup.parse(asg).cluster());
  }

  @Test
  public void getAsgForAppDetail() {
    String asg = "app--detail";
    Assert.assertEquals("app--detail", ServerGroup.parse(asg).asg());
  }

  @Test
  public void getStackForAppDetail() {
    String asg = "app--detail";
    Assert.assertNull(ServerGroup.parse(asg).stack());
  }

  @Test
  public void getDetailForAppDetail() {
    String asg = "app--detail";
    Assert.assertEquals("detail", ServerGroup.parse(asg).detail());
  }

  @Test
  public void parseAppSeq() {
    String asg = "app-v001";
    ServerGroup expected = new ServerGroup(asg, 3, -1, asg.length() - 5);
    Assert.assertEquals(expected, ServerGroup.parse(asg));
  }

  @Test
  public void getAppForAppSeq() {
    String asg = "app-v001";
    Assert.assertEquals("app", ServerGroup.parse(asg).app());
  }

  @Test
  public void getClusterForAppSeq() {
    String asg = "app-v001";
    Assert.assertEquals("app", ServerGroup.parse(asg).cluster());
  }

  @Test
  public void getAsgForAppSeq() {
    String asg = "app-v001";
    Assert.assertEquals("app-v001", ServerGroup.parse(asg).asg());
  }

  @Test
  public void getStackForAppSeq() {
    String asg = "app-v001";
    Assert.assertNull(ServerGroup.parse(asg).stack());
  }

  @Test
  public void getDetailForAppSeq() {
    String asg = "app-v001";
    Assert.assertNull(ServerGroup.parse(asg).detail());
  }

  @Test
  public void parseAppStackSeq() {
    String asg = "app-stack-v001";
    ServerGroup expected = new ServerGroup(asg, 3, 9, asg.length() - 5);
    Assert.assertEquals(expected, ServerGroup.parse(asg));
  }

  @Test
  public void getAppForAppStackSeq() {
    String asg = "app-stack-v001";
    Assert.assertEquals("app", ServerGroup.parse(asg).app());
  }

  @Test
  public void getClusterForAppStackSeq() {
    String asg = "app-stack-v001";
    Assert.assertEquals("app-stack", ServerGroup.parse(asg).cluster());
  }

  @Test
  public void getAsgForAppStackSeq() {
    String asg = "app-stack-v001";
    Assert.assertEquals("app-stack-v001", ServerGroup.parse(asg).asg());
  }

  @Test
  public void getStackForAppStackSeq() {
    String asg = "app-stack-v001";
    Assert.assertEquals("stack", ServerGroup.parse(asg).stack());
  }

  @Test
  public void getDetailForAppStackSeq() {
    String asg = "app-stack-v001";
    Assert.assertNull(ServerGroup.parse(asg).detail());
  }

  @Test
  public void parseAppStackDetailSeq() {
    String asg = "app-stack-detail-v001";
    ServerGroup expected = new ServerGroup(asg, 3, 9, asg.length() - 5);
    Assert.assertEquals(expected, ServerGroup.parse(asg));
  }

  @Test
  public void getAppForAppStackDetailSeq() {
    String asg = "app-stack-detail-v001";
    Assert.assertEquals("app", ServerGroup.parse(asg).app());
  }

  @Test
  public void getClusterForAppStackDetailSeq() {
    String asg = "app-stack-detail-v001";
    Assert.assertEquals("app-stack-detail", ServerGroup.parse(asg).cluster());
  }

  @Test
  public void getAsgForAppStackDetailSeq() {
    String asg = "app-stack-detail-v001";
    Assert.assertEquals("app-stack-detail-v001", ServerGroup.parse(asg).asg());
  }

  @Test
  public void getStackForAppStackDetailSeq() {
    String asg = "app-stack-detail-v001";
    Assert.assertEquals("stack", ServerGroup.parse(asg).stack());
  }

  @Test
  public void getDetailForAppStackDetailSeq() {
    String asg = "app-stack-detail-v001";
    Assert.assertEquals("detail", ServerGroup.parse(asg).detail());
  }

  @Test
  public void parseAppStackDetailsSeq() {
    String asg = "app-stack-detail_1-detail_2-v001";
    ServerGroup expected = new ServerGroup(asg, 3, 9, asg.length() - 5);
    Assert.assertEquals(expected, ServerGroup.parse(asg));
  }

  @Test
  public void getAppForAppStackDetailsSeq() {
    String asg = "app-stack-detail_1-detail_2-v001";
    Assert.assertEquals("app", ServerGroup.parse(asg).app());
  }

  @Test
  public void getClusterForAppStackDetailsSeq() {
    String asg = "app-stack-detail_1-detail_2-v001";
    Assert.assertEquals("app-stack-detail_1-detail_2", ServerGroup.parse(asg).cluster());
  }

  @Test
  public void getAsgForAppStackDetailsSeq() {
    String asg = "app-stack-detail_1-detail_2-v001";
    Assert.assertEquals("app-stack-detail_1-detail_2-v001", ServerGroup.parse(asg).asg());
  }

  @Test
  public void getStackForAppStackDetailsSeq() {
    String asg = "app-stack-detail_1-detail_2-v001";
    Assert.assertEquals("stack", ServerGroup.parse(asg).stack());
  }

  @Test
  public void getDetailForAppStackDetailsSeq() {
    String asg = "app-stack-detail_1-detail_2-v001";
    Assert.assertEquals("detail_1-detail_2", ServerGroup.parse(asg).detail());
  }

  @Test
  public void parseAppDetailSeq() {
    String asg = "app--detail-v001";
    ServerGroup expected = new ServerGroup(asg, 3, 4, asg.length() - 5);
    Assert.assertEquals(expected, ServerGroup.parse(asg));
  }

  @Test
  public void getAppForAppDetailSeq() {
    String asg = "app--detail-v001";
    Assert.assertEquals("app", ServerGroup.parse(asg).app());
  }

  @Test
  public void getClusterForAppDetailSeq() {
    String asg = "app--detail-v001";
    Assert.assertEquals("app--detail", ServerGroup.parse(asg).cluster());
  }

  @Test
  public void getAsgForAppDetailSeq() {
    String asg = "app--detail-v001";
    Assert.assertEquals("app--detail-v001", ServerGroup.parse(asg).asg());
  }

  @Test
  public void getStackForAppDetailSeq() {
    String asg = "app--detail-v001";
    Assert.assertNull(ServerGroup.parse(asg).stack());
  }

  @Test
  public void getDetailForAppDetailSeq() {
    String asg = "app--detail-v001";
    Assert.assertEquals("detail", ServerGroup.parse(asg).detail());
  }

  @Test
  public void parseEmptyApp() {
    String asg = "-v1698";
    ServerGroup expected = new ServerGroup(asg, 0, -1, 0);
    Assert.assertEquals(expected, ServerGroup.parse(asg));
  }

  @Test
  public void getAppEmptyApp() {
    String asg = "-v1698";
    Assert.assertNull(ServerGroup.parse(asg).app());
  }

  @Test
  public void getClusterEmptyApp() {
    String asg = "-v1698";
    Assert.assertNull(ServerGroup.parse(asg).cluster());
  }

  @Test
  public void parseEmptyString() {
    String asg = "";
    ServerGroup expected = new ServerGroup(asg, -1, -1, 0);
    Assert.assertEquals(expected, ServerGroup.parse(asg));
  }

  @Test
  public void getAppForEmptyString() {
    String asg = "";
    Assert.assertNull(ServerGroup.parse(asg).app());
  }

  @Test
  public void getClusterForEmptyString() {
    String asg = "";
    Assert.assertNull(ServerGroup.parse(asg).cluster());
  }

  @Test
  public void getAsgForEmptyString() {
    String asg = "";
    Assert.assertNull(ServerGroup.parse(asg).asg());
  }

  @Test
  public void getStackForEmptyString() {
    String asg = "";
    Assert.assertNull(ServerGroup.parse(asg).stack());
  }

  @Test
  public void getDetailForEmptyString() {
    String asg = "";
    Assert.assertNull(ServerGroup.parse(asg).detail());
  }

  private void appendRandomString(Random r, StringBuilder builder) {
    int length = r.nextInt(20);
    for (int i = 0; i < length; ++i) {
      char c = (char) (r.nextInt(26) + 'a');
      builder.append(c);
    }
  }

  private String randomServerGroup(Random r) {
    StringBuilder builder = new StringBuilder();
    int parts = r.nextInt(6) + 1;
    for (int i = 0; i < parts; ++i) {
      if (r.nextBoolean()) {
        appendRandomString(r, builder);
      } else {
        builder.append('v');
        builder.append(r.nextInt(10000));
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
    Random r = new Random(42);
    for (int i = 0; i < 5000; ++i) {
      String asg = randomServerGroup(r);
      ServerGroup sg = ServerGroup.parse(asg);
      Names frigga = Names.parseName(asg);
      Assert.assertEquals("app: " + asg, frigga.getApp(), sg.app());
      Assert.assertEquals("cluster: " + asg, frigga.getCluster(), sg.cluster());
      Assert.assertEquals("asg: " + asg, frigga.getGroup(), sg.asg());
      Assert.assertEquals("stack: " + asg, frigga.getStack(), sg.stack());
      Assert.assertEquals("detail: " + asg, frigga.getDetail(), sg.detail());
    }
  }
}
