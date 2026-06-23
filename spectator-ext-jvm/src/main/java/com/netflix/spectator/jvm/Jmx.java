/*
 * Copyright 2014-2022 Netflix, Inc.
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
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.patterns.PolledMeter;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Helpers for working with JMX mbeans.
 */
public final class Jmx {

  private static final Logger LOGGER = LoggerFactory.getLogger(Jmx.class);

  private static final AtomicLong PREV_GC_CPU_TIME = new AtomicLong(-1L);
  private static final AtomicLong PREV_PROCESS_CPU_TIME = new AtomicLong(-1L);
  private static final Method GC_CPU_TIME_METHOD = getGcCpuTimeMethod();

  private Jmx() {
  }

  /**
   * Add meters for the standard MXBeans provided by the jvm. This method will use
   * {@link java.lang.management.ManagementFactory#getPlatformMXBeans(Class)} to get the set of
   * mbeans from the local jvm.
   *
   * <p>The returned {@link AutoCloseable} can be used to stop polling and release resources.
   * Existing callers that do not need lifecycle management can safely ignore the return value.</p>
   */
  public static void registerStandardMXBeans(Registry registry) {
    startMonitoringStandardMXBeans(registry);
  }

  public static AutoCloseable startMonitoringStandardMXBeans(Registry registry) {
    List<AutoCloseable> closeables = new ArrayList<>();
    if (JavaFlightRecorder.isSupported()) {
      ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "spectator-jfr");
        t.setDaemon(true);
        return t;
      });
      AutoCloseable jfr = JavaFlightRecorder.monitorDefaultEvents(registry, executor);
      closeables.add(() -> {
        jfr.close();
        executor.shutdownNow();
      });
    } else {
      closeables.add(monitorClassLoadingMXBean(registry));
      closeables.add(monitorThreadMXBean(registry));
      closeables.add(monitorCompilationMXBean(registry));
    }

    closeables.add(monitorGcOverhead(registry));
    for (MemoryPoolMXBean mbean : ManagementFactory.getMemoryPoolMXBeans()) {
      monitorMemoryPoolMXBean(registry, mbean);
    }
    for (BufferPoolMXBean mbean : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
      monitorBufferPoolMXBean(registry, mbean);
    }

    return () -> {
      for (AutoCloseable c : closeables) {
        c.close();
      }
    };
  }

  private static void monitorMemoryPoolMXBean(Registry registry, MemoryPoolMXBean mbean) {
    String name = mbean.getName();
    String type = mbean.getType().name();
    Gauge used = registry.gauge("jvm.memory.used", "id", name, "memtype", type);
    Gauge committed = registry.gauge("jvm.memory.committed", "id", name, "memtype", type);
    Gauge max = registry.gauge("jvm.memory.max", "id", name, "memtype", type);
    // Sample usage once per poll so used/committed/max come from a single consistent snapshot
    // (as the previous MemoryPoolMeter did) and getUsage() is called once rather than per gauge.
    // getUsage() can return null for an invalid pool, so guard against it.
    PolledMeter.poll(registry, () -> {
      MemoryUsage usage = mbean.getUsage();
      if (usage != null) {
        used.set(usage.getUsed());
        committed.set(usage.getCommitted());
        max.set(usage.getMax());
      }
    });
  }

  private static void monitorBufferPoolMXBean(Registry registry, BufferPoolMXBean mbean) {
    String name = mbean.getName();
    PolledMeter.using(registry)
      .withName("jvm.buffer.count")
      .withTag("id", name)
      .monitorValue(mbean, BufferPoolMXBean::getCount);
    PolledMeter.using(registry)
      .withName("jvm.buffer.memoryUsed")
      .withTag("id", name)
      .monitorValue(mbean, BufferPoolMXBean::getMemoryUsed);
  }

  private static AutoCloseable monitorGcOverhead(Registry registry) {
    Id id = registry.createId("jvm.gc.overhead");
    PolledMeter.using(registry)
            .withId(id)
            .monitorStaticMethodValue(Jmx::getGcOverhead);
    return () -> PolledMeter.remove(registry, id);
  }

  private static Method getGcCpuTimeMethod() {
    try {
      // OpenJDK 26 and later - see https://bugs.openjdk.org/browse/JDK-8368529
      return MemoryMXBean.class.getMethod("getTotalGcCpuTime");
    } catch (NoSuchMethodException ignore) {
      return null;
    }
  }

  private static double getGcOverhead() {
    if (GC_CPU_TIME_METHOD == null) {
      return Double.NaN;
    }
    try {
      com.sun.management.OperatingSystemMXBean os = (com.sun.management.OperatingSystemMXBean)
              ManagementFactory.getOperatingSystemMXBean();
      MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
      long gcCpuTime = (long) GC_CPU_TIME_METHOD.invoke(memory);
      long processCpuTime = os.getProcessCpuTime();
      long prevGc = PREV_GC_CPU_TIME.getAndSet(gcCpuTime);
      long prevProcess = PREV_PROCESS_CPU_TIME.getAndSet(processCpuTime);
      if (prevGc < 0 || prevProcess < 0) {
        return Double.NaN;
      }
      long deltaProcess = processCpuTime - prevProcess;
      long deltaGc = gcCpuTime - prevGc;
      return deltaProcess <= 0 ? Double.NaN : (double) deltaGc / deltaProcess;
    } catch (InvocationTargetException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  private static AutoCloseable monitorClassLoadingMXBean(Registry registry) {
    Id loadedId = registry.createId("jvm.classloading.classesLoaded");
    Id unloadedId = registry.createId("jvm.classloading.classesUnloaded");
    ClassLoadingMXBean classLoadingMXBean = ManagementFactory.getClassLoadingMXBean();
    PolledMeter.using(registry)
      .withId(loadedId)
      .monitorMonotonicCounter(classLoadingMXBean, ClassLoadingMXBean::getTotalLoadedClassCount);
    PolledMeter.using(registry)
      .withId(unloadedId)
      .monitorMonotonicCounter(classLoadingMXBean, ClassLoadingMXBean::getUnloadedClassCount);
    return () -> {
      PolledMeter.remove(registry, loadedId);
      PolledMeter.remove(registry, unloadedId);
    };
  }

  private static AutoCloseable monitorThreadMXBean(Registry registry) {
    Id startedId = registry.createId("jvm.thread.threadsStarted");
    ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
    PolledMeter.using(registry)
      .withId(startedId)
      .monitorMonotonicCounter(threadMXBean, ThreadMXBean::getTotalStartedThreadCount);

    Gauge nonDaemonThreadCount = registry.gauge("jvm.thread.threadCount", "id", "non-daemon");
    Gauge daemonThreadCount = registry.gauge("jvm.thread.threadCount", "id", "daemon");
    ScheduledFuture<?> future = PolledMeter.poll(registry, () -> {
      int threads = threadMXBean.getThreadCount();
      int daemonThreads = threadMXBean.getDaemonThreadCount();
      nonDaemonThreadCount.set(Math.max(0, threads - daemonThreads));
      daemonThreadCount.set(daemonThreads);
    });
    return () -> {
      future.cancel(false);
      PolledMeter.remove(registry, startedId);
    };
  }

  private static AutoCloseable monitorCompilationMXBean(Registry registry) {
    CompilationMXBean compilationMXBean = ManagementFactory.getCompilationMXBean();
    if (!compilationMXBean.isCompilationTimeMonitoringSupported()) {
      return () -> {};
    }
    Id id = registry.createId("jvm.compilation.compilationTime")
        .withTag("compiler", compilationMXBean.getName());
    PolledMeter.using(registry)
      .withId(id)
      .monitorMonotonicCounterDouble(compilationMXBean, c -> c.getTotalCompilationTime() / 1000.0);
    return () -> PolledMeter.remove(registry, id);
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
    JmxConfig config = JmxConfig.from(cfg);
    PolledMeter.poll(registry, () -> {
      try {
        for (JmxData data : JmxData.query(config.getQuery())) {
          for (JmxMeasurementConfig measurementConfig : config.getMeasurements()) {
            measurementConfig.measure(registry, data);
          }
        }
      } catch (Exception e) {
        LOGGER.warn("failed to query jmx data: {}", config.getQuery().getCanonicalName(), e);
      }
    });
  }
}
