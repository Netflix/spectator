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
package com.netflix.spectator.agent;

import com.typesafe.config.Config;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AgentTest {

  @Test
  public void loadConfigA() {
    Config config = Agent.loadConfig(Agent.parseResourceList("a"));
    Assertions.assertEquals("a", config.getString("option"));
    Assertions.assertTrue(config.getBoolean("a"));
  }

  @Test
  public void loadConfigAB() {
    Config config = Agent.loadConfig(Agent.parseResourceList("a,b"));
    Assertions.assertEquals("b", config.getString("option"));
    Assertions.assertTrue(config.getBoolean("a"));
    Assertions.assertTrue(config.getBoolean("b"));
  }

  @Test
  public void loadConfigABC() {
    Config config = Agent.loadConfig(Agent.parseResourceList("a\tb, \nc"));
    Assertions.assertEquals("c", config.getString("option"));
    Assertions.assertTrue(config.getBoolean("a"));
    Assertions.assertTrue(config.getBoolean("b"));
    Assertions.assertTrue(config.getBoolean("c"));
  }

  @Test
  public void loadConfigListsAppend() {
    Config config = Agent.loadConfig(Agent.parseResourceList("a,b,c"));
    List<String> items = config.getStringList("list");
    Collections.sort(items);

    List<String> expected = new ArrayList<>();
    expected.add("a");
    expected.add("b");
    expected.add("c");

    Assertions.assertEquals(expected, items);
  }
}
