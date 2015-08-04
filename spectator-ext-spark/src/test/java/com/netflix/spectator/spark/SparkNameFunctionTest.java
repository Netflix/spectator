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
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Id;
import com.typesafe.config.ConfigFactory;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SparkNameFunctionTest {

  private final SparkNameFunction f = SparkNameFunction.fromConfig(ConfigFactory.load(), new DefaultRegistry());

  private void assertEquals(Id expected, Id actual) {
    Assert.assertEquals(((DefaultId) expected).normalize(), ((DefaultId) actual).normalize());
  }

  @Test
  public void executorName() {
    final String name = "app-20150309231421-0000.0.executor.filesystem.file.largeRead_ops";
    final Id expected = new DefaultId("spark.filesystem.file.largeRead_ops")
        .withTag("role", "executor");
        //.withTag("appId", "app-20150309231421-0000")
        //.withTag("executorId", "0");
    assertEquals(expected, f.apply(name));
  }

  @Test
  public void executorName2() {
    final String name = "20150626-185518-1776258826-5050-2845-S1.executor.filesystem.file.largeRead_ops";
    final Id expected = new DefaultId("spark.filesystem.file.largeRead_ops")
        .withTag("role", "executor");
        //.withTag("appId", "20150626-185518-1776258826-5050-2845-S1");
    assertEquals(expected, f.apply(name));
  }

  @Test
  public void executorName3() {
    final String name = "12345.1.3.executor.filesystem.file.largeRead_ops";
    final Id expected = new DefaultId("spark.filesystem.file.largeRead_ops")
        .withTag("role", "executor");
        //.withTag("appId", "12345.1.3");
    assertEquals(expected, f.apply(name));
  }

    @Test
  public void driverName() {
    final String name = "app-20150309231421-0000.driver.BlockManager.disk.diskSpaceUsed_MB";
    final Id expected = new DefaultId("spark.disk.diskSpaceUsed")
        .withTag("role", "driver")
        .withTag("source", "BlockManager");
        //.withTag("appId", "app-20150309231421-0000");
    assertEquals(expected, f.apply(name));
  }

  @Test
  public void driverName2() {
    final String name = "app-20150309231421-0000.driver.DAGScheduler.job.activeJobs";
    final Id expected = new DefaultId("spark.job.activeJobs")
        .withTag("role", "driver")
        .withTag("source", "DAGScheduler");
        //.withTag("appId", "app-20150309231421-0000");
    assertEquals(expected, f.apply(name));
  }

  @Test
  public void driverName3() {
    final String name = "local-1429219722964.<driver>.DAGScheduler.job.activeJobs";
    final Id expected = new DefaultId("spark.job.activeJobs")
        .withTag("role", "driver")
        .withTag("source", "DAGScheduler");
        //.withTag("appId", "local-1429219722964");
    assertEquals(expected, f.apply(name));
  }

  @Test
  public void driverStreamingSimple() {
    final String name = "app-20150527224111-0014.<driver>.SubscriptionEnded.StreamingMetrics.streaming.receivers";
    final Id expected = new DefaultId("spark.streaming.receivers")
        .withTag("role", "driver")
        .withTag("source", "StreamingMetrics");
        //.withTag("appId", "app-20150527224111-0014")
        //.withTag("appName", "SubscriptionEnded");
    assertEquals(expected, f.apply(name));
  }

  @Test
  public void driverStreamingTotal() {
    final String name = "app-20150527224111-0014.<driver>.SubscriptionEnded.StreamingMetrics.streaming.totalCompletedBatches";
    final Id expected = new DefaultId("spark.streaming.totalCompletedBatches")
        .withTag("role", "driver")
        .withTag("source", "StreamingMetrics");
        //.withTag("appId", "app-20150527224111-0014")
        //.withTag("appName", "SubscriptionEnded");
    assertEquals(expected, f.apply(name));
  }

  @Test
  public void driverStreamingDelay() {
    final String name = "app-20150527224111-0014.<driver>.SubscriptionEnded.StreamingMetrics.streaming.lastReceivedBatch_submissionDelay";
    final Id expected = new DefaultId("spark.streaming.lastReceivedBatch_submissionDelay")
        .withTag("role", "driver")
        .withTag("source", "StreamingMetrics");
        //.withTag("appId", "app-20150527224111-0014")
        //.withTag("appName", "SubscriptionEnded");
    assertEquals(expected, f.apply(name));
  }

  @Test
  public void driverStreamingTime() {
    final String name = "app-20150527224111-0014.<driver>.SubscriptionEnded.StreamingMetrics.streaming.lastReceivedBatch_submissionTime";
    Assert.assertNull(f.apply(name));
  }

  @Test
  public void applicationName() {
    final String name = "application.Spark shell.1425968061869.cores";
    final Id expected = new DefaultId("spark.cores")
        .withTag("role", "application")
        .withTag("appName", "Spark shell");
    assertEquals(expected, f.apply(name));
  }

  @Test
  public void applicationName2() {
    final String name = "application.SubscriptionEnded.1429226958083.runtime_ms";
    final Id expected = new DefaultId("spark.runtime")
        .withTag("role", "application")
        .withTag("appName", "SubscriptionEnded");
    assertEquals(expected, f.apply(name));
  }

  @Test
  public void masterName() {
    final String name = "master.apps";
    final Id expected = new DefaultId("spark.apps")
        .withTag("role", "master");
    assertEquals(expected, f.apply(name));
  }

  @Test
  public void workerName() {
    final String name = "worker.memFree_MB";
    final Id expected = new DefaultId("spark.memFree")
        .withTag("role", "worker");
    assertEquals(expected, f.apply(name));
  }

}
