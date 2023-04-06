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

import com.netflix.spectator.api.Registry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.openjdk.jol.info.GraphLayout;

public abstract class MemoryUseTest {

  private String registryType;

  public MemoryUseTest(String registryType) {
    this.registryType = registryType;
  }

  private double javaMajorVersion() {
    return Double.parseDouble(System.getProperty("java.specification.version"));
  }

  private void checkMemoryUsage(Registry registry, long limit) {
    // JOL doesn't seem to work with jdk20, will investigate later
    Assumptions.assumeTrue(javaMajorVersion() < 20.0);
    GraphLayout graph = GraphLayout.parseInstance(registry);
    long size = graph.totalSize();
    String details = "memory use exceeds limit: " + size + " > " + limit + "\n\n" + graph.toFootprint();
    //System.out.println(details);
    Assertions.assertTrue(size <= limit, details);
  }

  @Test
  public void manyTags() {
    Registry registry = Main.run(registryType, "many-tags");
    checkMemoryUsage(registry, 8_000_000);
  }

  @Test
  public void tagKeyExplosion() {
    Registry registry = Main.run(registryType, "tag-key-explosion");
    checkMemoryUsage(registry, 8_000_000);
  }

  @Test
  public void tagValueExplosion() {
    Registry registry = Main.run(registryType, "tag-value-explosion");
    checkMemoryUsage(registry, 8_000_000);
  }

  @Test
  public void nameExplosion() {
    Registry registry = Main.run(registryType, "name-explosion");
    checkMemoryUsage(registry, 8_000_000);
  }
}
