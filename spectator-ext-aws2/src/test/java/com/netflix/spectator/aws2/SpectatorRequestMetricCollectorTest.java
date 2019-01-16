/*
 * Copyright 2014-2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spectator.aws2;

import com.netflix.spectator.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.DefaultRequest;
import software.amazon.awssdk.Request;
import software.amazon.awssdk.metrics.spi.AwsRequestMetrics;
import software.amazon.awssdk.metrics.spi.TimingInfo;
import software.amazon.awssdk.services.cloudwatch.model.ListMetricsRequest;
import software.amazon.awssdk.util.AwsRequestMetricsFullSupport;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class SpectatorRequestMetricCollectorTest {

  Registry registry;
  SpectatorRequestMetricCollector collector;

  @BeforeEach
  public void setUp() {
    registry = new DefaultRegistry();
    collector = new SpectatorRequestMetricCollector(registry);
  }

  private void execRequest(String endpoint, int status) {
    AwsRequestMetrics metrics = new AwsRequestMetricsFullSupport();
    metrics.addProperty(AwsRequestMetrics.Field.ServiceName, "AmazonCloudWatch");
    metrics.addProperty(AwsRequestMetrics.Field.ServiceEndpoint, endpoint);
    metrics.addProperty(AwsRequestMetrics.Field.StatusCode, "" + status);
    if (status == 503) {
      metrics.addProperty(AwsRequestMetrics.Field.AWSErrorCode, "Throttled");
    }
    String counterName = "BytesProcessed";
    String timerName = "ClientExecuteTime";
    metrics.setCounter(counterName, 12345);
    metrics.getTimingInfo().addSubMeasurement(timerName, TimingInfo.unmodifiableTimingInfo(100000L, 200000L));

    Request<?> req = new DefaultRequest(
        ListMetricsRequest.builder().build(), "AmazonCloudWatch");
    req.setAwsRequestMetrics(metrics);
    req.setEndpoint(URI.create(endpoint));

    collector.collectMetrics(req, null);
  }

  /** Returns a set of all values for a given tag key. */
  private Set<String> valueSet(String k) {
    return registry.stream()
        .map(m -> Utils.getTagValue(m.id(), k))
        .filter(v -> v != null)
        .collect(Collectors.toSet());
  }

  private Set<String> set(String... vs) {
    return new HashSet<>(Arrays.asList(vs));
  }

  @Test
  public void extractServiceEndpoint() {
    execRequest("http://monitoring", 200);
    assertEquals(set("monitoring"), valueSet("serviceEndpoint"));

    execRequest("http://s3", 200);
    assertEquals(set("monitoring", "s3"), valueSet("serviceEndpoint"));
  }

  @Test
  public void extractServiceName() {
    execRequest("http://monitoring", 200);
    assertEquals(set("AmazonCloudWatch"), valueSet("serviceName"));
  }

  @Test
  public void extractRequestType() {
    execRequest("http://monitoring", 200);
    assertEquals(set("ListMetricsRequest"), valueSet("requestType"));
  }

  @Test
  public void extractStatusCode() {
    execRequest("http://monitoring", 200);
    execRequest("http://monitoring", 429);
    execRequest("http://monitoring", 503);
    assertEquals(set("200", "429", "503"), valueSet("statusCode"));
  }

  @Test
  public void extractErrorCode() {
    execRequest("http://monitoring", 200);
    assertEquals(set(), valueSet("AWSErrorCode"));

    execRequest("http://monitoring", 503);
    assertEquals(set("Throttled"), valueSet("AWSErrorCode"));
  }

  @Test
  public void testMetricCollection() {
    execRequest("http://foo", 200);

    //then
    List<Meter> allMetrics = new ArrayList<>();
    registry.iterator().forEachRemaining(allMetrics::add);

    assertEquals(2, allMetrics.size());
    Optional<Timer> expectedTimer = registry.timers().findFirst();
    assertTrue(expectedTimer.isPresent());
    Timer timer = expectedTimer.get();
    assertEquals(1, timer.count());
    assertEquals(100000, timer.totalTime());

    Optional<Counter> expectedCounter = registry.counters().findFirst();
    assertTrue(expectedCounter.isPresent());
    assertEquals(12345L, expectedCounter.get().count());
  }

  @Test
  public void testListFiltering() {
    assertEquals(Optional.empty(), SpectatorRequestMetricCollector.firstValue(null, Object::toString));
    assertEquals(Optional.empty(), SpectatorRequestMetricCollector.firstValue(Collections.emptyList(), Object::toString));
    assertEquals(Optional.of("1"), SpectatorRequestMetricCollector.firstValue(Collections.singletonList(1L), Object::toString));
    assertEquals(Optional.empty(), SpectatorRequestMetricCollector.firstValue(Collections.singletonList(null), Object::toString));
  }
}