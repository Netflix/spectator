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
package com.netflix.spectator.api;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class DefaultTimerTest {

  private final ManualClock clock = new ManualClock();

  @Test
  public void testInit() {
    Timer t = new DefaultTimer(clock, NoopId.INSTANCE);
    Assertions.assertEquals(t.count(), 0L);
    Assertions.assertEquals(t.totalTime(), 0L);
    Assertions.assertFalse(t.hasExpired());
  }

  @Test
  public void testRecord() {
    Timer t = new DefaultTimer(clock, NoopId.INSTANCE);
    t.record(42, TimeUnit.MILLISECONDS);
    Assertions.assertEquals(t.count(), 1L);
    Assertions.assertEquals(t.totalTime(), 42000000L);
  }

  @Test
  public void testRecordBatch() throws Exception {
    Timer t = new DefaultTimer(clock, NoopId.INSTANCE);
    try (Timer.BatchUpdater b = t.batchUpdater(2)) {
      b.record(42, TimeUnit.MILLISECONDS);
      b.record(42, TimeUnit.MILLISECONDS);
      Assertions.assertEquals(t.count(), 2L);
      Assertions.assertEquals(t.totalTime(), 84000000L);
      b.record(1, TimeUnit.MILLISECONDS);
    }
    Assertions.assertEquals(t.count(), 3L);
    Assertions.assertEquals(t.totalTime(), 85000000L);
  }

  @Test
  public void testRecordDuration() {
    Timer t = new DefaultTimer(clock, NoopId.INSTANCE);
    t.record(Duration.ofMillis(42));
    Assertions.assertEquals(t.count(), 1L);
    Assertions.assertEquals(t.totalTime(), 42000000L);
  }

  @Test
  public void testRecordDurationBatch() throws Exception {
    Timer t = new DefaultTimer(clock, NoopId.INSTANCE);
    try (Timer.BatchUpdater b = t.batchUpdater(2)) {
      b.record(Duration.ofMillis(42));
    }
    Assertions.assertEquals(t.count(), 1L);
    Assertions.assertEquals(t.totalTime(), 42000000L);
  }

  @Test
  public void testRecordNegative() {
    Timer t = new DefaultTimer(clock, NoopId.INSTANCE);
    t.record(-42, TimeUnit.MILLISECONDS);
    Assertions.assertEquals(t.count(), 0L);
    Assertions.assertEquals(t.totalTime(), 0L);
  }

  @Test
  public void testRecordNegativeBatch() throws Exception {
    Timer t = new DefaultTimer(clock, NoopId.INSTANCE);
    try (Timer.BatchUpdater b = t.batchUpdater(2)) {
      b.record(-42, TimeUnit.MILLISECONDS);
    }
    Assertions.assertEquals(t.count(), 0L);
    Assertions.assertEquals(t.totalTime(), 0L);
  }

  @Test
  public void testRecordZero() {
    Timer t = new DefaultTimer(clock, NoopId.INSTANCE);
    t.record(0, TimeUnit.MILLISECONDS);
    Assertions.assertEquals(t.count(), 1L);
    Assertions.assertEquals(t.totalTime(), 0L);
  }

  @Test
  public void testRecordZeroBatch() throws Exception {
    Timer t = new DefaultTimer(clock, NoopId.INSTANCE);
    try (Timer.BatchUpdater b = t.batchUpdater(2)) {
      b.record(0, TimeUnit.MILLISECONDS);
    }
    Assertions.assertEquals(t.count(), 1L);
    Assertions.assertEquals(t.totalTime(), 0L);
  }

  @Test
  public void testRecordCallable() throws Exception {
    Timer t = new DefaultTimer(clock, NoopId.INSTANCE);
    clock.setMonotonicTime(100L);
    int v = t.recordCallable(() -> {
      clock.setMonotonicTime(500L);
      return 42;
    });
    Assertions.assertEquals(v, 42);
    Assertions.assertEquals(t.count(), 1L);
    Assertions.assertEquals(t.totalTime(), 400L);
  }

  @Test
  public void testRecordCallableException() throws Exception {
    Timer t = new DefaultTimer(clock, NoopId.INSTANCE);
    clock.setMonotonicTime(100L);
    boolean seen = false;
    try {
      t.recordCallable(() -> {
        clock.setMonotonicTime(500L);
        throw new Exception("foo");
      });
    } catch (Exception e) {
      seen = true;
    }
    Assertions.assertTrue(seen);
    Assertions.assertEquals(t.count(), 1L);
    Assertions.assertEquals(t.totalTime(), 400L);
  }

  @Test
  public void testRecordRunnable() throws Exception {
    Timer t = new DefaultTimer(clock, NoopId.INSTANCE);
    clock.setMonotonicTime(100L);
    t.recordRunnable(() -> clock.setMonotonicTime(500L));
    Assertions.assertEquals(t.count(), 1L);
    Assertions.assertEquals(t.totalTime(), 400L);
  }

  @Test
  public void testRecordRunnableException() {
    Timer t = new DefaultTimer(clock, NoopId.INSTANCE);
    clock.setMonotonicTime(100L);
    boolean seen = false;
    try {
      t.recordRunnable(() -> {
        clock.setMonotonicTime(500L);
        throw new RuntimeException("foo");
      });
    } catch (Exception e) {
      seen = true;
    }
    Assertions.assertTrue(seen);
    Assertions.assertEquals(t.count(), 1L);
    Assertions.assertEquals(t.totalTime(), 400L);
  }

  @Test
  public void testRecordSupplier() throws Exception {
    Timer t = new DefaultTimer(clock, NoopId.INSTANCE);
    clock.setMonotonicTime(100L);
    String value = t.recordSupplier(() -> {
      clock.setMonotonicTime(500L);
      return "foo";
    });
    Assertions.assertEquals(value, "foo");
    Assertions.assertEquals(t.count(), 1L);
    Assertions.assertEquals(t.totalTime(), 400L);
  }

  @Test
  public void testRecordSupplierException() {
    Timer t = new DefaultTimer(clock, NoopId.INSTANCE);
    clock.setMonotonicTime(100L);
    boolean seen = false;
    try {
      t.recordSupplier(() -> {
        clock.setMonotonicTime(500L);
        throw new RuntimeException("foo");
      });
    } catch (Exception e) {
      seen = true;
    }
    Assertions.assertTrue(seen);
    Assertions.assertEquals(t.count(), 1L);
    Assertions.assertEquals(t.totalTime(), 400L);
  }

  @Test
  public void testRecordBooleanSupplier() throws Exception {
    Timer t = new DefaultTimer(clock, NoopId.INSTANCE);
    clock.setMonotonicTime(100L);
    boolean value = t.recordBooleanSupplier(() -> {
      clock.setMonotonicTime(500L);
      return true;
    });
    Assertions.assertTrue(value);
    Assertions.assertEquals(t.count(), 1L);
    Assertions.assertEquals(t.totalTime(), 400L);
  }

  @Test
  public void testRecordBooleanSupplierException() {
    Timer t = new DefaultTimer(clock, NoopId.INSTANCE);
    clock.setMonotonicTime(100L);
    boolean seen = false;
    try {
      t.recordBooleanSupplier(() -> {
        clock.setMonotonicTime(500L);
        throw new RuntimeException("foo");
      });
    } catch (Exception e) {
      seen = true;
    }
    Assertions.assertTrue(seen);
    Assertions.assertEquals(t.count(), 1L);
    Assertions.assertEquals(t.totalTime(), 400L);
  }

  @Test
  public void testRecordIntSupplier() throws Exception {
    Timer t = new DefaultTimer(clock, NoopId.INSTANCE);
    clock.setMonotonicTime(100L);
    int value = t.recordIntSupplier(() -> {
      clock.setMonotonicTime(500L);
      return 42;
    });
    Assertions.assertEquals(value, 42);
    Assertions.assertEquals(t.count(), 1L);
    Assertions.assertEquals(t.totalTime(), 400L);
  }

  @Test
  public void testRecordIntSupplierException() {
    Timer t = new DefaultTimer(clock, NoopId.INSTANCE);
    clock.setMonotonicTime(100L);
    boolean seen = false;
    try {
      t.recordIntSupplier(() -> {
        clock.setMonotonicTime(500L);
        throw new RuntimeException("foo");
      });
    } catch (Exception e) {
      seen = true;
    }
    Assertions.assertTrue(seen);
    Assertions.assertEquals(t.count(), 1L);
    Assertions.assertEquals(t.totalTime(), 400L);
  }

  @Test
  public void testRecordLongSupplier() throws Exception {
    Timer t = new DefaultTimer(clock, NoopId.INSTANCE);
    clock.setMonotonicTime(100L);
    long value = t.recordLongSupplier(() -> {
      clock.setMonotonicTime(500L);
      return 42L;
    });
    Assertions.assertEquals(value, 42L);
    Assertions.assertEquals(t.count(), 1L);
    Assertions.assertEquals(t.totalTime(), 400L);
  }

  @Test
  public void testRecordLongSupplierException() {
    Timer t = new DefaultTimer(clock, NoopId.INSTANCE);
    clock.setMonotonicTime(100L);
    boolean seen = false;
    try {
      t.recordLongSupplier(() -> {
        clock.setMonotonicTime(500L);
        throw new RuntimeException("foo");
      });
    } catch (Exception e) {
      seen = true;
    }
    Assertions.assertTrue(seen);
    Assertions.assertEquals(t.count(), 1L);
    Assertions.assertEquals(t.totalTime(), 400L);
  }

  @Test
  public void testRecordDoubleSupplier() throws Exception {
    Timer t = new DefaultTimer(clock, NoopId.INSTANCE);
    clock.setMonotonicTime(100L);
    double value = t.recordDoubleSupplier(() -> {
      clock.setMonotonicTime(500L);
      return 42.5;
    });
    Assertions.assertEquals(value, 42.5);
    Assertions.assertEquals(t.count(), 1L);
    Assertions.assertEquals(t.totalTime(), 400L);
  }

  @Test
  public void testRecordDoubleSupplierException() {
    Timer t = new DefaultTimer(clock, NoopId.INSTANCE);
    clock.setMonotonicTime(100L);
    boolean seen = false;
    try {
      t.recordDoubleSupplier(() -> {
        clock.setMonotonicTime(500L);
        throw new RuntimeException("foo");
      });
    } catch (Exception e) {
      seen = true;
    }
    Assertions.assertTrue(seen);
    Assertions.assertEquals(t.count(), 1L);
    Assertions.assertEquals(t.totalTime(), 400L);
  }

  private int square(int v) {
    return v * v;
  }

  @Test
  public void testCallable() throws Exception {
    Timer t = new DefaultTimer(clock, NoopId.INSTANCE);
    int v2 = t.recordCallable(() -> square(42));
  }

  @Test
  public void testMeasure() {
    Timer t = new DefaultTimer(clock, new DefaultId("foo"));
    t.record(42, TimeUnit.MILLISECONDS);
    clock.setWallTime(3712345L);
    for (Measurement m : t.measure()) {
      Assertions.assertEquals(m.timestamp(), 3712345L);
      if (m.id().equals(t.id().withTag(Statistic.count))) {
        Assertions.assertEquals(m.value(), 1.0, 0.1e-12);
      } else if (m.id().equals(t.id().withTag(Statistic.totalTime))) {
        Assertions.assertEquals(m.value(), 42e6, 0.1e-12);
      } else {
        Assertions.fail("unexpected id: " + m.id());
      }
    }
  }

}
