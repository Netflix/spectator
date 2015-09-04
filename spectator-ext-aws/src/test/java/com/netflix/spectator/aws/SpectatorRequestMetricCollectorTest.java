/*
 * Copyright 2015 Netflix, Inc.
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
package com.netflix.spectator.aws;

import com.amazonaws.DefaultRequest;
import com.amazonaws.Request;
import com.amazonaws.Response;
import com.amazonaws.http.HttpResponse;
import com.amazonaws.util.AWSRequestMetrics;
import com.amazonaws.util.AWSRequestMetricsFullSupport;
import com.amazonaws.util.TimingInfo;
import com.netflix.spectator.api.*;
import org.apache.http.client.methods.HttpPost;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;

public class SpectatorRequestMetricCollectorTest {

  Registry registry;
  SpectatorRequestMetricCollector collector;

  @Before
  public void setUp() {
    registry = new DefaultRegistry();
    collector = new SpectatorRequestMetricCollector(registry);
  }

  @Test
  public void testMetricCollection() {
    //setup
    AWSRequestMetrics metrics = new AWSRequestMetricsFullSupport();
    String counterName = "BytesProcessed";
    String timerName = "ClientExecuteTime";
    metrics.setCounter(counterName, 12345);
    metrics.getTimingInfo().addSubMeasurement(timerName, TimingInfo.unmodifiableTimingInfo(100000L, 200000L));

    Request<?> req = new DefaultRequest("foo");
    req.setAWSRequestMetrics(metrics);
    req.setEndpoint(URI.create("http://foo"));

    HttpResponse hr = new HttpResponse(req, new HttpPost("http://foo"));
    hr.setStatusCode(200);
    Response<?> resp = new Response<>(null, new HttpResponse(req, new HttpPost("http://foo")));

    //when
    collector.collectMetrics(req, resp);

    //then
    List<Meter> allMetrics = new ArrayList<>();
    registry.iterator().forEachRemaining(allMetrics::add);

    assertEquals(2, allMetrics.size());
    Optional<Timer> expectedTimer = allMetrics
        .stream()
        .filter(m -> m instanceof Timer && m.id().name().equals(SpectatorRequestMetricCollector.idName(timerName)))
        .map(m -> (Timer) m)
        .findFirst();
    assertTrue(expectedTimer.isPresent());
    Timer timer = expectedTimer.get();
    assertEquals(1, timer.count());
    assertEquals(100000, timer.totalTime());

    Optional<Counter> expectedCounter = allMetrics
        .stream()
        .filter(m -> m.id().name().equals(SpectatorRequestMetricCollector.idName(counterName)))
        .map(m -> (Counter) m)
        .findFirst();
    assertTrue(expectedCounter.isPresent());
    assertEquals(12345L, expectedCounter.get().count());
  }
}