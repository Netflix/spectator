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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

public class GcLoggerTest {

  @Test
  public void startAndStopDoNotThrow() {
    GcLogger logger = new GcLogger();
    logger.start(null);
    logger.stop();
  }

  @Test
  public void getLogsReturnsListAfterGc() throws Exception {
    GcLogger logger = new GcLogger();
    logger.start(null);
    try {
      System.gc();
      long deadline = System.currentTimeMillis() + 5000L;
      while (logger.getLogs().isEmpty() && System.currentTimeMillis() < deadline) {
        Thread.sleep(10);
      }
      Assertions.assertFalse(logger.getLogs().isEmpty(), "expected at least one GC event");
    } finally {
      logger.stop();
    }
  }

  @Test
  public void listenerIsCalledOnGcEvent() throws Exception {
    AtomicBoolean called = new AtomicBoolean(false);
    GcLogger logger = new GcLogger();
    logger.start(event -> called.set(true));
    try {
      System.gc();
      long deadline = System.currentTimeMillis() + 5000L;
      while (!called.get() && System.currentTimeMillis() < deadline) {
        Thread.sleep(10);
      }
      Assertions.assertTrue(called.get(), "expected listener to be called");
    } finally {
      logger.stop();
    }
  }
}
