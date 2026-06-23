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

/** Metric name constants for JVM GC metrics. */
final class GcMetricNames {

  /** Max size of old generation memory pool. */
  static final String MAX_DATA_SIZE = "jvm.gc.maxDataSize";

  /** Size of old generation memory pool after a full GC. */
  static final String LIVE_DATA_SIZE = "jvm.gc.liveDataSize";

  /** Incremented for any positive increases in the size of the old generation pool before GC to after GC. */
  static final String PROMOTION_RATE = "jvm.gc.promotionRate";

  /** Incremented for the increase in the size of the young generation pool after one GC to before the next. */
  static final String ALLOCATION_RATE = "jvm.gc.allocationRate";

  /** Incremented for any positive increases in the size of the survivor pool before GC to after GC. */
  static final String SURVIVOR_RATE = "jvm.gc.survivorRate";

  /** Pause time due to a GC event. */
  static final String PAUSE_TIME = "jvm.gc.pause";

  /** Time spent in concurrent phases of GC. */
  static final String CONCURRENT_PHASE_TIME = "jvm.gc.concurrentPhaseTime";

  private GcMetricNames() {
  }
}
