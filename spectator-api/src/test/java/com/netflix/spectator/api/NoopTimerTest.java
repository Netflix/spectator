/*
 * Copyright 2014-2016 Netflix, Inc.
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
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(JUnit4.class)
public class NoopTimerTest {
  @Test
  public void testId() {
    Assert.assertEquals(NoopTimer.INSTANCE.id(), NoopId.INSTANCE);
    Assert.assertFalse(NoopTimer.INSTANCE.hasExpired());
  }

  @Test
  public void testRecord() {
    NoopTimer t = NoopTimer.INSTANCE;
    t.record(42, TimeUnit.MILLISECONDS);
    Assert.assertEquals(t.count(), 0L);
    Assert.assertEquals(t.totalTime(), 0L);
  }

  @Test
  public void testRecordCallableException() throws Exception {
    NoopTimer t = NoopTimer.INSTANCE;
    boolean seen = false;
    try {
      t.call(() -> {
        throw new Exception("foo");
      });
    } catch (Exception e) {
      seen = true;
    }
    Assert.assertTrue(seen);
  }

  @Test
  public void testRecordRunnable() throws Exception {
    NoopTimer t = NoopTimer.INSTANCE;
    AtomicBoolean run = new AtomicBoolean();

    t.run(() -> run.set(true));
    Assert.assertTrue(run.get());
  }

  @Test
  public void testMeasure() {
    NoopTimer t = NoopTimer.INSTANCE;
    t.record(42, TimeUnit.MILLISECONDS);
    Assert.assertFalse(t.measure().iterator().hasNext());
  }

}
