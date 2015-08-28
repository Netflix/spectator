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
package com.netflix.spectator.metrics3;

import com.codahale.metrics.MetricRegistry;
import com.netflix.spectator.api.ManualClock;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(JUnit4.class)
public class MetricsRegistryTest {

  private final ManualClock clock = new ManualClock();

  @Test
  public void metricName() {
    MetricRegistry codaRegistry = new MetricRegistry();
    MetricsRegistry r = new MetricsRegistry(clock, codaRegistry);
    r.counter("foo", "id", "bar", "a", "b", "a", "c").increment();
    Assert.assertTrue(codaRegistry.getMeters().containsKey("foo.id-bar.a-c"));
  }

  @Test
  public void counter() {
    MetricRegistry codaRegistry = new MetricRegistry();
    MetricsRegistry r = new MetricsRegistry(clock, codaRegistry);
    r.counter("foo").increment();
    Assert.assertEquals(1, codaRegistry.getMeters().get("foo").getCount());
    r.counter("foo").increment(15);
    Assert.assertEquals(16, codaRegistry.getMeters().get("foo").getCount());
  }

  @Test
  public void timer() {
    MetricRegistry codaRegistry = new MetricRegistry();
    MetricsRegistry r = new MetricsRegistry(clock, codaRegistry);
    r.timer("foo").record(1, TimeUnit.MILLISECONDS);
    Assert.assertEquals(1, codaRegistry.getTimers().get("foo").getCount());
  }

  @Test
  public void distributionSummary() {
    MetricRegistry codaRegistry = new MetricRegistry();
    MetricsRegistry r = new MetricsRegistry(clock, codaRegistry);
    r.distributionSummary("foo").record(1);
    Assert.assertEquals(1, codaRegistry.getHistograms().get("foo").getCount());
  }

  @Ignore
  public void gauge() {
    MetricRegistry codaRegistry = new MetricRegistry();
    MetricsRegistry r = new MetricsRegistry(clock, codaRegistry);
    AtomicInteger num = r.gauge("foo", new AtomicInteger(42));
    Assert.assertEquals(42.0, (Double) codaRegistry.getGauges().get("foo").getValue(), 1e-12);
  }
}
