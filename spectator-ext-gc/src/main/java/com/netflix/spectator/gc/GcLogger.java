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
package com.netflix.spectator.gc;

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Spectator;
import com.netflix.spectator.api.Timer;
import com.netflix.spectator.api.patterns.PolledMeter;
import com.netflix.spectator.impl.Preconditions;
import com.sun.management.GarbageCollectionNotificationInfo;
import com.sun.management.GcInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.ListenerNotFoundException;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Logger to collect GC notifcation events.
 *
 * <p>At most one instance should report to a given registry. The metrics are registered on
 * the supplied registry when the instance is constructed, so constructing multiple instances
 * that share a registry will double-count the rate counters and register duplicate gauges for
 * the same ids. Because the no-arg constructor uses the {@link Spectator#globalRegistry()},
 * in the common case this means a single instance per JVM. Each started instance also adds its
 * own listener to the GC MXBeans, so every GC event is processed once per started instance.
 * {@link #start(GcEventListener)} is a no-op if the instance has already been started.</p>
 */
public final class GcLogger {

  private static final Logger LOGGER = LoggerFactory.getLogger(GcLogger.class);

  // One major GC per hour would require 168 for a week
  // One minor GC per minute would require 180 for three hours
  private static final int BUFFER_SIZE = 256;

  // Registry used to report the GC metrics
  private final Registry registry;

  // Max size of old generation memory pool
  private final AtomicLong maxDataSize;

  // Size of old generation memory pool after a full GC
  private final AtomicLong liveDataSize;

  // Incremented for any positive increases in the size of the old generation memory pool
  // before GC to after GC
  private final Counter promotionRate;

  // Incremented for the increase in the size of the young generation memory pool after one GC
  // to before the next
  private final Counter allocationRate;

  // Incremented for any positive increases in the size of the survivor memory pool
  // before GC to after GC
  private final Counter survivorRate;

  // Pause time due to GC event
  private final Id pauseTime;

  // Time spent in concurrent phases of GC
  private final Id concurrentPhaseTime;

  private final Lock lock = new ReentrantLock();

  private final long jvmStartTime;

  private final Map<String, CircularBuffer<GcEvent>> gcLogs;

  private long youngGenSizeAfter = 0L;

  private String youngGenPoolName = null;

  private String survivorPoolName = null;

  private String oldGenPoolName = null;

  private GcNotificationListener notifListener = null;

  private GcEventListener eventListener = null;

  /** Create a new instance that reports metrics to the {@link Spectator#globalRegistry()}. */
  public GcLogger() {
    this(Spectator.globalRegistry());
  }

  /**
   * Create a new instance that reports metrics to the provided registry.
   *
   * @param registry
   *     Registry used to report the GC metrics. A single instance per JVM is expected; see
   *     the class documentation for details.
   */
  public GcLogger(Registry registry) {
    this.registry = registry;
    this.maxDataSize = registry.gauge("jvm.gc.maxDataSize", new AtomicLong(0L));
    this.liveDataSize = registry.gauge("jvm.gc.liveDataSize", new AtomicLong(0L));
    this.promotionRate = registry.counter("jvm.gc.promotionRate");
    this.allocationRate = registry.counter("jvm.gc.allocationRate");
    this.survivorRate = registry.counter("jvm.gc.survivorRate");
    this.pauseTime = registry.createId("jvm.gc.pause");
    this.concurrentPhaseTime = registry.createId("jvm.gc.concurrentPhaseTime");

    jvmStartTime = ManagementFactory.getRuntimeMXBean().getStartTime();
    Map<String, CircularBuffer<GcEvent>> gcLogs = new HashMap<>();
    for (GarbageCollectorMXBean mbean : ManagementFactory.getGarbageCollectorMXBeans()) {
      CircularBuffer<GcEvent> buffer = new CircularBuffer<>(BUFFER_SIZE);
      gcLogs.put(mbean.getName(), buffer);
    }
    this.gcLogs = Collections.unmodifiableMap(gcLogs);

    for (MemoryPoolMXBean mbean : ManagementFactory.getMemoryPoolMXBeans()) {
      String poolName = mbean.getName();
      // For non-generational collectors the young and old gen pool names will be the
      // same
      if (HelperFunctions.isYoungGenPool(poolName)) {
        youngGenPoolName = poolName;
      }
      if (HelperFunctions.isSurvivorPool(poolName)) {
        survivorPoolName = poolName;
      }
      if (HelperFunctions.isOldGenPool(poolName)) {
        oldGenPoolName = poolName;
      }
    }
  }

  /**
   * Start collecting GC events for the provided registry and tie the collection to the registry
   * lifecycle: closing the registry (or calling {@link PolledMeter#removeAll(Registry)}) will
   * stop the logger. This is a convenience for the common case of monitoring GC for a registry
   * without managing the {@link #start(GcEventListener)} / {@link #stop()} lifecycle by hand.
   *
   * @param registry
   *     Registry used to report the GC metrics and whose lifecycle controls the logger.
   * @return
   *     Handle that can be used to stop the logger earlier; closing it stops collection. Closing
   *     the handle is idempotent and is harmless if the registry has already been closed.
   */
  public static AutoCloseable monitor(Registry registry) {
    GcLogger logger = new GcLogger(registry);
    logger.start(null);
    return PolledMeter.monitorResource(registry, logger::stop);
  }

  /**
   * Start collecting data about GC events.
   *
   * @param listener
   *     If not null, the listener will be called with the event objects after metrics and the
   *     log buffer is updated.
   */
  public void start(GcEventListener listener) {
    lock.lock();
    try {
      // Guard against double-start on this instance. Starting more than once (or starting
      // multiple instances) would register duplicate notification listeners and double-count
      // events, so this is a no-op if already started.
      if (notifListener != null) {
        LOGGER.warn("logger already started");
        return;
      }
      eventListener = listener;
      notifListener = new GcNotificationListener();
      for (GarbageCollectorMXBean mbean : ManagementFactory.getGarbageCollectorMXBeans()) {
        if (mbean instanceof NotificationEmitter) {
          final NotificationEmitter emitter = (NotificationEmitter) mbean;
          emitter.addNotificationListener(notifListener, null, null);
        }
      }
    } finally {
      lock.unlock();
    }
  }

  /** Stop collecting GC events. */
  public void stop() {
    lock.lock();
    try {
      Preconditions.checkState(notifListener != null, "logger has not been started");
      for (GarbageCollectorMXBean mbean : ManagementFactory.getGarbageCollectorMXBeans()) {
        if (mbean instanceof NotificationEmitter) {
          final NotificationEmitter emitter = (NotificationEmitter) mbean;
          try {
            emitter.removeNotificationListener(notifListener);
          } catch (ListenerNotFoundException e) {
            LOGGER.warn("could not remove gc listener", e);
          }
        }
      }
      notifListener = null;
    } finally {
      lock.unlock();
    }
  }

  /** Return the current set of GC events in the in-memory log. */
  public List<GcEvent> getLogs() {
    final List<GcEvent> logs = new ArrayList<>();
    for (CircularBuffer<GcEvent> buffer : gcLogs.values()) {
      logs.addAll(buffer.toList());
    }
    logs.sort(GcEvent.REVERSE_TIME_ORDER);
    return logs;
  }

  private void updateMetrics(String name, GcInfo info) {
    final Map<String, MemoryUsage> before = info.getMemoryUsageBeforeGc();
    final Map<String, MemoryUsage> after = info.getMemoryUsageAfterGc();

    if (oldGenPoolName != null) {
      final long oldBefore = before.get(oldGenPoolName).getUsed();
      final long oldAfter = after.get(oldGenPoolName).getUsed();
      final long delta = oldAfter - oldBefore;
      if (delta > 0L) {
        promotionRate.increment(delta);
      }

      // Shenandoah doesn't report accurate pool sizes for pauses, all numbers are 0. Ignore
      // those updates.
      //
      // Some GCs such as G1 can reduce the old gen size as part of a minor GC. To track the
      // live data size we record the value if we see a reduction in the old gen heap size or
      // after a major GC.
      if (oldAfter > 0L && (oldAfter < oldBefore || HelperFunctions.isOldGcType(name))) {
        liveDataSize.set(oldAfter);
        final long oldMaxAfter = after.get(oldGenPoolName).getMax();
        maxDataSize.set(oldMaxAfter);
      }
    }

    if (survivorPoolName != null) {
      final long survivorBefore = before.get(survivorPoolName).getUsed();
      final long survivorAfter = after.get(survivorPoolName).getUsed();
      final long delta = survivorAfter - survivorBefore;
      if (delta > 0L) {
        survivorRate.increment(delta);
      }
    }

    if (youngGenPoolName != null) {
      final long youngBefore = before.get(youngGenPoolName).getUsed();
      final long youngAfter = after.get(youngGenPoolName).getUsed();
      // Shenandoah doesn't report accurate pool sizes for pauses, all numbers are 0. Ignore
      // those updates.
      if (youngBefore > 0L) {
        final long delta = youngBefore - youngGenSizeAfter;
        youngGenSizeAfter = youngAfter;
        if (delta > 0L) {
          allocationRate.increment(delta);
        }
      }
    }
  }

  // Package-private rather than private so tests can drive the event-processing path with a
  // synthetic notification.
  void processGcEvent(GarbageCollectionNotificationInfo info) {
    GcEvent event = new GcEvent(info, jvmStartTime + info.getGcInfo().getStartTime());
    gcLogs.get(info.getGcName()).add(event);
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(event.toString());
    }

    // Update pause timer for the action and cause...
    Id eventId = (isConcurrentPhase(info) ? concurrentPhaseTime : pauseTime)
      .withTag("action", info.getGcAction())
      .withTag("cause", info.getGcCause());
    Timer timer = registry.timer(eventId);
    long duration = Math.max(1, info.getGcInfo().getDuration());
    timer.record(duration, TimeUnit.MILLISECONDS);

    // Update promotion and allocation counters
    updateMetrics(info.getGcName(), info.getGcInfo());

    // Notify an event listener if registered
    if (eventListener != null) {
      try {
        eventListener.onComplete(event);
      } catch (Exception e) {
        LOGGER.warn("exception thrown by event listener", e);
      }
    }
  }

  private boolean isConcurrentPhase(GarbageCollectionNotificationInfo info) {
    // So far the only indicator known is that the cause will be reported as "No GC"
    // when using CMS.
    //
    // For ZGC, behavior was changed in JDK17:
    // https://bugs.openjdk.java.net/browse/JDK-8265136
    //
    // For ZGC in older versions, there is no way to accurately get the amount of time
    // in STW pauses.
    //
    // For G1, a new bean was added in JDK20 to indicate time spent in concurrent
    // phases:
    // https://bugs.openjdk.org/browse/JDK-8297247
    return "No GC".equals(info.getGcCause())           // CMS
        || "G1 Concurrent GC".equals(info.getGcName()) // G1 in JDK20+
        || info.getGcName().endsWith(" Cycles");       // Shenandoah, ZGC
  }

  private final class GcNotificationListener implements NotificationListener {
    @Override public void handleNotification(Notification notification, Object ref) {
      final String type = notification.getType();
      if (GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(type)) {
        CompositeData cd = (CompositeData) notification.getUserData();
        GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from(cd);
        processGcEvent(info);
      }
    }
  }
}
