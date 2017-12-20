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

import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Utils;
import com.typesafe.config.ConfigFactory;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RunWith(JUnit4.class)
public class SparkNameFunctionTest {

  private final Registry registry = new DefaultRegistry();
  private final SparkNameFunction f = SparkNameFunction.fromConfig(ConfigFactory.load(), registry);

  private void assertEquals(Id expected, Id actual) {
    Assert.assertEquals(Utils.normalize(expected), Utils.normalize(actual));
  }

  //EXECUTOR
  @Test
  public void executorMetric() {
    final String name = "97278898-4bd4-49c2-9889-aa5f969a7816-0013.97278898-4bd4-49c2-9889-aa5f969a7816-S1/2.executor.filesystem.file.largeRead_ops";
    final Id expected = registry.createId("spark.executor.filesystem.file.largeRead_ops")
        .withTag("appId", "97278898-4bd4-49c2-9889-aa5f969a7816-0013")
        .withTag("agentId","97278898-4bd4-49c2-9889-aa5f969a7816-S1")
        .withTag("executorId", "2")
        .withTag("role", "executor");
    assertEquals(expected, f.apply(name));
  }

  @Test
  public void executorMetric20() {
    final String name = "97278898-4bd4-49c2-9889-aa5f969a7816-0013.2.executor.filesystem.file.largeRead_ops";
    final Id expected = registry.createId("spark.executor.filesystem.file.largeRead_ops")
        .withTag("appId", "97278898-4bd4-49c2-9889-aa5f969a7816-0013")
        .withTag("executorId", "2")
        .withTag("role", "executor");
    assertEquals(expected, f.apply(name));
  }

  @Test
  public void executorJvmMetric() {
    final String name = "97278898-4bd4-49c2-9889-aa5f969a7816-0013.97278898-4bd4-49c2-9889-aa5f969a7816-S1/2.jvm.heap.committed";
    final Id expected = registry.createId("spark.jvm.heap.committed")
        .withTag("appId", "97278898-4bd4-49c2-9889-aa5f969a7816-0013")
        .withTag("agentId","97278898-4bd4-49c2-9889-aa5f969a7816-S1")
        .withTag("executorId", "2")
        .withTag("role", "executor");
    assertEquals(expected, f.apply(name));
  }

  @Test
  public void executorJvmMetric20() {
    final String name = "97278898-4bd4-49c2-9889-aa5f969a7816-0013.2.jvm.heap.committed";
    final Id expected = registry.createId("spark.jvm.heap.committed")
        .withTag("appId", "97278898-4bd4-49c2-9889-aa5f969a7816-0013")
        .withTag("executorId", "2")
        .withTag("role", "executor");
    assertEquals(expected, f.apply(name));
  }

  @Test
  public void executorCodeGenerator() {
    final String name = "97278898-4bd4-49c2-9889-aa5f969a7816-0013.97278898-4bd4-49c2-9889-aa5f969a7816-S1/2.CodeGenerator.compilationTime";
    final Id expected = registry.createId("spark.CodeGenerator.compilationTime")
        .withTag("appId", "97278898-4bd4-49c2-9889-aa5f969a7816-0013")
        .withTag("agentId","97278898-4bd4-49c2-9889-aa5f969a7816-S1")
        .withTag("executorId", "2")
        .withTag("role", "executor");
    assertEquals(expected, f.apply(name));
  }

  @Test
  public void executorCodeGenerator20() {
    final String name = "97278898-4bd4-49c2-9889-aa5f969a7816-0013.2.CodeGenerator.compilationTime";
    final Id expected = registry.createId("spark.CodeGenerator.compilationTime")
        .withTag("appId", "97278898-4bd4-49c2-9889-aa5f969a7816-0013")
        .withTag("executorId", "2")
        .withTag("role", "executor");
    assertEquals(expected, f.apply(name));
  }

  // DRIVER
  @Test
  public void driverMetric() {
    final String name = "97278898-4bd4-49c2-9889-aa5f969a7816-0013.driver.BlockManager.disk.diskSpaceUsed_MB";
    final Id expected = registry.createId("spark.BlockManager.disk.diskSpaceUsed")  // Trailing _MB removed
        .withTag("appId", "97278898-4bd4-49c2-9889-aa5f969a7816-0013")
        .withTag("role", "driver");
    assertEquals(expected, f.apply(name));
  }

  @Test
  public void driverMetricNoUnits() {
    final String name = "97278898-4bd4-49c2-9889-aa5f969a7816-0013.driver.DAGScheduler.messageProcessingTime";
    final Id expected = registry.createId("spark.DAGScheduler.messageProcessingTime")
        .withTag("appId", "97278898-4bd4-49c2-9889-aa5f969a7816-0013")
        .withTag("role", "driver");
    assertEquals(expected, f.apply(name));
  }

  @Test
  public void driverJvmMetric() {
    final String name = "97278898-4bd4-49c2-9889-aa5f969a7816-0013.driver.jvm.heap.committed";
    final Id expected = registry.createId("spark.jvm.heap.committed")
        .withTag("appId", "97278898-4bd4-49c2-9889-aa5f969a7816-0013")
        .withTag("role", "driver");
    assertEquals(expected, f.apply(name));
  }

  // Streaming
  @Test
  public void driverStreamingSimple() {
    final String name = "97278898-4bd4-49c2-9889-aa5f969a7816-0013.driver.HdfsWordCount.StreamingMetrics.streaming.lastCompletedBatch_processingDelay";
    final Id expected = registry.createId("spark.streaming.lastCompletedBatch_processingDelay")
        .withTag("appId", "97278898-4bd4-49c2-9889-aa5f969a7816-0013")
        .withTag("role", "driver");
    assertEquals(expected, f.apply(name));
  }


 @Test
  public void JustPatternMatching() {

    final String pattern_string = "^([^.]+)\\.(driver)\\.((CodeGenerator|DAGScheduler|BlockManager|jvm)\\..*)$";
    final String metric = "97278898-4bd4-49c2-9889-aa5f969a7816-0023.driver.jvm.pools.PS-Old-Gen.used";
    final Pattern pattern = Pattern.compile(pattern_string);
    final Matcher m = pattern.matcher(metric);

    Assert.assertEquals(true, m.matches());
    Assert.assertEquals("97278898-4bd4-49c2-9889-aa5f969a7816-0023", m.group(1));
    Assert.assertEquals("driver", m.group(2));
    Assert.assertEquals("jvm.pools.PS-Old-Gen.used", m.group(3));
    Assert.assertEquals("jvm", m.group(4));
  }


}
