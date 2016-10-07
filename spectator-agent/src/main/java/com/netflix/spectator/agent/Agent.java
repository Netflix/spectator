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

/**
 * Agent that can be added to JVM to get basic stats about GC, memory, and optionally
 * JMX.
 */
public final class Agent {

  private Agent() {
  }

  private static Config loadConfig(String userResource) {
    Config config = ConfigFactory.load("agent");
    if (userResource != null && !"".equals(userResource)) {
      Config user = ConfigFactory.load(userResource);
      config = user.withFallback(config);
    }
    return config.getConfig("netflix.spectator.agent");
  }

  /** Entry point for the agent. */
  public static void premain(String arg, Instrumentation instrumentation) {
    // Setup logging
    Config config = loadConfig(arg);

    // Setup Registry
    AtlasConfig atlasConfig = k -> config.hasPath(k) ? config.getString(k) : null;
    AtlasRegistry registry = new AtlasRegistry(Clock.SYSTEM, atlasConfig);

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

    // Start collection for the registry
    registry.start();

    // Shutdown registry
    Runtime.getRuntime().addShutdownHook(new Thread(registry::stop, "spectator-agent-shutdown"));
  }
}
