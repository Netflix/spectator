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
package com.netflix.spectator.spark;

import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SparkValueFunctionTest {

  private final SparkValueFunction f = SparkValueFunction.fromConfig(ConfigFactory.load());

  @Test
  public void executorName() {
    final String name = "app-20150309231421-0000.0.executor.filesystem.file.largeRead_ops";
    Assertions.assertEquals(42.0, f.convert(name, 42.0), 1e-12);
  }

  @Test
  public void driverName() {
    final String name = "app-20150309231421-0000.driver.BlockManager.disk.diskSpaceUsed_MB";
    Assertions.assertEquals(42.0 * 1e6, f.convert(name, 42.0), 1e-12);
  }

  @Test
  public void driverStreamingTime() {
    final String name = "97278898-4bd4-49c2-9889-aa5f969a7816-0129.driver.HdfsWordCount.StreamingMetrics.streaming.lastCompletedBatch_processingEndTime";
    Assertions.assertEquals(42.0 / 1000.0, f.convert(name, 42.0), 1e-12);
  }

  @Test
  public void driverStreamingDelay() {
    final String name = "app-20150527224111-0014.<driver>.SubscriptionEnded.StreamingMetrics.streaming.lastReceivedBatch_submissionDelay";
    Assertions.assertEquals(42.0 / 1000.0, f.convert(name, 42.0), 1e-12);
  }

  @Test
  public void driverStreamingDelayAbnormal() {
    final String name = "app-20150527224111-0014.<driver>.SubscriptionEnded.StreamingMetrics.streaming.lastReceivedBatch_submissionDelay";
    Assertions.assertEquals(Double.NaN, f.convert(name, -1.0), 1e-12);
  }

}
