/*
 * Copyright 2014-2022 Netflix, Inc.
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
package com.netflix.spectator.sidecar;

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DistributionSummary;
import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.ManualClock;
import com.netflix.spectator.api.Timer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;


public class SidecarRegistryTest {

  private final ManualClock clock = new ManualClock();
  private final TestConfig config = new TestConfig();
  private final SidecarRegistry registry = new SidecarRegistry(clock, config, config.writer());

  @BeforeEach
  public void beforeEach() throws Exception {
    clock.setWallTime(0L);
    clock.setMonotonicTime(0L);
    registry.close();
  }

  private void assertSingleMessage(String expected) {
    List<String> messages = config.writer().messages();
    Assertions.assertEquals(1, messages.size());
    Assertions.assertEquals(messages.get(0), expected);
  }

  private void assertNoMessage() {
    List<String> messages = config.writer().messages();
    Assertions.assertEquals(0, messages.size());
  }

  @Test
  public void idJustName() {
    registry.counter("test").increment();
    assertSingleMessage("c:test:1");
  }

  @Test
  public void idJustNameInvalidChars() {
    registry.counter("test name").increment();
    assertSingleMessage("c:test_name:1");
  }

  @Test
  public void idWithTags() {
    registry.counter("test", "app", "foo", "node", "i$1234").increment();
    assertSingleMessage("c:test,app=foo,node=i_1234:1");
  }

  @Test
  public void counter() {
    Counter c = registry.counter("test");
    c.increment();
    assertSingleMessage("c:test:1");
    Assertions.assertTrue(Double.isNaN(c.actualCount()));
  }

  @Test
  public void counterIncrementAmount() {
    registry.counter("test").increment(42);
    assertSingleMessage("c:test:42");
  }

  @Test
  public void counterIncrementNegative() {
    registry.counter("test").increment(-42);
    assertNoMessage();
  }

  @Test
  public void counterIncrementZero() {
    registry.counter("test").increment(0);
    assertNoMessage();
  }

  @Test
  public void counterAdd() {
    registry.counter("test").add(42.0);
    assertSingleMessage("c:test:42.0");
  }

  @Test
  public void counterAddNegative() {
    registry.counter("test").add(-42.0);
    assertNoMessage();
  }

  @Test
  public void counterAddZero() {
    registry.counter("test").add(0.0);
    assertNoMessage();
  }

  @Test
  public void distributionSummary() {
    DistributionSummary d = registry.distributionSummary("test");
    d.record(42);
    assertSingleMessage("d:test:42");
    Assertions.assertEquals(0L, d.count());
    Assertions.assertEquals(0L, d.totalAmount());
  }

  @Test
  public void distributionSummaryNegative() {
    DistributionSummary d = registry.distributionSummary("test");
    d.record(-42);
    assertNoMessage();
  }

  @Test
  public void distributionSummaryZero() {
    DistributionSummary d = registry.distributionSummary("test");
    d.record(0);
    assertSingleMessage("d:test:0");
  }

  @Test
  public void gauge() {
    Gauge g = registry.gauge("test");
    g.set(42);
    assertSingleMessage("g:test:42.0");
    Assertions.assertTrue(Double.isNaN(g.value()));
  }

  @Test
  public void maxGauge() {
    Gauge g = registry.maxGauge("test");
    g.set(42);
    assertSingleMessage("m:test:42.0");
    Assertions.assertTrue(Double.isNaN(g.value()));
  }

  @Test
  public void timer() {
    Timer t = registry.timer("test");
    t.record(42, TimeUnit.MILLISECONDS);
    assertSingleMessage("t:test:0.042");
    Assertions.assertEquals(0L, t.count());
    Assertions.assertEquals(0L, t.totalTime());
  }

  @Test
  public void timerNegative() {
    Timer t = registry.timer("test");
    t.record(-42, TimeUnit.MILLISECONDS);
    assertNoMessage();
  }

  @Test
  public void timerZero() {
    Timer t = registry.timer("test");
    t.record(0, TimeUnit.MILLISECONDS);
    assertSingleMessage("t:test:0.0");
  }

  @Test
  public void timerRecordCallable() throws Exception {
    Timer t = registry.timer("test");
    long v = t.record(() -> {
      clock.setMonotonicTime(100_000_000);
      return registry.clock().monotonicTime();
    });
    Assertions.assertEquals(100_000_000L, v);
    assertSingleMessage("t:test:0.1");
  }

  @Test
  public void timerRecordRunnable() {
    Timer t = registry.timer("test");
    t.record(() -> {
      clock.setMonotonicTime(100_000_000);
    });
    assertSingleMessage("t:test:0.1");
  }

  @Test
  public void get() {
    registry.counter("test").increment();
    Assertions.assertNull(registry.get(Id.create("test")));
  }

  @Test
  public void iterator() {
    registry.counter("test").increment();
    Assertions.assertFalse(registry.iterator().hasNext());
  }

  private static class TestConfig implements SidecarConfig {

    private final MemoryWriter writer = new MemoryWriter();

    @Override public String get(String k) {
      return null;
    }

    @Override public String outputLocation() {
      return "none";
    }

    MemoryWriter writer() {
      return writer;
    }
  }
}
