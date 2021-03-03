/*
 * Copyright 2014-2021 Netflix, Inc.
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

import com.netflix.iep.NetflixEnvironment;
import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Spectator;
import com.netflix.spectator.atlas.AtlasConfig;
import com.netflix.spectator.atlas.AtlasRegistry;
import com.netflix.spectator.gc.GcLogger;
import com.netflix.spectator.jvm.Jmx;
import com.netflix.spectator.jvm.JmxPoller;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Agent that can be added to JVM to get basic stats about GC, memory, and optionally
 * JMX.
 */
public final class Agent {

  private static final Logger LOGGER = LoggerFactory.getLogger(Agent.class);

  private Agent() {
  }

  /** Parse the set of user resources. */
  static List<Object> parseResourceList(String userResources) {
    List<Object> resources = new ArrayList<>();
    if (userResources != null && !"".equals(userResources)) {
      for (String userResource : userResources.split("[,\\s]+")) {
        if (userResource.startsWith("file:")) {
          File file = new File(userResource.substring("file:".length()));
          resources.add(file);
        } else {
          resources.add(userResource);
        }
      }
    }
    return resources;
  }

  /** Check for changes to the configs on the file system. */
  static List<File> findUpdatedConfigs(List<Object> resources, long timestamp) {
    return resources.stream()
        .filter(r -> r instanceof File && ((File) r).lastModified() > timestamp)
        .map(r -> (File) r)
        .collect(Collectors.toList());
  }

  /** Helper to load config files specified by the user. */
  static Config loadConfig(List<Object> resources) {
    Config config = ConfigFactory.load("agent");
    for (Object r : resources) {
      if (r instanceof File) {
        File file = (File) r;
        LOGGER.info("loading configuration from file: {}", file);
        Config user = ConfigFactory.parseFile(file);
        config = user.withFallback(config);
      } else {
        String userResource = (String) r;
        LOGGER.info("loading configuration from resource: {}", userResource);
        Config user = ConfigFactory.parseResourcesAnySyntax(userResource);
        config = user.withFallback(config);
      }
    }
    return config.resolve().getConfig("netflix.spectator.agent");
  }

  /**
   * To make debugging easier since we usually create a fat jar, system properties can
   * be created so that all jar versions can easily be accessed via JMX or using tools
   * like {@code jinfo}.
   */
  private static void createDependencyProperties(Config config) {
    if (config.hasPath("dependencies")) {
      List<String> deps = config.getStringList("dependencies");
      for (int i = 0; i < deps.size(); ++i) {
        String prop = String.format("netflix.spectator.agent.dependency.%03d", i);
        System.setProperty(prop, deps.get(i));
      }
    }
  }

  /** Entry point for the agent. */
  public static void premain(String arg, Instrumentation instrumentation) throws Exception {
    // Setup logging
    final List<Object> resources = parseResourceList(arg);
    Config config = loadConfig(resources);
    LOGGER.debug("loaded configuration: {}", config.root().render());
    createDependencyProperties(config);

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
      ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "spectator-agent-jmx");
        t.setDaemon(true);
        return t;
      });

      // Poll the JMX data at least once per step interval unless it is less than a second
      // to avoid to much overhead
      Duration step = Duration.parse(config.getString("atlas.step"));
      long delay = Math.max(1000L, step.toMillis() / 2L);

      // Keep track of last time the configs have been loaded
      final AtomicLong lastUpdated = new AtomicLong(System.currentTimeMillis());
      final JmxPoller poller = new JmxPoller(registry);
      poller.updateConfigs(config.getConfigList("jmx.mappings"));

      exec.scheduleWithFixedDelay(() -> {
        try {
          List<File> updatedConfigs = findUpdatedConfigs(resources, lastUpdated.get());
          if (!updatedConfigs.isEmpty()) {
            LOGGER.info("detected updated config files: {}", updatedConfigs);
            lastUpdated.set(System.currentTimeMillis());
            Config cfg = loadConfig(resources);
            poller.updateConfigs(cfg.getConfigList("jmx.mappings"));
          }
        } catch (Exception e) {
          LOGGER.warn("failed to update jmx config mappings, using previous config", e);
        }
        poller.poll();
      }, delay, delay, TimeUnit.MILLISECONDS);
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
      Map<String, String> tags = NetflixEnvironment.commonTagsForAtlas();
      for (Config cfg : config.getConfigList("atlas.tags")) {
        // These are often populated by environment variables that can sometimes be empty
        // rather than not set when missing. Empty strings are not allowed by Atlas.
        String value = cfg.getString("value");
        if (!value.isEmpty()) {
          tags.put(cfg.getString("key"), cfg.getString("value"));
        }
      }
      return tags;
    }
  }
}
