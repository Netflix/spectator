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

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.ExtendedRegistry;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Meter;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.regex.Pattern;

@RunWith(JUnit4.class)
public class SpectatorReporterTest {

  private final MetricRegistry metricsRegistry = new MetricRegistry();
  private final ExtendedRegistry registry = new ExtendedRegistry(new DefaultRegistry());
  private final SpectatorReporter reporter = SpectatorReporter.forRegistry(metricsRegistry)
      .withSpectatorRegistry(registry)
      .withGaugeCounters(Pattern.compile("^gaugeCounter.*$"))
      .build();

  private final Counter metricsCounter = metricsRegistry.counter("metricsCounter");
  private final Counter gaugeCounter = metricsRegistry.counter("gaugeCounter");

  @Test
  public void incrementGaugeCounter() {
    long before = registry.counter("gaugeCounter").count();

    gaugeCounter.inc();
    reporter.report();
    long after = registry.counter("gaugeCounter").count();
    Assert.assertEquals(before + 1, after);

    metricsCounter.dec();
    reporter.report();
    before = after;
    after = getValue("gaugeCounter");
    Assert.assertEquals(before, after);
  }

  private long getValue(String name) {
    Meter meter = registry.get(registry.createId(name));
    if (meter != null) {
      for (Measurement m : meter.measure()) {
        return (long) m.value();
      }
    }
    return Long.MAX_VALUE;
  }

  @Test
  public void incrementMetricsCounter() {
    reporter.report();
    long before = getValue("metricsCounter");

    metricsCounter.inc();
    reporter.report();
    long after = getValue("metricsCounter");
    Assert.assertEquals(before + 1, after);

    metricsCounter.dec();
    reporter.report();
    before = after;
    after = getValue("metricsCounter");
    Assert.assertEquals(before - 1, after);
  }
}
