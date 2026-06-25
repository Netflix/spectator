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
import com.netflix.spectator.api.Meter;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Spectator;
import com.netflix.spectator.api.Timer;
import com.sun.management.GarbageCollectionNotificationInfo;
import com.sun.management.GcInfo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GcLoggerTest {

  private static final List<String> GC_METER_NAMES = Arrays.asList(
      "jvm.gc.maxDataSize",
      "jvm.gc.liveDataSize",
      "jvm.gc.promotionRate",
      "jvm.gc.allocationRate",
      "jvm.gc.survivorRate");

  private Set<String> meterNames(Registry registry) {
    Set<String> names = new HashSet<>();
    for (Meter m : registry) {
      names.add(m.id().name());
    }
    return names;
  }

  private long totalTimerCount(Registry registry) {
    long total = 0L;
    for (Meter m : registry) {
      if (m instanceof Timer) {
        total += ((Timer) m).count();
      }
    }
    return total;
  }

  /** Build a synthetic notification from a real GcInfo, or null if none is available yet. */
  private GarbageCollectionNotificationInfo lastGcNotification() {
    System.gc();
    for (GarbageCollectorMXBean mbean : ManagementFactory.getGarbageCollectorMXBeans()) {
      GcInfo gcInfo = ((com.sun.management.GarbageCollectorMXBean) mbean).getLastGcInfo();
      if (gcInfo != null) {
        // The gc name must match a bean known to the logger so the event is buffered.
        return new GarbageCollectionNotificationInfo(
            mbean.getName(), "end of minor GC", "Allocation Failure", gcInfo);
      }
    }
    return null;
  }

  @Test
  public void reportsToProvidedRegistry() {
    Set<String> globalBefore = meterNames(Spectator.globalRegistry());

    Registry registry = new DefaultRegistry();
    new GcLogger(registry);

    // The gauges and counters are registered eagerly in the constructor, so they should be
    // present on the supplied registry without any GC events having occurred.
    Set<String> names = meterNames(registry);
    for (String name : GC_METER_NAMES) {
      Assertions.assertTrue(names.contains(name), name + " should be on the provided registry");
    }

    // ...and constructing against a custom registry must not register them on the global
    // registry. Comparing presence before/after keeps this robust to anything already
    // registered globally elsewhere in the suite.
    Set<String> globalAfter = meterNames(Spectator.globalRegistry());
    for (String name : GC_METER_NAMES) {
      Assertions.assertEquals(globalBefore.contains(name), globalAfter.contains(name),
          name + " must not be registered on the global registry by GcLogger(Registry)");
    }
  }

  @Test
  public void multipleRegistriesDoNotInterfere() {
    GarbageCollectionNotificationInfo info = lastGcNotification();
    Assumptions.assumeTrue(info != null, "no GcInfo available to build a synthetic event");

    Registry r1 = new DefaultRegistry();
    Registry r2 = new DefaultRegistry();
    GcLogger l1 = new GcLogger(r1);
    new GcLogger(r2);

    // Drive an event through the first logger only. processGcEvent always records one pause
    // (or concurrent-phase) timer per event.
    l1.processGcEvent(info);

    // The event is recorded against the first logger's registry...
    Assertions.assertEquals(1L, totalTimerCount(r1));
    // ...and the second registry, whose logger never processed an event, is untouched. With the
    // old shared static fields this would have leaked across instances.
    Assertions.assertEquals(0L, totalTimerCount(r2));
  }

  @Test
  public void stopBeforeStartThrows() {
    GcLogger logger = new GcLogger(new DefaultRegistry());
    Assertions.assertThrows(IllegalStateException.class, logger::stop);
  }

  @Test
  public void startStopLifecycle() {
    GcLogger logger = new GcLogger(new DefaultRegistry());
    logger.start(null);
    try {
      // Second start is a no-op and must not throw.
      logger.start(null);
    } finally {
      // Always remove the listener from the shared GC MXBeans, even if an assertion fails,
      // so a leaked listener cannot interfere with other tests in the same JVM.
      logger.stop();
    }
  }

  @Test
  public void monitorTiesLifecycleToRegistry() {
    Registry r = new DefaultRegistry();
    AutoCloseable handle = GcLogger.monitor(r);
    Assertions.assertNotNull(handle);
    // monitor() starts the logger and registers a cleanup resource with the registry.
    Assertions.assertFalse(r.state().isEmpty());

    // Closing the registry stops the logger and clears the registry state.
    r.close();
    Assertions.assertTrue(r.state().isEmpty());
  }

  @Test
  public void monitorHandleStopsLogger() throws Exception {
    Registry r = new DefaultRegistry();
    AutoCloseable handle = GcLogger.monitor(r);
    int before = r.state().size();

    // Closing the handle stops collection and removes the logger's cleanup resource. The gauge
    // bookkeeping for the metrics remains until the registry itself is closed.
    handle.close();
    Assertions.assertEquals(before - 1, r.state().size());

    // Closing again is a no-op and must not throw.
    handle.close();
    Assertions.assertEquals(before - 1, r.state().size());
  }
}
