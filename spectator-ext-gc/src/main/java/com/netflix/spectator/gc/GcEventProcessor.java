/*
 * Copyright 2014-2026 Netflix, Inc.
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
import com.netflix.spectator.api.Timer;
import com.sun.management.GarbageCollectionNotificationInfo;
import com.sun.management.GcInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Core GC event processing logic shared by {@link GcLogger} and {@link GcLoggerFactory}.
 * Holds all metrics, pool-name state, gc log buffers, and event listeners for one registry.
 */
final class GcEventProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(GcEventProcessor.class);
  private static final int BUFFER_SIZE = 256;

  private final Registry registry;
  private final AtomicLong maxDataSize;
  private final AtomicLong liveDataSize;
  private final Counter promotionRate;
  private final Counter allocationRate;
  private final Counter survivorRate;
  private final Id pauseTime;
  private final Id concurrentPhaseTime;
  private final HelperFunctions.PoolNames poolNames;
  private final Map<String, CircularBuffer<GcEvent>> gcLogs;
  private final long jvmStartTime;
  private final CopyOnWriteArrayList<GcEventListener> eventListeners = new CopyOnWriteArrayList<>();

  private final NotificationListener notificationListener = new GcNotificationListener();

  private long youngGenSizeAfter = 0L;

  /** Returns the JMX notification listener that processes GC events. */
  NotificationListener getNotificationListener() {
    return notificationListener;
  }

  GcEventProcessor(Registry registry) {
    this.registry = registry;
    this.maxDataSize = registry.gauge(GcMetricNames.MAX_DATA_SIZE, new AtomicLong(0L));
    this.liveDataSize = registry.gauge(GcMetricNames.LIVE_DATA_SIZE, new AtomicLong(0L));
    this.promotionRate = registry.counter(GcMetricNames.PROMOTION_RATE);
    this.allocationRate = registry.counter(GcMetricNames.ALLOCATION_RATE);
    this.survivorRate = registry.counter(GcMetricNames.SURVIVOR_RATE);
    this.pauseTime = registry.createId(GcMetricNames.PAUSE_TIME);
    this.concurrentPhaseTime = registry.createId(GcMetricNames.CONCURRENT_PHASE_TIME);
    this.poolNames = HelperFunctions.detectPoolNames();
    this.jvmStartTime = ManagementFactory.getRuntimeMXBean().getStartTime();

    Map<String, CircularBuffer<GcEvent>> logs = new HashMap<>();
    for (GarbageCollectorMXBean mbean : ManagementFactory.getGarbageCollectorMXBeans()) {
      logs.put(mbean.getName(), new CircularBuffer<>(BUFFER_SIZE));
    }
    this.gcLogs = Collections.unmodifiableMap(logs);
  }

  /** Add a listener that fires on each GC event. */
  void addListener(GcEventListener listener) {
    eventListeners.add(listener);
  }

  /** Add a listener and return a handle that removes only this listener when closed. */
  AutoCloseable addRemovableListener(GcEventListener listener) {
    eventListeners.add(listener);
    return () -> eventListeners.remove(listener);
  }

  /** Returns recent GC events from all collectors, sorted newest-first. */
  List<GcEvent> getLogs() {
    List<GcEvent> logs = new ArrayList<>();
    for (CircularBuffer<GcEvent> buffer : gcLogs.values()) {
      logs.addAll(buffer.toList());
    }
    logs.sort(GcEvent.REVERSE_TIME_ORDER);
    return logs;
  }

  private void updateMetrics(String name, GcInfo info) {
    final Map<String, MemoryUsage> before = info.getMemoryUsageBeforeGc();
    final Map<String, MemoryUsage> after = info.getMemoryUsageAfterGc();

    if (poolNames.oldGen() != null) {
      final long oldBefore = before.get(poolNames.oldGen()).getUsed();
      final long oldAfter = after.get(poolNames.oldGen()).getUsed();
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
        maxDataSize.set(after.get(poolNames.oldGen()).getMax());
      }
    }

    if (poolNames.survivor() != null) {
      final long survivorBefore = before.get(poolNames.survivor()).getUsed();
      final long survivorAfter = after.get(poolNames.survivor()).getUsed();
      final long delta = survivorAfter - survivorBefore;
      if (delta > 0L) {
        survivorRate.increment(delta);
      }
    }

    if (poolNames.youngGen() != null) {
      final long youngBefore = before.get(poolNames.youngGen()).getUsed();
      final long youngAfter = after.get(poolNames.youngGen()).getUsed();
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

  private void processGcEvent(GarbageCollectionNotificationInfo info) {
    GcEvent event = new GcEvent(info, jvmStartTime + info.getGcInfo().getStartTime());
    CircularBuffer<GcEvent> buffer = gcLogs.get(info.getGcName());
    if (buffer != null) {
      buffer.add(event);
    }
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(event.toString());
    }

    Id eventId = (HelperFunctions.isConcurrentPhase(info) ? concurrentPhaseTime : pauseTime)
        .withTag("action", info.getGcAction())
        .withTag("cause", info.getGcCause());
    Timer timer = registry.timer(eventId);
    long duration = Math.max(1, info.getGcInfo().getDuration());
    timer.record(duration, TimeUnit.MILLISECONDS);

    updateMetrics(info.getGcName(), info.getGcInfo());

    for (GcEventListener listener : eventListeners) {
      try {
        listener.onComplete(event);
      } catch (Exception e) {
        LOGGER.warn("exception thrown by event listener", e);
      }
    }
  }

  private final class GcNotificationListener implements NotificationListener {
    @Override
    public void handleNotification(Notification notification, Object ref) {
      if (GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION
          .equals(notification.getType())) {
        CompositeData cd = (CompositeData) notification.getUserData();
        processGcEvent(GarbageCollectionNotificationInfo.from(cd));
      }
    }
  }
}
