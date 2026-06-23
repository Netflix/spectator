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

import com.sun.management.GarbageCollectionNotificationInfo;
import com.sun.management.GcInfo;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Utility functions for GC. */
final class HelperFunctions {

  private static final Map<String, GcType> KNOWN_COLLECTOR_NAMES = knownCollectors();

  private HelperFunctions() {
  }

  private static Map<String, GcType> knownCollectors() {
    Map<String, GcType> m = new HashMap<>();
    m.put("ConcurrentMarkSweep",  GcType.OLD);
    m.put("Copy",                 GcType.YOUNG);
    m.put("G1 Old Generation",    GcType.OLD);
    m.put("G1 Young Generation",  GcType.YOUNG);
    m.put("MarkSweepCompact",     GcType.OLD);
    m.put("PS MarkSweep",         GcType.OLD);
    m.put("PS Scavenge",          GcType.YOUNG);
    m.put("ParNew",               GcType.YOUNG);
    m.put("ZGC",                  GcType.OLD);
    m.put("ZGC Cycles",           GcType.OLD);
    m.put("ZGC Pauses",           GcType.OLD);
    m.put("ZGC Minor Cycles",     GcType.YOUNG);
    m.put("ZGC Minor Pauses",     GcType.YOUNG);
    m.put("ZGC Major Cycles",     GcType.OLD);
    m.put("ZGC Major Pauses",     GcType.OLD);
    m.put("Shenandoah Cycles",    GcType.OLD);
    m.put("Shenandoah Pauses",    GcType.OLD);
    return Collections.unmodifiableMap(m);
  }

  /** Determine the type, old or young, based on the name of the collector. */
  static GcType getGcType(String name) {
    GcType t = KNOWN_COLLECTOR_NAMES.get(name);
    return (t == null) ? GcType.UNKNOWN : t;
  }

  /** Return true if it is an old GC type. */
  static boolean isOldGcType(String name) {
    return getGcType(name) == GcType.OLD;
  }

  /** Returns true if memory pool name matches an old generation pool. */
  static boolean isOldGenPool(String name) {
    return name.contains("Old Gen")
        || name.endsWith("Tenured Gen")
        || "Shenandoah".equals(name)
        || "ZHeap".equals(name);
  }

  /** Returns true if memory pool name matches an young generation pool. */
  static boolean isYoungGenPool(String name) {
    return name.contains("Young Gen")
        || name.endsWith("Eden Space")
        || "Shenandoah".equals(name)
        || "ZHeap".equals(name);
  }

  static boolean isSurvivorPool(String name) {
    return name.endsWith("Survivor Space");
  }

  /** Compute the total usage across all pools. */
  static long getTotalUsage(Map<String, MemoryUsage> usages) {
    long sum = 0L;
    for (Map.Entry<String, MemoryUsage> e : usages.entrySet()) {
      sum += e.getValue().getUsed();
    }
    return sum;
  }

  /** Compute the max usage across all pools. */
  static long getTotalMaxUsage(Map<String, MemoryUsage> usages) {
    long sum = 0L;
    for (Map.Entry<String, MemoryUsage> e : usages.entrySet()) {
      long max = e.getValue().getMax();
      if (max > 0) {
        sum += e.getValue().getMax();
      }
    }
    return sum;
  }

  /** Compute the amount of data promoted during a GC event. */
  static long getPromotionSize(GcInfo info) {
    long totalBefore = getTotalUsage(info.getMemoryUsageBeforeGc());
    long totalAfter = getTotalUsage(info.getMemoryUsageAfterGc());
    return totalAfter - totalBefore;
  }

  /** Detect the young gen, survivor, and old gen pool names from the JVM memory pools. */
  static PoolNames detectPoolNames() {
    String youngGenPoolName = null;
    String survivorPoolName = null;
    String oldGenPoolName = null;
    for (MemoryPoolMXBean mbean : ManagementFactory.getMemoryPoolMXBeans()) {
      String name = mbean.getName();
      // For non-generational collectors the young and old gen pool names will be the same
      if (isYoungGenPool(name)) {
        youngGenPoolName = name;
      }
      if (isSurvivorPool(name)) {
        survivorPoolName = name;
      }
      if (isOldGenPool(name)) {
        oldGenPoolName = name;
      }
    }
    return new PoolNames(youngGenPoolName, survivorPoolName, oldGenPoolName);
  }

  /**
   * Returns true if the GC event represents a concurrent phase rather than a stop-the-world
   * pause.
   */
  static boolean isConcurrentPhase(GarbageCollectionNotificationInfo info) {
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
        || info.getGcName().endsWith(" Cycles");        // Shenandoah, ZGC
  }

  /** Holds the detected pool names for young gen, survivor, and old gen. */
  static final class PoolNames {
    private final String youngGen;
    private final String survivor;
    private final String oldGen;

    PoolNames(String youngGen, String survivor, String oldGen) {
      this.youngGen = youngGen;
      this.survivor = survivor;
      this.oldGen = oldGen;
    }

    /** Returns the young generation pool name, or null if not detected. */
    String youngGen() {
      return youngGen;
    }

    /** Returns the survivor pool name, or null if not detected. */
    String survivor() {
      return survivor;
    }

    /** Returns the old generation pool name, or null if not detected. */
    String oldGen() {
      return oldGen;
    }
  }

}
