/**
 * Copyright 2014 Netflix, Inc.
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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(JUnit4.class)
public class DefaultRegistryTest {

  private final ManualClock clock = new ManualClock();

  @Before
  public void init() {
    System.setProperty("spectator.api.propagateWarnings", "true");
    System.setProperty("spectator.api.maxNumberOfMeters", "10000");
  }

  @Test
  public void testCreateId() {
    Registry r = new DefaultRegistry(clock);
    Assert.assertEquals(r.createId("foo"), new DefaultId("foo"));
  }

  @Test
  public void testCreateIdWithTags() {
    Registry r = new DefaultRegistry(clock);
    TagList ts = new TagList("k", "v");
    Assert.assertEquals(r.createId("foo", ts), new DefaultId("foo", ts));
  }

  @Test
  public void testRegister() {
    Registry r = new DefaultRegistry(clock);
    Counter c = new DefaultCounter(clock, r.createId("foo"));
    r.register(c);
    c.increment();
    Assert.assertEquals(c.count(), 1L);
    r.register(c);
    Meter meter = r.get(c.id());
    for (Measurement m : meter.measure()) {
      Assert.assertEquals(m.value(), 2.0, 1e-12);
    }
  }

  @Test
  public void testCounter() {
    Registry r = new DefaultRegistry(clock);
    Counter c = r.counter(r.createId("foo"));
    c.increment();
    Assert.assertEquals(c.count(), 1L);

    Counter c2 = r.counter(r.createId("foo"));
    Assert.assertSame(c, c2);
  }

  @Test
  public void testTimer() {
    Registry r = new DefaultRegistry(clock);
    Timer t = r.timer(r.createId("foo"));
    t.record(42L, TimeUnit.MILLISECONDS);
    Assert.assertEquals(t.count(), 1L);

    Timer t2 = r.timer(r.createId("foo"));
    Assert.assertSame(t, t2);
  }

  @Test
  public void testDistributionSummary() {
    Registry r = new DefaultRegistry(clock);
    DistributionSummary t = r.distributionSummary(r.createId("foo"));
    t.record(42L);
    Assert.assertEquals(t.count(), 1L);

    DistributionSummary t2 = r.distributionSummary(r.createId("foo"));
    Assert.assertSame(t, t2);
  }

  @Test(expected = IllegalStateException.class)
  public void testRegisterBadTypeAccess() {
    Registry r = new DefaultRegistry(clock);
    Counter c = new DefaultCounter(clock, r.createId("foo"));
    r.register(c);
    r.counter(c.id());
  }

  @Test(expected = IllegalStateException.class)
  public void testCounterBadTypeAccess() {
    Registry r = new DefaultRegistry(clock);
    r.counter(r.createId("foo"));
    r.distributionSummary(r.createId("foo"));
  }

  @Test(expected = IllegalStateException.class)
  public void testTimerBadTypeAccess() {
    Registry r = new DefaultRegistry(clock);
    r.timer(r.createId("foo"));
    r.counter(r.createId("foo"));
  }

  @Test(expected = IllegalStateException.class)
  public void testDistributionSummaryBadTypeAccess() {
    Registry r = new DefaultRegistry(clock);
    r.distributionSummary(r.createId("foo"));
    r.timer(r.createId("foo"));
  }

  @Test
  public void testRegisterBadTypeAccessNoThrow() {
    System.setProperty("spectator.api.propagateWarnings", "false");
    Registry r = new DefaultRegistry(clock);
    Counter c = new DefaultCounter(clock, r.createId("foo"));
    r.counter(c.id());
    r.register(c);
    Assert.assertNotSame(r.get(c.id()), c);
  }

  @Test
  public void testCounterBadTypeAccessNoThrow() {
    System.setProperty("spectator.api.propagateWarnings", "false");
    Registry r = new DefaultRegistry(clock);
    r.counter(r.createId("foo"));
    Assert.assertEquals(r.distributionSummary(r.createId("foo")), NoopDistributionSummary.INSTANCE);
  }

  @Test
  public void testTimerBadTypeAccessNoThrow() {
    System.setProperty("spectator.api.propagateWarnings", "false");
    Registry r = new DefaultRegistry(clock);
    r.timer(r.createId("foo"));
    Assert.assertEquals(r.counter(r.createId("foo")), NoopCounter.INSTANCE);
  }

  @Test
  public void testDistributionSummaryBadTypeAccessNoThrow() {
    System.setProperty("spectator.api.propagateWarnings", "false");
    Registry r = new DefaultRegistry(clock);
    r.distributionSummary(r.createId("foo"));
    Assert.assertEquals(r.timer(r.createId("foo")), NoopTimer.INSTANCE);
  }

  @Test
  public void testMaxLimitExceededCounter() {
    System.setProperty("spectator.api.maxNumberOfMeters", "1");
    Registry r = new DefaultRegistry(clock);
    Assert.assertNotSame(r.counter(r.createId("c1")), NoopCounter.INSTANCE);
    Assert.assertSame(r.counter(r.createId("c2")), NoopCounter.INSTANCE);
    Assert.assertNotSame(r.counter(r.createId("c1")), NoopCounter.INSTANCE);
  }

  @Test
  public void testMaxLimitExceededTimer() {
    System.setProperty("spectator.api.maxNumberOfMeters", "1");
    Registry r = new DefaultRegistry(clock);
    Assert.assertNotSame(r.timer(r.createId("c1")), NoopTimer.INSTANCE);
    Assert.assertSame(r.timer(r.createId("c2")), NoopTimer.INSTANCE);
    Assert.assertNotSame(r.timer(r.createId("c1")), NoopTimer.INSTANCE);
  }

  @Test
  public void testMaxLimitExceededDistributionSummary() {
    System.setProperty("spectator.api.maxNumberOfMeters", "1");
    Registry r = new DefaultRegistry(clock);
    Assert.assertNotSame(r.distributionSummary(r.createId("c1")), NoopDistributionSummary.INSTANCE);
    Assert.assertSame(r.distributionSummary(r.createId("c2")), NoopDistributionSummary.INSTANCE);
    Assert.assertNotSame(r.distributionSummary(r.createId("c1")), NoopDistributionSummary.INSTANCE);
  }

  private double sum(Registry r) {
    double sum = 0.0;
    for (Meter m : r) {
      for (Measurement v : m.measure()) {
        sum += v.value();
      }
    }
    return sum;
  }

  @Test
  public void testMaxLimitExceededRegister() {
    System.setProperty("spectator.api.maxNumberOfMeters", "1");
    final AtomicInteger one = new AtomicInteger(1);
    ExtendedRegistry r = new ExtendedRegistry(new DefaultRegistry(clock));

    Assert.assertEquals(sum(r), 0.0, 1e-12);
    r.gauge(r.createId("c1"), one);
    Assert.assertEquals(sum(r), 1.0, 1e-12);
    r.gauge(r.createId("c2"), one);
    Assert.assertEquals(sum(r), 1.0, 1e-12);
    r.gauge(r.createId("c1"), one);
    Assert.assertEquals(sum(r), 2.0, 1e-12);
  }

  @Test
  public void testGet() {
    Registry r = new DefaultRegistry(clock);
    Counter c = r.counter(r.createId("foo"));
    Meter m = r.get(c.id());
    Assert.assertSame(c, m);
  }

  @Test
  public void testIteratorEmpty() {
    Registry r = new DefaultRegistry(clock);
    for (Meter m : r) {
      Assert.fail("should be empty, but found " + m.id());
    }
  }

  @Test
  public void testIterator() {
    Registry r = new DefaultRegistry(clock);
    r.counter(r.createId("foo"));
    r.counter(r.createId("bar"));
    Set<Id> expected = new HashSet<>();
    expected.add(r.createId("foo"));
    expected.add(r.createId("bar"));
    for (Meter m : r) {
      expected.remove(m.id());
    }
    Assert.assertTrue(expected.isEmpty());
  }
}
