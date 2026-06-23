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

import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Registry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

public class GcLoggerFactoryTest {

  private static void waitForEvent(GcLoggerFactory.Handle handle) throws Exception {
    System.gc();
    long deadline = System.currentTimeMillis() + 5000L;
    while (handle.getLogs().isEmpty() && System.currentTimeMillis() < deadline) {
      Thread.sleep(10);
    }
  }

  @Test
  public void startAndCloseDoNotThrow() {
    Registry registry = new DefaultRegistry();
    GcLoggerFactory.Handle handle = GcLoggerFactory.start(registry);
    handle.close();
  }

  @Test
  public void startTwiceThrows() {
    Registry registry = new DefaultRegistry();
    GcLoggerFactory.Handle handle = GcLoggerFactory.start(registry);
    try {
      Assertions.assertThrows(IllegalStateException.class,
          () -> GcLoggerFactory.start(registry));
    } finally {
      handle.close();
    }
  }

  @Test
  public void closeAllowsRestart() {
    Registry registry = new DefaultRegistry();
    GcLoggerFactory.Handle handle = GcLoggerFactory.start(registry);
    handle.close();
    GcLoggerFactory.Handle handle2 = GcLoggerFactory.start(registry);
    handle2.close();
  }

  @Test
  public void getLogsReturnsEventsAfterGc() throws Exception {
    Registry registry = new DefaultRegistry();
    GcLoggerFactory.Handle handle = GcLoggerFactory.start(registry);
    try {
      waitForEvent(handle);
      Assertions.assertFalse(handle.getLogs().isEmpty(), "expected at least one GC event");
    } finally {
      handle.close();
    }
  }

  @Test
  public void listenerIsCalledOnGcEvent() throws Exception {
    AtomicInteger count = new AtomicInteger(0);
    Registry registry = new DefaultRegistry();
    GcLoggerFactory.Handle handle = GcLoggerFactory.start(registry);
    try {
      handle.addListener(event -> count.incrementAndGet());
      waitForEvent(handle);
      Assertions.assertTrue(count.get() > 0, "expected listener to be called");
    } finally {
      handle.close();
    }
  }

  @Test
  public void removedListenerIsNotCalled() throws Exception {
    AtomicInteger count = new AtomicInteger(0);
    Registry registry = new DefaultRegistry();
    GcLoggerFactory.Handle handle = GcLoggerFactory.start(registry);
    try {
      AutoCloseable listenerHandle = handle.addListener(event -> count.incrementAndGet());
      listenerHandle.close();
      waitForEvent(handle);
      Assertions.assertEquals(0, count.get(), "removed listener should not be called");
    } finally {
      handle.close();
    }
  }

  @Test
  public void multipleListenersAllFire() throws Exception {
    AtomicInteger count1 = new AtomicInteger(0);
    AtomicInteger count2 = new AtomicInteger(0);
    Registry registry = new DefaultRegistry();
    GcLoggerFactory.Handle handle = GcLoggerFactory.start(registry);
    try {
      handle.addListener(event -> count1.incrementAndGet());
      handle.addListener(event -> count2.incrementAndGet());
      waitForEvent(handle);
      Assertions.assertTrue(count1.get() > 0, "expected first listener to be called");
      Assertions.assertTrue(count2.get() > 0, "expected second listener to be called");
    } finally {
      handle.close();
    }
  }

  @Test
  public void independentRegistriesHaveIndependentHandles() {
    Registry registry1 = new DefaultRegistry();
    Registry registry2 = new DefaultRegistry();
    GcLoggerFactory.Handle handle1 = GcLoggerFactory.start(registry1);
    GcLoggerFactory.Handle handle2 = GcLoggerFactory.start(registry2);
    handle1.close();
    handle2.close();
  }

  @Test
  public void afterCloseLogsNoLongerUpdate() throws Exception {
    Registry registry = new DefaultRegistry();
    GcLoggerFactory.Handle handle = GcLoggerFactory.start(registry);
    waitForEvent(handle);
    int countBeforeClose = handle.getLogs().size();
    handle.close();

    // Force another GC; the closed handle should not accumulate more events.
    System.gc();
    Thread.sleep(200);
    Assertions.assertEquals(countBeforeClose, handle.getLogs().size(),
        "closed handle should not accumulate new events");
  }

  @Test
  public void metricsAreRecordedToInjectedRegistry() throws Exception {
    Registry registry = new DefaultRegistry();
    GcLoggerFactory.Handle handle = GcLoggerFactory.start(registry);
    try {
      waitForEvent(handle);
      long pauseTimerCount = registry.stream()
          .filter(m -> m.id().name().equals(GcMetricNames.PAUSE_TIME)
              || m.id().name().equals(GcMetricNames.CONCURRENT_PHASE_TIME))
          .count();
      Assertions.assertTrue(pauseTimerCount > 0,
          "expected at least one pause or concurrent-phase timer in the injected registry");
    } finally {
      handle.close();
    }
  }
}
