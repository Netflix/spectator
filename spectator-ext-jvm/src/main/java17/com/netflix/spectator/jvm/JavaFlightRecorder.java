/*
 * Copyright 2014-2024 Netflix, Inc.
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

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Timer;
import jdk.jfr.EventSettings;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingStream;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class JavaFlightRecorder {

  private static final String PREFIX = "jdk.";
  private static final String ClassLoadingStatistics = PREFIX + "ClassLoadingStatistics";
  private static final String CompilerStatistics = PREFIX + "CompilerStatistics";
  private static final String JavaThreadStatistics = PREFIX + "JavaThreadStatistics";
  private static final String VirtualThreadPinned = PREFIX + "VirtualThreadPinned";
  private static final String VirtualThreadSubmitFailed = PREFIX + "VirtualThreadSubmitFailed";
  private static final String YoungGarbageCollection = PREFIX + "YoungGarbageCollection";
  private static final String ZAllocationStall = PREFIX + "ZAllocationStall";
  private static final String ZYoungGarbageCollection = PREFIX + "ZYoungGarbageCollection";

  private JavaFlightRecorder() {
  }

  public static boolean isSupported() {
    try {
        Class.forName("jdk.jfr.consumer.RecordingStream");
    } catch (ClassNotFoundException e) {
        return false;
    }
    return true;
  }

  public static AutoCloseable monitorDefaultEvents(Registry registry, Executor executor) {
    if (!isSupported()) {
      throw new UnsupportedOperationException("This JVM does not support Java Flight Recorder event streaming");
    }
    Objects.requireNonNull(registry);
    Objects.requireNonNull(executor);
    RecordingStream rs = new RecordingStream();
    collectClassLoadingStatistics(registry, rs);
    collectCompilerStatistics(registry, rs);
    collectThreadStatistics(registry, rs);
    collectVirtualThreadEvents(registry, rs);
    collectGcEvents(registry, rs);
    executor.execute(rs::start);
    return rs::close;
  }

  private static void collectClassLoadingStatistics(Registry registry, RecordingStream rs) {
    Counter classesLoaded = registry.counter("jvm.classloading.classesLoaded");
    AtomicLong prevLoadedClassCount = new AtomicLong();
    Counter classesUnloaded = registry.counter("jvm.classloading.classesUnloaded");
    AtomicLong prevUnloadedClassCount = new AtomicLong();
    consume(ClassLoadingStatistics, rs, event -> {
      long loadedClassCount = event.getLong("loadedClassCount");
      loadedClassCount = loadedClassCount - prevLoadedClassCount.getAndSet(loadedClassCount);
      classesLoaded.increment(loadedClassCount);

      long unloadedClassCount = event.getLong("unloadedClassCount");
      unloadedClassCount = unloadedClassCount - prevUnloadedClassCount.getAndSet(unloadedClassCount);
      classesUnloaded.increment(unloadedClassCount);
    });
  }

  private static void collectCompilerStatistics(Registry registry, RecordingStream rs) {
    Counter compilationTime = registry.counter("jvm.compilation.compilationTime");
    AtomicLong prevTotalTimeSpent = new AtomicLong();
    consume(CompilerStatistics, rs, event -> {
      long totalTimeSpent = event.getLong("totalTimeSpent");
      totalTimeSpent = totalTimeSpent - prevTotalTimeSpent.getAndAdd(totalTimeSpent);
      compilationTime.add(totalTimeSpent / 1000.0);
    });
  }

  private static void collectThreadStatistics(Registry registry, RecordingStream rs) {
    Gauge nonDaemonThreadCount = registry.gauge("jvm.thread.threadCount", "id", "non-daemon");
    Gauge daemonThreadCount = registry.gauge("jvm.thread.threadCount", "id", "daemon");
    Counter threadsStarted = registry.counter("jvm.thread.threadsStarted");
    AtomicLong prevAccumulatedCount = new AtomicLong();
    consume(JavaThreadStatistics, rs, event -> {
      long activeCount = event.getLong("activeCount");
      long daemonCount = event.getLong("daemonCount");
      long nonDaemonCount = activeCount - daemonCount;
      nonDaemonThreadCount.set(nonDaemonCount);
      daemonThreadCount.set(daemonCount);
      long accumulatedCount = event.getLong("accumulatedCount");
      accumulatedCount = accumulatedCount - prevAccumulatedCount.getAndSet(accumulatedCount);
      threadsStarted.increment(accumulatedCount);
    });
  }

  private static void collectVirtualThreadEvents(Registry registry, RecordingStream rs) {
    Timer pinned = registry.timer("jvm.vt.pinned");
    Counter submitFailed = registry.counter("jvm.vt.submitFailed");
    // 20ms threshold set to match default behavior
    consume(VirtualThreadPinned, rs, event ->
      pinned.record(event.getDuration())
    ).withThreshold(Duration.ofMillis(20));
    consume(VirtualThreadSubmitFailed, rs, event ->
      submitFailed.increment()
    );
  }

  private static void collectGcEvents(Registry registry, RecordingStream rs) {
    // ZGC and Shenandoah are not covered by the generic event, there is
    // a ZGC specific event to get coverage there, right now there doesn't
    // appear to be similar data available for Shenandoah
    Gauge tenuringThreshold = registry.gauge("jvm.gc.tenuringThreshold");
    Consumer<RecordedEvent> tenuringThresholdFn = event ->
        tenuringThreshold.set(event.getLong("tenuringThreshold"));
    consume(YoungGarbageCollection, rs, tenuringThresholdFn);
    consume(ZYoungGarbageCollection, rs, tenuringThresholdFn);

    consume(ZAllocationStall, rs, event ->
      registry.timer("jvm.gc.allocationStall", "type", event.getString("type"))
        .record(event.getDuration()));
  }

  /**
   * Consume a given JFR event. For full event details see the event definitions and default/profiling configuration:
   * <p>
   * - <a href="https://github.com/openjdk/jdk/blob/master/src/hotspot/share/jfr/metadata/metadata.xml">metadata.xml</a>
   * - <a href="https://github.com/openjdk/jdk/blob/master/src/jdk.jfr/share/conf/jfr/default.jfc">default.jfc</a>
   * - <a href="https://github.com/openjdk/jdk/blob/master/src/jdk.jfr/share/conf/jfr/profile.jfc">profile.jfc</a>
   * <p>
   * We avoid the default event configurations because despite their claims of "low-overhead" there are
   * situtations where they can impose significant overhead to the application.
   */
  private static EventSettings consume(String name, RecordingStream rs, Consumer<RecordedEvent> consumer) {
    // Apply sensible defaults to settings to avoid the overhead of collecting unnecessary stacktraces
    // and collecting periodic events at a finer interval than we require upstream
    EventSettings settings = rs.enable(name)
      .withoutStackTrace()
      .withThreshold(Duration.ofMillis(0))
      .withPeriod(Duration.ofSeconds(5));
    rs.onEvent(name, consumer);
    return settings;
  }

}
