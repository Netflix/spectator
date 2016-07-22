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
package com.netflix.spectator.api;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(JUnit4.class)
public class DefaultRegistryTest {

  private final ManualClock clock = new ManualClock();

  private DefaultRegistry newRegistry(boolean warnings, int numberOfMeters) {
    return new DefaultRegistry(clock, new TestRegistryConfig(warnings, numberOfMeters));
  }

  @Test
  public void testCreateId() {
    Registry r = newRegistry(true, 10000);
    Assert.assertEquals(r.createId("foo"), new DefaultId("foo"));
  }

  @Test
  public void testCreateIdWithTags() {
    Registry r = newRegistry(true, 10000);
    ArrayTagSet ts = ArrayTagSet.create("k", "v");
    Assert.assertEquals(r.createId("foo", ts), new DefaultId("foo", ts));
  }

  @Test
  public void testCreateDynamicId() {
    Registry r = newRegistry(true, 10000);
    Assert.assertEquals(r.createDynamicId("foo"), new DefaultDynamicId("foo"));
  }

  @Test
  public void testCreateDynamicIdWithFactories() {
    Registry r = newRegistry(true, 10000);
    Collection<TagFactory> factories = Collections.singletonList(new TagFactory() {
      @Override
      public String name() {
        return "unused";
      }

      @Override
      public Tag createTag() {
        return null;
      }
    });
    Assert.assertEquals(r.createDynamicId("foo", factories), new DefaultDynamicId("foo").withTagFactories(factories));
  }

  @Test
  public void testRegister() {
    Registry r = newRegistry(true, 10000);
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
    Registry r = newRegistry(true, 10000);
    Counter c = r.counter(r.createId("foo"));
    c.increment();
    Assert.assertEquals(c.count(), 1L);

    Counter c2 = r.counter(r.createId("foo"));
    Assert.assertSame(c, c2);
  }

  @Test
  public void testTimer() {
    Registry r = newRegistry(true, 10000);
    Timer t = r.timer(r.createId("foo"));
    t.record(42L, TimeUnit.MILLISECONDS);
    Assert.assertEquals(t.count(), 1L);

    Timer t2 = r.timer(r.createId("foo"));
    Assert.assertSame(t, t2);
  }

  @Test
  public void testDistributionSummary() {
    Registry r = newRegistry(true, 10000);
    DistributionSummary t = r.distributionSummary(r.createId("foo"));
    t.record(42L);
    Assert.assertEquals(t.count(), 1L);

    DistributionSummary t2 = r.distributionSummary(r.createId("foo"));
    Assert.assertSame(t, t2);
  }

  @Test(expected = IllegalStateException.class)
  public void testRegisterBadTypeAccess() {
    Registry r = newRegistry(true, 10000);
    Counter c = new DefaultCounter(clock, r.createId("foo"));
    r.register(c);
    r.counter(c.id());
  }

  @Test(expected = IllegalStateException.class)
  public void testCounterBadTypeAccess() {
    Registry r = newRegistry(true, 10000);
    r.counter(r.createId("foo"));
    r.distributionSummary(r.createId("foo"));
  }

  @Test(expected = IllegalStateException.class)
  public void testTimerBadTypeAccess() {
    Registry r = newRegistry(true, 10000);
    r.timer(r.createId("foo"));
    r.counter(r.createId("foo"));
  }

  @Test(expected = IllegalStateException.class)
  public void testDistributionSummaryBadTypeAccess() {
    Registry r = newRegistry(true, 10000);
    r.distributionSummary(r.createId("foo"));
    r.timer(r.createId("foo"));
  }

  @Test
  public void testRegisterBadTypeAccessNoThrow() {
    Registry r = newRegistry(false, 10000);
    Counter c = new DefaultCounter(clock, r.createId("foo"));
    r.counter(c.id());
    r.register(c);
    Assert.assertNotSame(r.get(c.id()), c);
  }

  @Test
  public void testCounterBadTypeAccessNoThrow() {
    Registry r = newRegistry(false, 10000);
    r.counter(r.createId("foo"));
    Assert.assertEquals(r.distributionSummary(r.createId("foo")), NoopDistributionSummary.INSTANCE);
  }

  @Test
  public void testTimerBadTypeAccessNoThrow() {
    Registry r = newRegistry(false, 10000);
    r.timer(r.createId("foo"));
    Assert.assertEquals(r.counter(r.createId("foo")), NoopCounter.INSTANCE);
  }

  @Test
  public void testDistributionSummaryBadTypeAccessNoThrow() {
    Registry r = newRegistry(false, 10000);
    r.distributionSummary(r.createId("foo"));
    Assert.assertEquals(r.timer(r.createId("foo")), NoopTimer.INSTANCE);
  }

  @Test
  public void testMaxLimitExceededCounter() {
    Registry r = newRegistry(true, 1);
    Assert.assertNotSame(r.counter(r.createId("c1")), NoopCounter.INSTANCE);
    Assert.assertSame(r.counter(r.createId("c2")), NoopCounter.INSTANCE);
    Assert.assertNotSame(r.counter(r.createId("c1")), NoopCounter.INSTANCE);
  }

  @Test
  public void testMaxLimitExceededTimer() {
    Registry r = newRegistry(true, 1);
    Assert.assertNotSame(r.timer(r.createId("c1")), NoopTimer.INSTANCE);
    Assert.assertSame(r.timer(r.createId("c2")), NoopTimer.INSTANCE);
    Assert.assertNotSame(r.timer(r.createId("c1")), NoopTimer.INSTANCE);
  }

  @Test
  public void testMaxLimitExceededDistributionSummary() {
    Registry r = newRegistry(true, 1);
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
    final AtomicInteger one = new AtomicInteger(1);
    Registry r = newRegistry(true, 1);

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
    Registry r = newRegistry(true, 10000);
    Counter c = r.counter(r.createId("foo"));
    Meter m = r.get(c.id());
    Assert.assertSame(c, m);
  }

  @Test
  public void testIteratorEmpty() {
    Registry r = newRegistry(true, 10000);
    for (Meter m : r) {
      Assert.fail("should be empty, but found " + m.id());
    }
  }

  @Test
  public void testIterator() {
    Registry r = newRegistry(true, 10000);
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

  @Test
  public void testCounterNullId() {
    Registry r = newRegistry(false, 10000);
    Counter c = r.counter((Id) null);
    c.increment();
    Assert.assertEquals(c, NoopCounter.INSTANCE);
  }

  @Test
  public void testDistributionSummaryNullId() {
    Registry r = newRegistry(false, 10000);
    DistributionSummary c = r.distributionSummary((Id) null);
    c.record(42L);
    Assert.assertEquals(c, NoopDistributionSummary.INSTANCE);
  }

  @Test
  public void testTimerNullId() {
    Registry r = newRegistry(false, 10000);
    Timer c = r.timer((Id) null);
    c.record(42L, TimeUnit.SECONDS);
    Assert.assertEquals(c, NoopTimer.INSTANCE);
  }
}
