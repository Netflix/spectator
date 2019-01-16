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
package com.netflix.spectator.jvm;

import com.netflix.spectator.api.Registry;
import com.typesafe.config.Config;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;

/**
 * Helpers for working with JMX mbeans.
 */
public final class Jmx {

  private Jmx() {
  }

  /**
   * Add meters for the standard MXBeans provided by the jvm. This method will use
   * {@link java.lang.management.ManagementFactory#getPlatformMXBeans(Class)} to get the set of
   * mbeans from the local jvm.
   */
  public static void registerStandardMXBeans(Registry registry) {
    for (MemoryPoolMXBean mbean : ManagementFactory.getPlatformMXBeans(MemoryPoolMXBean.class)) {
      registry.register(new MemoryPoolMeter(registry, mbean));
    }

    for (BufferPoolMXBean mbean : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
      registry.register(new BufferPoolMeter(registry, mbean));
    }
  }

  /**
   * Add meters based on configured JMX queries. See the {@link JmxConfig} class for more
   * details.
   *
   * @param registry
   *     Registry to use for reporting the data.
   * @param cfg
   *     Config object with the mappings.
   */
  public static void registerMappingsFromConfig(Registry registry, Config cfg) {
    registry.register(new JmxMeter(registry, JmxConfig.from(cfg)));
  }
}
