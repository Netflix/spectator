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

import com.sun.management.GarbageCollectionNotificationInfo;
import com.sun.management.GcInfo;

import java.time.Instant;
import java.util.Comparator;

/**
 * Metadata about a garbage collection event.
 */
@SuppressWarnings("PMD.DataClass")
public class GcEvent {

  private static final long ONE_KIBIBYTE = 1 << 10;
  private static final long ONE_MEBIBYTE = 1 << 20;
  private static final long ONE_GIBIBYTE = 1 << 30;

  private final String name;
  private final GarbageCollectionNotificationInfo info;
  private final GcType type;
  private final long startTime;

  /**
   * Create a new instance.
   *
   * @param info
   *     The info object from the notification emitter on the
   *     {@link java.lang.management.GarbageCollectorMXBean}.
   * @param startTime
   *     Start time in milliseconds since the epoch. Note the info object has a start time relative
   *     to the time the jvm process was started.
   */
  public GcEvent(GarbageCollectionNotificationInfo info, long startTime) {
    this.name = info.getGcName();
    this.info = info;
    this.type = HelperFunctions.getGcType(name);
    this.startTime = startTime;
  }

  /** Type of GC event that occurred. */
  public GcType getType() {
    return type;
  }

  /** Name of the collector for the event. */
  public String getName() {
    return name;
  }

  /** Start time in milliseconds since the epoch. */
  public long getStartTime() {
    return startTime;
  }

  /**
   * Info object from the {@link java.lang.management.GarbageCollectorMXBean} notification
   * emitter.
   */
  public GarbageCollectionNotificationInfo getInfo() {
    return info;
  }

  @Override
  public String toString() {
    final GcInfo gcInfo = info.getGcInfo();
    final long totalBefore = HelperFunctions.getTotalUsage(gcInfo.getMemoryUsageBeforeGc());
    final long totalAfter = HelperFunctions.getTotalUsage(gcInfo.getMemoryUsageAfterGc());
    final long max = HelperFunctions.getTotalMaxUsage(gcInfo.getMemoryUsageAfterGc());

    String unit = "KiB";
    double cnv = ONE_KIBIBYTE;
    if (max > ONE_GIBIBYTE) {
      unit = "GiB";
      cnv = ONE_GIBIBYTE;
    } else if (max > ONE_MEBIBYTE) {
      unit = "MiB";
      cnv = ONE_MEBIBYTE;
    }

    String change = String.format(
      "%.1f%s => %.1f%s / %.1f%s",
      totalBefore / cnv, unit,
      totalAfter / cnv, unit,
      max / cnv, unit);
    String percentChange = String.format(
      "%.1f%% => %.1f%%", 100.0 * totalBefore / max, 100.0 * totalAfter / max);

    final Instant instant = Instant.ofEpochMilli(startTime);
    return type.toString() + ": "
        + name + ", id=" + gcInfo.getId() + ", at=" + instant.toString()
        + ", duration=" + gcInfo.getDuration() + "ms" + ", cause=[" + info.getGcCause() + "]"
        + ", " + change + " (" + percentChange + ")";
  }

  /** Order events from oldest to newest. */
  public static final Comparator<GcEvent> TIME_ORDER = Comparator.comparingLong(GcEvent::getStartTime);

  /** Order events from newest to oldest. */
  public static final Comparator<GcEvent> REVERSE_TIME_ORDER = TIME_ORDER.reversed();
}
