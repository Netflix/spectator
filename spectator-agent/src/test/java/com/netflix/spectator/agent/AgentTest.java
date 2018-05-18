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
package com.netflix.spectator.agent;

import com.typesafe.config.Config;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AgentTest {

  @Test
  public void loadConfigA() {
    Config config = Agent.loadConfig("a");
    Assert.assertEquals("a", config.getString("option"));
    Assert.assertTrue(config.getBoolean("a"));
  }

  @Test
  public void loadConfigAB() {
    Config config = Agent.loadConfig("a,b");
    Assert.assertEquals("b", config.getString("option"));
    Assert.assertTrue(config.getBoolean("a"));
    Assert.assertTrue(config.getBoolean("b"));
  }

  @Test
  public void loadConfigABC() {
    Config config = Agent.loadConfig("a\tb, \nc");
    Assert.assertEquals("c", config.getString("option"));
    Assert.assertTrue(config.getBoolean("a"));
    Assert.assertTrue(config.getBoolean("b"));
    Assert.assertTrue(config.getBoolean("c"));
  }
}
