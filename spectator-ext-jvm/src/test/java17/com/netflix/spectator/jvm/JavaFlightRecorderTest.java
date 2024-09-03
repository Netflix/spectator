/*
 * Copyright 2014-2024 Netflix, Inc.
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

package com.netflix.spectator.jvm;

import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Registry;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class JavaFlightRecorderTest {

  @SuppressFBWarnings
  public static volatile Object obj;

  @Test
  public void isSupported() {
    assertTrue(JavaFlightRecorder.isSupported());
  }

  @Test
  public void checkDefaultMeasures() throws Exception {
    Registry registry = new DefaultRegistry();
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try (var closable = JavaFlightRecorder.monitorDefaultEvents(registry, executor)) {
      // allocate rapidly to trigger a GC, black holing using the approach from
      // https://github.com/openjdk/jdk/blob/master/test/hotspot/jtreg/gc/testlibrary/Allocation.java
      for (int i = 0; i < 100; i++) {
        obj = new byte[4 * 1024 * 1024];
        obj = null;
      }
      Thread.sleep(6000);
    }
    executor.shutdownNow();

    Map<Id, Measurement> measures = registry.measurements()
      .collect(Collectors.toMap(Measurement::id, m -> m));

    Measurement classesLoaded = measures.get(Id.create("jvm.classloading.classesLoaded"));
    Measurement classesUnloaded = measures.get(Id.create("jvm.classloading.classesUnloaded"));
    assertNotEquals(null, classesLoaded);
    assertNotEquals(null, classesUnloaded);
    assertTrue(classesLoaded.value() > 3000 && classesLoaded.value() < 4000);
    assertEquals(0, classesUnloaded.value());

    Measurement compilationTime = measures.get(Id.create("jvm.compilation.compilationTime"));
    assertNotEquals(null, compilationTime);

    Measurement nonDaemonThreadCount = measures.get(Id.create("jvm.thread.threadCount").withTag("id", "non-daemon"));
    Measurement daemonThreadCount = measures.get(Id.create("jvm.thread.threadCount").withTag("id", "daemon"));
    Measurement threadsStarted = measures.get(Id.create("jvm.thread.threadsStarted"));
    assertNotEquals(null, nonDaemonThreadCount);
    assertEquals(5, nonDaemonThreadCount.value());
    assertNotEquals(null, daemonThreadCount);
    assertEquals(7, daemonThreadCount.value());
    assertNotEquals(null, threadsStarted);
    assertEquals(12, threadsStarted.value());
  }

}
