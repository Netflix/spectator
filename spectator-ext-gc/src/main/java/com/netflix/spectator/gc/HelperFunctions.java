/*
 * Copyright 2014-2019 Netflix, Inc.
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

import com.sun.management.GcInfo;

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
    return name.endsWith("Old Gen")
        || name.endsWith("Tenured Gen")
        || "Shenandoah".equals(name)
        || "ZHeap".equals(name);
  }

  /** Returns true if memory pool name matches an young generation pool. */
  static boolean isYoungGenPool(String name) {
    return name.endsWith("Eden Space");
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
}
