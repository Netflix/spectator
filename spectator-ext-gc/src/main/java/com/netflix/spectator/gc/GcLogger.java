/**
 * Copyright 2015 Netflix, Inc.
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
import com.netflix.spectator.api.Spectator;
import com.netflix.spectator.api.Timer;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Logger to collect GC notifcation events.
 */
public final class GcLogger {

  private static final Logger LOGGER = LoggerFactory.getLogger(GcLogger.class);

  // One major GC per hour would require 168 for a week
  // One minor GC per minute would require 180 for three hours
  private static final int BUFFER_SIZE = 256;

  // Max size of old generation memory pool
  private static final AtomicLong MAX_DATA_SIZE =
    Spectator.registry().gauge("jvm.gc.maxDataSize", new AtomicLong(0L));

  // Size of old generation memory pool after a full GC
  private static final AtomicLong LIVE_DATA_SIZE =
    Spectator.registry().gauge("jvm.gc.liveDataSize", new AtomicLong(0L));

  // Incremented for any positive increases in the size of the old generation memory pool
  // before GC to after GC
  private static final Counter PROMOTION_RATE =
    Spectator.registry().counter("jvm.gc.promotionRate");

  // Incremented for the increase in the size of the young generation memory pool after one GC
  // to before the next
  private static final Counter ALLOCATION_RATE =
    Spectator.registry().counter("jvm.gc.allocationRate");

  // Pause time due to GC event
  private static final Id PAUSE_TIME = Spectator.registry().createId("jvm.gc.pause");

  private final long jvmStartTime;

  private final ConcurrentHashMap<String, CircularBuffer<GcEvent>> gcLogs = new ConcurrentHashMap<>();

  private long youngGenSizeAfter = 0L;

  private String youngGenPoolName = null;
  private String oldGenPoolName = null;

  private GcNotificationListener notifListener = null;

  private GcEventListener eventListener = null;

  /** Create a new instance. */
  public GcLogger() {
    jvmStartTime = ManagementFactory.getRuntimeMXBean().getStartTime();
    for (GarbageCollectorMXBean mbean : ManagementFactory.getGarbageCollectorMXBeans()) {
      CircularBuffer<GcEvent> buffer = new CircularBuffer<>(BUFFER_SIZE);
      gcLogs.put(mbean.getName(), buffer);
    }

    for (MemoryPoolMXBean mbean : ManagementFactory.getMemoryPoolMXBeans()) {
      if (HelperFunctions.isYoungGenPool(mbean.getName())) {
        youngGenPoolName = mbean.getName();
      }
      if (HelperFunctions.isOldGenPool(mbean.getName())) {
        oldGenPoolName = mbean.getName();
      }
    }
  }

  /**
   * Start collecting data about GC events.
   *
   * @param listener
   *     If not null, the listener will be called with the event objects after metrics and the
   *     log buffer is updated.
   */
  public synchronized void start(GcEventListener listener) {
    // TODO: this class has a bad mix of static fields used from an instance of the class. For now
    // this has been changed not to throw to make the dependency injection use-cases work. A
    // more general refactor of the GcLogger class is needed.
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
  }

  /** Stop collecting GC events. */
  public synchronized void stop() {
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
  }

  /** Return the current set of GC events in the in-memory log. */
  public List<GcEvent> getLogs() {
    final List<GcEvent> logs = new ArrayList<>();
    for (CircularBuffer<GcEvent> buffer : gcLogs.values()) {
      logs.addAll(buffer.toList());
    }
    Collections.sort(logs, GcEvent.REVERSE_TIME_ORDER);
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
        PROMOTION_RATE.increment(delta);
      }

      // Some GCs such as G1 can reduce the old gen size as part of a minor GC. To track the
      // live data size we record the value if we see a reduction in the old gen heap size or
      // after a major GC.
      if (oldAfter < oldBefore || HelperFunctions.getGcType(name) == GcType.OLD) {
        LIVE_DATA_SIZE.set(oldAfter);
        final long oldMaxAfter = after.get(oldGenPoolName).getMax();
        MAX_DATA_SIZE.set(oldMaxAfter);
      }
    }

    if (youngGenPoolName != null) {
      final long youngBefore = before.get(youngGenPoolName).getUsed();
      final long youngAfter = after.get(youngGenPoolName).getUsed();
      final long delta = youngBefore - youngGenSizeAfter;
      youngGenSizeAfter = youngAfter;
      if (delta > 0L) {
        ALLOCATION_RATE.increment(delta);
      }
    }
  }

  private void processGcEvent(GarbageCollectionNotificationInfo info) {
    GcEvent event = new GcEvent(info, jvmStartTime + info.getGcInfo().getStartTime());
    gcLogs.get(info.getGcName()).add(event);
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(event.toString());
    }

    // Update pause timer for the action and cause...
    Id eventId = PAUSE_TIME
      .withTag("action", info.getGcAction())
      .withTag("cause", info.getGcCause());
    Timer timer = Spectator.registry().timer(eventId);
    timer.record(info.getGcInfo().getDuration(), TimeUnit.MILLISECONDS);

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

  private class GcNotificationListener implements NotificationListener {
    public void handleNotification(Notification notification, Object ref) {
      final String type = notification.getType();
      if (type.equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION)) {
        CompositeData cd = (CompositeData) notification.getUserData();
        GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from(cd);
        processGcEvent(info);
      }
    }
  }
}
