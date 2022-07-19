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

import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.patterns.PolledMeter;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.MBeanServer;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.CompilationMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.ThreadMXBean;

/**
 * Helpers for working with JMX mbeans.
 */
public final class Jmx {

  private static final Logger LOGGER = LoggerFactory.getLogger(Jmx.class);

  private Jmx() {
  }

  /**
   * Add meters for the standard MXBeans provided by the jvm. This method will use
   * {@link java.lang.management.ManagementFactory#getPlatformMXBeans(Class)} to get the set of
   * mbeans from the local jvm.
   */
  public static void registerStandardMXBeans(Registry registry) {
    monitorClassLoadingMXBean(registry);
    monitorThreadMXBean(registry);
    monitorCompilationMXBean(registry);
    maybeRegisterHotspotInternal(registry);

    for (MemoryPoolMXBean mbean : ManagementFactory.getMemoryPoolMXBeans()) {
      registry.register(new MemoryPoolMeter(registry, mbean));
    }
    for (BufferPoolMXBean mbean : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
      registry.register(new BufferPoolMeter(registry, mbean));
    }
  }

  private static void monitorClassLoadingMXBean(Registry registry) {
    ClassLoadingMXBean classLoadingMXBean = ManagementFactory.getClassLoadingMXBean();
    PolledMeter.using(registry)
      .withName("jvm.classloading.classesLoaded")
      .monitorMonotonicCounter(classLoadingMXBean, ClassLoadingMXBean::getTotalLoadedClassCount);
    PolledMeter.using(registry)
      .withName("jvm.classloading.classesUnloaded")
      .monitorMonotonicCounter(classLoadingMXBean, ClassLoadingMXBean::getUnloadedClassCount);
  }

  private static void monitorThreadMXBean(Registry registry) {
    ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
    PolledMeter.using(registry)
      .withName("jvm.thread.threadsStarted")
      .monitorMonotonicCounter(threadMXBean, ThreadMXBean::getTotalStartedThreadCount);

    Gauge nonDaemonThreadCount = registry.gauge("jvm.thread.threadCount", "id", "non-daemon");
    Gauge daemonThreadCount = registry.gauge("jvm.thread.threadCount", "id", "daemon");
    PolledMeter.poll(registry, () -> {
      int threads = threadMXBean.getThreadCount();
      int daemonThreads = threadMXBean.getDaemonThreadCount();
      nonDaemonThreadCount.set(Math.max(0, threads - daemonThreads));
      daemonThreadCount.set(daemonThreads);
    });
  }

  private static void monitorCompilationMXBean(Registry registry) {
    CompilationMXBean compilationMXBean = ManagementFactory.getCompilationMXBean();
    if (compilationMXBean.isCompilationTimeMonitoringSupported()) {
      PolledMeter.using(registry)
        .withName("jvm.compilation.compilationTime")
        .withTag("compiler", compilationMXBean.getName())
        .monitorMonotonicCounterDouble(compilationMXBean, c -> c.getTotalCompilationTime() / 1000.0);
    }
  }

  @SuppressWarnings("PMD.AvoidCatchingThrowable")
  private static void maybeRegisterHotspotInternal(Registry registry) {
    MBeanServer server = ManagementFactory.getPlatformMBeanServer();
    try {
      sun.management.HotspotInternal hotspotInternal = new sun.management.HotspotInternal();
      server.registerMBean(hotspotInternal, null);
    } catch (Throwable t) {
      // Not Hotspot or IllegalAccessError from JDK 16+ due sun.management package being inaccessible
      LOGGER.trace("Unable to register HotspotInternal MBean", t);
      return;
    }
    for (Config config : ConfigFactory.parseResources("hotspot.conf")
            .resolve()
            .getConfigList("netflix.spectator.agent.jmx.mappings")) {
      registerMappingsFromConfig(registry, config);
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
