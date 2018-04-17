/*
 * Copyright 2014-2018 Netflix, Inc.
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
package com.netflix.spectator.api;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.TimeUnit;

@RunWith(JUnit4.class)
public class SwapMeterTest {
  private final Registry registry = new DefaultRegistry();

  private final Id counterId = registry.createId("counter");
  private final Counter counter = registry.counter(counterId);

  private final Id gaugeId = registry.createId("gauge");
  private final Gauge gauge = registry.gauge(gaugeId);

  private final Id timerId = registry.createId("timer");
  private final Timer timer = registry.timer(timerId);

  private final Id distSummaryId = registry.createId("distSummary");
  private final DistributionSummary distSummary = registry.distributionSummary(distSummaryId);

  @Test
  public void counterNullHasExpired() {
    SwapCounter sc = new SwapCounter(registry, counterId, null);
    Assert.assertTrue(sc.hasExpired());
  }

  @Test
  public void counterNullLookup() {
    SwapCounter sc = new SwapCounter(registry, counterId, null);
    sc.increment();
    Assert.assertEquals(counter.count(), sc.get().count());
  }

  @Test
  public void counterSetToNull() {
    SwapCounter sc = new SwapCounter(registry, counterId, counter);
    Assert.assertFalse(sc.hasExpired());
    sc.set(null);
    Assert.assertTrue(sc.hasExpired());
    Assert.assertEquals(counter.count(), sc.get().count());
  }

  @Test
  public void gaugeNullHasExpired() {
    SwapGauge sg = new SwapGauge(registry, gaugeId, null);
    Assert.assertTrue(sg.hasExpired());
  }

  @Test
  public void gaugeNullLookup() {
    SwapGauge sg = new SwapGauge(registry, gaugeId, null);
    sg.set(42.0);
    Assert.assertEquals(gauge.value(), sg.get().value(), 1e-12);
  }

  @Test
  public void gaugeSetToNull() {
    SwapGauge sg = new SwapGauge(registry, gaugeId, gauge);
    Assert.assertFalse(sg.hasExpired());
    sg.set(null);
    Assert.assertTrue(sg.hasExpired());
    Assert.assertEquals(gauge.value(), sg.get().value(), 1e-12);
  }

  @Test
  public void timerNullHasExpired() {
    SwapTimer st = new SwapTimer(registry, timerId, null);
    Assert.assertTrue(st.hasExpired());
  }

  @Test
  public void timerNullLookup() {
    SwapTimer st = new SwapTimer(registry, timerId, null);
    st.record(42, TimeUnit.NANOSECONDS);
    Assert.assertEquals(timer.totalTime(), st.get().totalTime());
  }

  @Test
  public void timerSetToNull() {
    SwapTimer st = new SwapTimer(registry, timerId, timer);
    Assert.assertFalse(st.hasExpired());
    st.set(null);
    Assert.assertTrue(st.hasExpired());
    Assert.assertEquals(timer.totalTime(), st.get().totalTime());
  }

  @Test
  public void distSummaryNullHasExpired() {
    SwapDistributionSummary st = new SwapDistributionSummary(registry, distSummaryId, null);
    Assert.assertTrue(st.hasExpired());
  }

  @Test
  public void distSummaryNullLookup() {
    SwapDistributionSummary st = new SwapDistributionSummary(registry, distSummaryId, null);
    st.record(42);
    Assert.assertEquals(distSummary.totalAmount(), st.get().totalAmount());
  }

  @Test
  public void distSummarySetToNull() {
    SwapDistributionSummary st = new SwapDistributionSummary(registry, distSummaryId, distSummary);
    Assert.assertFalse(st.hasExpired());
    st.set(null);
    Assert.assertTrue(st.hasExpired());
    Assert.assertEquals(distSummary.totalAmount(), st.get().totalAmount());
  }
}
