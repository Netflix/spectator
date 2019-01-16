/*
 * Copyright 2014-2019 Netflix, Inc.
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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class NoopTimerTest {
  @Test
  public void testId() {
    Assertions.assertEquals(NoopTimer.INSTANCE.id(), NoopId.INSTANCE);
    Assertions.assertFalse(NoopTimer.INSTANCE.hasExpired());
  }

  @Test
  public void testRecord() {
    NoopTimer t = NoopTimer.INSTANCE;
    t.record(42, TimeUnit.MILLISECONDS);
    Assertions.assertEquals(t.count(), 0L);
    Assertions.assertEquals(t.totalTime(), 0L);
  }

  @Test
  public void testRecordCallableException() throws Exception {
    NoopTimer t = NoopTimer.INSTANCE;
    boolean seen = false;
    try {
      t.record(() -> {
        throw new Exception("foo");
      });
    } catch (Exception e) {
      seen = true;
    }
    Assertions.assertTrue(seen);
  }

  @Test
  public void testRecordRunnable() throws Exception {
    NoopTimer t = NoopTimer.INSTANCE;
    AtomicBoolean run = new AtomicBoolean();

    t.record(() -> run.set(true));
    Assertions.assertTrue(run.get());
  }

  @Test
  public void testMeasure() {
    NoopTimer t = NoopTimer.INSTANCE;
    t.record(42, TimeUnit.MILLISECONDS);
    Assertions.assertFalse(t.measure().iterator().hasNext());
  }

}
