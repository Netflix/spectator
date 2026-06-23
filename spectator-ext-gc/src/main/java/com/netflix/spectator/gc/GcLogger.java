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

import com.netflix.spectator.api.Spectator;
import com.netflix.spectator.impl.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.ListenerNotFoundException;
import javax.management.NotificationEmitter;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Logger to collect GC notifcation events.
 */
public final class GcLogger {

  private static final Logger LOGGER = LoggerFactory.getLogger(GcLogger.class);
  private static final GcEventProcessor PROCESSOR = new GcEventProcessor(Spectator.globalRegistry());

  private final Lock lock = new ReentrantLock();
  private boolean started = false;
  private AutoCloseable listenerRef = null;

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
      if (started) {
        LOGGER.warn("logger already started");
        return;
      }
      started = true;
      if (listener != null) {
        listenerRef = PROCESSOR.addRemovableListener(listener);
      }
      for (GarbageCollectorMXBean mbean : ManagementFactory.getGarbageCollectorMXBeans()) {
        if (mbean instanceof NotificationEmitter) {
          ((NotificationEmitter) mbean).addNotificationListener(
              PROCESSOR.getNotificationListener(), null, null);
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
      Preconditions.checkState(started, "logger has not been started");
      for (GarbageCollectorMXBean mbean : ManagementFactory.getGarbageCollectorMXBeans()) {
        if (mbean instanceof NotificationEmitter) {
          try {
            ((NotificationEmitter) mbean).removeNotificationListener(PROCESSOR.getNotificationListener());
          } catch (ListenerNotFoundException e) {
            LOGGER.warn("could not remove gc listener", e);
          }
        }
      }
      if (listenerRef != null) {
        try {
          listenerRef.close();
        } catch (Exception ignored) {
        }
        listenerRef = null;
      }
      started = false;
    } finally {
      lock.unlock();
    }
  }

  /** Return the current set of GC events in the in-memory log. */
  public List<GcEvent> getLogs() {
    return PROCESSOR.getLogs();
  }
}
