/*
 * Copyright 2014-2016 Netflix, Inc.
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

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Spectator;
import com.netflix.spectator.atlas.AtlasConfig;
import com.netflix.spectator.atlas.AtlasRegistry;
import com.netflix.spectator.gc.GcLogger;
import com.netflix.spectator.jvm.Jmx;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.lang.instrument.Instrumentation;
import java.util.HashMap;
import java.util.Map;

/**
 * Agent that can be added to JVM to get basic stats about GC, memory, and optionally
 * JMX.
 */
public final class Agent {

  private Agent() {
  }

  private static Config loadConfig(String userResources) {
    Config config = ConfigFactory.load("agent");
    if (userResources != null && !"".equals(userResources)) {
      for (String userResource : userResources.split("[,\\s]+]")) {
        Config user = ConfigFactory.load(userResource);
        config = user.withFallback(config);
      }
    }
    return config.getConfig("netflix.spectator.agent");
  }

  /** Entry point for the agent. */
  public static void premain(String arg, Instrumentation instrumentation) throws Exception {
    // Setup logging
    Config config = loadConfig(arg);

    // Setup Registry
    AtlasRegistry registry = new AtlasRegistry(Clock.SYSTEM, new AgentAtlasConfig(config));

    // Add to global registry for http stats and GC logger
    Spectator.globalRegistry().add(registry);

    // Enable GC logger
    GcLogger gcLogger = new GcLogger();
    if (config.getBoolean("collection.gc")) {
      gcLogger.start(null);
    }

    // Enable JVM data collection
    if (config.getBoolean("collection.jvm")) {
      Jmx.registerStandardMXBeans(registry);
    }

    // Enable JMX query collection
    if (config.getBoolean("collection.jmx")) {
      for (Config cfg : config.getConfigList("jmx.mappings")) {
        registry.register(new JmxMeter(registry, JmxConfig.from(cfg)));
      }
    }

    // Start collection for the registry
    registry.start();

    // Shutdown registry
    Runtime.getRuntime().addShutdownHook(new Thread(registry::stop, "spectator-agent-shutdown"));
  }

  private static class AgentAtlasConfig implements AtlasConfig {

    private final Config config;

    AgentAtlasConfig(Config config) {
      this.config = config;
    }

    @Override public String get(String k) {
      return config.hasPath(k) ? config.getString(k) : null;
    }

    @Override public Map<String, String> commonTags() {
      Map<String, String> tags = new HashMap<>();
      for (Config cfg : config.getConfigList("atlas.tags")) {
        tags.put(cfg.getString("key"), cfg.getString("value"));
      }
      return tags;
    }
  }
}
