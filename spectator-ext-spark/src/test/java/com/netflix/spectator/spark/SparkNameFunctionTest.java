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

import com.netflix.spectator.api.DefaultId;
import com.netflix.spectator.api.Id;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SparkNameFunctionTest {

  @Test
  public void executorName() {
    final String name = "app-20150309231421-0000.0.executor.filesystem.file.largeRead_ops";
    final Id expected = new DefaultId("spark.filesystem.file.largeRead_ops")
        .withTag("role", "executor")
        .withTag("appId", "app-20150309231421-0000")
        .withTag("executorId", "0");
    final SparkNameFunction f = new SparkNameFunction();
    Assert.assertEquals(expected, f.apply(name));
  }

  @Test
  public void driverName() {
    final String name = "app-20150309231421-0000.driver.BlockManager.disk.diskSpaceUsed_MB";
    final Id expected = new DefaultId("spark.BlockManager.disk.diskSpaceUsed_MB")
        .withTag("role", "driver")
        .withTag("appId", "app-20150309231421-0000");
    final SparkNameFunction f = new SparkNameFunction();
    Assert.assertEquals(expected, f.apply(name));
  }

  @Test
  public void driverName2() {
    final String name = "app-20150309231421-0000.driver.DAGScheduler.job.activeJobs";
    final Id expected = new DefaultId("spark.DAGScheduler.job.activeJobs")
        .withTag("role", "driver")
        .withTag("appId", "app-20150309231421-0000");
    final SparkNameFunction f = new SparkNameFunction();
    Assert.assertEquals(expected, f.apply(name));
  }

  @Test
  public void applicationName() {
    final String name = "application.Spark shell.1425968061869.cores";
    final SparkNameFunction f = new SparkNameFunction();
    Assert.assertNull(f.apply(name));
    //final Id expected = new DefaultId("spark.cores")
    //    .withTag("role", "application")
    //    .withTag("jobId", "Spark shell.1425968061869");
    //final SparkNameFunction f = new SparkNameFunction();
    //Assert.assertEquals(expected, f.apply(name));
  }

  @Test
  public void masterName() {
    final String name = "master.apps";
    final Id expected = new DefaultId("spark.apps")
        .withTag("role", "master");
    final SparkNameFunction f = new SparkNameFunction();
    Assert.assertEquals(expected, f.apply(name));
  }

  @Test
  public void workerName() {
    final String name = "worker.memFree_MB";
    final Id expected = new DefaultId("spark.memFree_MB")
        .withTag("role", "worker");
    final SparkNameFunction f = new SparkNameFunction();
    Assert.assertEquals(expected, f.apply(name));
  }

}
