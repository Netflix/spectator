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

import com.netflix.spectator.api.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.ListenerNotFoundException;
import javax.management.NotificationEmitter;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Factory for starting registry-aware GC logging. Unlike {@link GcLogger}, the registry is
 * injected, allowing metrics to flow to any {@link Registry} instance rather than the global one.
 *
 * <p>At most one active handle per registry is permitted; a second call to
 * {@link #start(Registry)} for the same registry throws {@link IllegalStateException} until the
 * existing handle is closed.
 */
public final class GcLoggerFactory {

  private static final ConcurrentHashMap<Registry, HandleImpl> ACTIVE =
      new ConcurrentHashMap<>();

  private GcLoggerFactory() {
  }

  /**
   * Start GC logging for the given registry.
   *
   * @param registry the registry to record metrics into
   * @return a handle that can be used to add listeners, query logs, and stop GC logging
   * @throws IllegalStateException if GC logging is already active for this registry
   */
  public static Handle start(Registry registry) {
    final AtomicBoolean created = new AtomicBoolean(false);
    HandleImpl handle = ACTIVE.computeIfAbsent(registry, key -> {
      created.set(true);
      return new HandleImpl(key);
    });
    if (!created.get()) {
      throw new IllegalStateException(
          "GC logging is already active for this registry; close the existing handle first");
    }
    try {
      handle.registerListeners();
    } catch (Exception e) {
      handle.close();
      throw e;
    }
    return handle;
  }

  /**
   * Handle returned by {@link GcLoggerFactory#start(Registry)}. Closing the handle stops GC
   * logging and allows a new handle to be started for the same registry.
   */
  public interface Handle extends AutoCloseable {

    /**
     * Add a listener that will be called after each GC event. Returns an {@link AutoCloseable}
     * that removes only this listener when closed.
     */
    AutoCloseable addListener(GcEventListener listener);

    /** Returns recent GC events from the in-memory circular buffer, sorted newest-first. */
    List<GcEvent> getLogs();

    /** Stops GC collection and deregisters JMX listeners. */
    @Override
    void close();
  }

  private static final class HandleImpl implements Handle {

    private static final Logger LOGGER = LoggerFactory.getLogger(HandleImpl.class);

    private final Registry registry;
    private final GcEventProcessor processor;

    HandleImpl(Registry registry) {
      this.registry = registry;
      this.processor = new GcEventProcessor(registry);
    }

    void registerListeners() {
      for (GarbageCollectorMXBean mbean : ManagementFactory.getGarbageCollectorMXBeans()) {
        if (mbean instanceof NotificationEmitter) {
          ((NotificationEmitter) mbean).addNotificationListener(
              processor.getNotificationListener(), null, null);
        }
      }
    }

    @Override
    public AutoCloseable addListener(GcEventListener listener) {
      return processor.addRemovableListener(listener);
    }

    @Override
    public List<GcEvent> getLogs() {
      return processor.getLogs();
    }

    @Override
    public void close() {
      ACTIVE.remove(registry, this);
      for (GarbageCollectorMXBean mbean : ManagementFactory.getGarbageCollectorMXBeans()) {
        if (mbean instanceof NotificationEmitter) {
          try {
            ((NotificationEmitter) mbean).removeNotificationListener(processor.getNotificationListener());
          } catch (ListenerNotFoundException e) {
            LOGGER.warn("could not remove gc listener", e);
          }
        }
      }
    }
  }
}
