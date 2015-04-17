/**
 * Copyright 2015 Netflix, Inc.
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
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SparkValueFunctionTest {

  private final SparkValueFunction f = SparkValueFunction.fromConfig(ConfigFactory.load());

  @Test
  public void executorName() {
    final String name = "app-20150309231421-0000.0.executor.filesystem.file.largeRead_ops";
    Assert.assertEquals(42.0, f.convert(name, 42.0), 1e-12);
  }

  @Test
  public void driverName() {
    final String name = "app-20150309231421-0000.driver.BlockManager.disk.diskSpaceUsed_MB";
    Assert.assertEquals(42.0 * 1e6, f.convert(name, 42.0), 1e-12);
  }

  @Test
  public void applicationName2() {
    final String name = "application.SubscriptionEnded.1429226958083.runtime_ms";
    Assert.assertEquals(42.0 / 1000.0, f.convert(name, 42.0), 1e-12);
  }

  @Test
  public void workerName() {
    final String name = "worker.memFree_MB";
    Assert.assertEquals(42.0 * 1e6, f.convert(name, 42.0), 1e-12);
  }
}
