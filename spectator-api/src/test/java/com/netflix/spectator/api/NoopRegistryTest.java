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

import java.util.concurrent.TimeUnit;

@RunWith(JUnit4.class)
public class NoopRegistryTest {

  private final ManualClock clock = new ManualClock();

  @Test
  public void testCreateId() {
    Registry r = new NoopRegistry();
    Assert.assertEquals(r.createId("foo"), NoopId.INSTANCE);
  }

  @Test
  public void testCreateIdWithTags() {
    Registry r = new NoopRegistry();
    TagList ts = new TagList("k", "v");
    Assert.assertEquals(r.createId("foo", ts), NoopId.INSTANCE);
  }

  @Test
  public void testRegister() {
    Registry r = new NoopRegistry();
    Counter c = new DefaultCounter(clock, r.createId("foo"));
    r.register(c);
    Assert.assertNull(r.get(c.id()));
  }

  @Test
  public void testCounter() {
    Registry r = new NoopRegistry();
    Counter c = r.counter(r.createId("foo"));
    c.increment();
    Assert.assertEquals(c.count(), 0L);

    Counter c2 = r.counter(r.createId("foo"));
    Assert.assertSame(c, c2);
  }

  @Test
  public void testTimer() {
    Registry r = new NoopRegistry();
    Timer t = r.timer(r.createId("foo"));
    t.record(42L, TimeUnit.MILLISECONDS);
    Assert.assertEquals(t.count(), 0L);

    Timer t2 = r.timer(r.createId("foo"));
    Assert.assertSame(t, t2);
  }

  @Test
  public void testDistributionSummary() {
    Registry r = new NoopRegistry();
    DistributionSummary t = r.distributionSummary(r.createId("foo"));
    t.record(42L);
    Assert.assertEquals(t.count(), 0L);

    DistributionSummary t2 = r.distributionSummary(r.createId("foo"));
    Assert.assertSame(t, t2);
  }

  @Test
  public void testGet() {
    Registry r = new NoopRegistry();
    Counter c = r.counter(r.createId("foo"));
    Assert.assertNull(r.get(c.id()));
  }

  @Test
  public void testIteratorEmpty() {
    Registry r = new NoopRegistry();
    for (Meter m : r) {
      Assert.fail("should be empty, but found " + m.id());
    }
  }

  @Test
  public void testIterator() {
    Registry r = new NoopRegistry();
    r.counter(r.createId("foo"));
    r.counter(r.createId("bar"));
    for (Meter m : r) {
      Assert.fail("should be empty, but found " + m.id());
    }
  }
}
