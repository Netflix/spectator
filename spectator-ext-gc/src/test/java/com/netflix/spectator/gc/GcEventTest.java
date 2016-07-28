/**
 * Copyright 2016 Netflix, Inc.
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
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;

@RunWith(JUnit4.class)
public class GcEventTest {

  @Test
  public void toStringSizes() {
    // Try to ensure that at least one GC event has occurred
    System.gc();

    // Loop through and get a GcInfo
    for (GarbageCollectorMXBean mbean : ManagementFactory.getGarbageCollectorMXBeans()) {
      GcInfo gcInfo = ((com.sun.management.GarbageCollectorMXBean) mbean).getLastGcInfo();
      if (gcInfo != null) {
        GarbageCollectionNotificationInfo info = new GarbageCollectionNotificationInfo(
            mbean.getName(),
            "Action",
            "Allocation Failure",
            gcInfo);
        GcEvent event = new GcEvent(info, 0L);

        final String eventStr = event.toString();
        Assert.assertTrue(eventStr.contains("cause=[Allocation Failure]"));

        // TODO: need to find a better way to create a fake GcInfo object for tests
        final long max = HelperFunctions.getTotalMaxUsage(gcInfo.getMemoryUsageAfterGc());
        if (max > (1L << 30)) {
          Assert.assertTrue(eventStr.contains("GiB"));
        } else if (max > (1L << 20)) {
          Assert.assertTrue(eventStr.contains("MiB"));
        } else {
          Assert.assertTrue(eventStr.contains("KiB"));
        }
      }
    }
  }
}
