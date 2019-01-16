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

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.atlas.AtlasRegistry;
import com.netflix.spectator.servo.ServoRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.openjdk.jol.info.GraphLayout;

/**
 * Runs some basic tests against Spectator registry implementations to help gauge the
 * performance. This is used for automated tests around the memory usage and can also be
 * used for capturing flame graphs or with flight recorder to get CPU profile information.
 */
public final class Main {

  private Main() {
  }

  private static Registry createRegistry(String type) {
    Registry registry = null;
    switch (type) {
      case "noop":
        registry = new NoopRegistry();
        break;
      case "default":
        registry = new DefaultRegistry();
        break;
      case "atlas":
        registry = new AtlasRegistry(Clock.SYSTEM, System::getProperty);
        break;
      case "servo":
        registry = new ServoRegistry();
        break;
      default:
        throw new IllegalArgumentException("unknown registry type: '" + type + "'");
    }
    return registry;
  }

  private static final Map<String, Consumer<Registry>> TESTS = new HashMap<>();
  static {
    TESTS.put("many-tags", new ManyTags());
    TESTS.put("name-explosion", new NameExplosion());
    TESTS.put("tag-key-explosion", new TagKeyExplosion());
    TESTS.put("tag-value-explosion", new TagValueExplosion());
  }

  /**
   * Run a test for a given type of registry.
   *
   * @param type
   *     Should be one of `noop`, `default`, `atlas`, or `servo`.
   * @param test
   *     Test name to run. See the `TESTS` map for available options.
   */
  public static Registry run(String type, String test) {
    Registry registry = createRegistry(type);
    TESTS.get(test).accept(registry);
    return registry;
  }

  /** Run a test and output the memory footprint of the registry. */
  @SuppressWarnings("PMD")
  public static void main(String[] args) {
    if (args.length < 2) {
      System.out.println("Usage: perf <registry> <test>");
      System.exit(1);
    }

    Registry registry = run(args[0], args[1]);

    GraphLayout igraph = GraphLayout.parseInstance(registry);
    System.out.println(igraph.toFootprint());
  }
}
