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

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleConsumer;
import java.util.function.DoublePredicate;
import java.util.function.DoubleSupplier;
import java.util.function.DoubleToIntFunction;
import java.util.function.DoubleToLongFunction;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;
import java.util.function.IntBinaryOperator;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.IntSupplier;
import java.util.function.IntToDoubleFunction;
import java.util.function.IntToLongFunction;
import java.util.function.IntUnaryOperator;
import java.util.function.LongBinaryOperator;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;
import java.util.function.LongSupplier;
import java.util.function.LongToDoubleFunction;
import java.util.function.LongToIntFunction;
import java.util.function.LongUnaryOperator;
import java.util.function.ObjDoubleConsumer;
import java.util.function.ObjIntConsumer;
import java.util.function.ObjLongConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleBiFunction;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntBiFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongBiFunction;
import java.util.function.ToLongFunction;
import java.util.function.UnaryOperator;

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
  public void testRecordBooleanSupplier() {
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
  public void testRecordIntSupplier() {
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
  public void testRecordLongSupplier() {
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
  public void testRecordDoubleSupplier() {
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

  @SuppressWarnings("unchecked")
  private <T> void testWrapper(Class<T> cls, BiFunction<Timer, T, T> wrap, Consumer<T> run, Object retValue) {
    clock.setMonotonicTime(0L);
    Timer t = new DefaultTimer(clock, NoopId.INSTANCE);
    clock.setMonotonicTime(100L);

    // Success
    T f = (T) Proxy.newProxyInstance(
        cls.getClassLoader(),
        new Class<?>[]{cls},
        (obj, m, args) -> {
          clock.setMonotonicTime(500L);
          return retValue;
        }
    );
    T wrapped = wrap.apply(t, f);
    Assertions.assertEquals(t.count(), 0L);
    run.accept(wrapped);
    Assertions.assertEquals(t.count(), 1L);
    Assertions.assertEquals(t.totalTime(), 400L);

    // Exception
    T f2 = (T) Proxy.newProxyInstance(
        cls.getClassLoader(),
        new Class<?>[]{cls},
        (obj, m, args) -> {
          clock.setMonotonicTime(1000L);
          throw new RuntimeException("foo");
        }
    );
    final T wrapped2 = wrap.apply(t, f2);
    Assertions.assertThrows(RuntimeException.class, () -> run.accept(wrapped2));
    Assertions.assertEquals(t.count(), 2L);
    Assertions.assertEquals(t.totalTime(), 900L);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testWrappingLambdas() {
    Consumer<Callable> cc = c -> {
      try {
        c.call();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    };
    testWrapper(Callable.class, Timer::wrapCallable, cc, "");
    testWrapper(Runnable.class, Timer::wrapRunnable, Runnable::run, "");
    testWrapper(BiConsumer.class, Timer::wrapBiConsumer, c -> c.accept("", ""), "");
    testWrapper(BiFunction.class, Timer::wrapBiFunction, c -> c.apply("", ""), "");
    testWrapper(BinaryOperator.class, Timer::wrapBinaryOperator, c -> c.apply("", ""), "");
    testWrapper(BiPredicate.class, Timer::wrapBiPredicate, c -> c.test("", ""), false);
    testWrapper(BooleanSupplier.class, Timer::wrapBooleanSupplier, BooleanSupplier::getAsBoolean, false);
    testWrapper(Consumer.class, Timer::wrapConsumer, c -> c.accept(""), "");
    testWrapper(DoubleBinaryOperator.class, Timer::wrapDoubleBinaryOperator, c -> c.applyAsDouble(1.0, 2.0), 3.0);
    testWrapper(DoubleConsumer.class, Timer::wrapDoubleConsumer, c -> c.accept(1.0), "");
    testWrapper(java.util.function.DoubleFunction.class, Timer::wrapDoubleFunction, c -> c.apply(1.0), 1.0);
    testWrapper(DoublePredicate.class, Timer::wrapDoublePredicate, c -> c.test(1.0), false);
    testWrapper(DoubleSupplier.class, Timer::wrapDoubleSupplier, DoubleSupplier::getAsDouble, 1.0);
    testWrapper(DoubleToIntFunction.class, Timer::wrapDoubleToIntFunction, c -> c.applyAsInt(1.0), 1);
    testWrapper(DoubleToLongFunction.class, Timer::wrapDoubleToLongFunction, c -> c.applyAsLong(1.0), 1L);
    testWrapper(DoubleUnaryOperator.class, Timer::wrapDoubleUnaryOperator, c -> c.applyAsDouble(1.0), 1.0);
    testWrapper(Function.class, Timer::wrapFunction, c -> c.apply(""), "");
    testWrapper(IntBinaryOperator.class, Timer::wrapIntBinaryOperator, c -> c.applyAsInt(1, 2), 3);
    testWrapper(IntConsumer.class, Timer::wrapIntConsumer, c -> c.accept(1), "");
    testWrapper(IntFunction.class, Timer::wrapIntFunction, c -> c.apply(1), 1);
    testWrapper(IntPredicate.class, Timer::wrapIntPredicate, c -> c.test(1), false);
    testWrapper(IntSupplier.class, Timer::wrapIntSupplier, IntSupplier::getAsInt, 1);
    testWrapper(IntToDoubleFunction.class, Timer::wrapIntToDoubleFunction, c -> c.applyAsDouble(1), 1.0);
    testWrapper(IntToLongFunction.class, Timer::wrapIntToLongFunction, c -> c.applyAsLong(1), 1L);
    testWrapper(IntUnaryOperator.class, Timer::wrapIntUnaryOperator, c -> c.applyAsInt(1), 1);
    testWrapper(LongBinaryOperator.class, Timer::wrapLongBinaryOperator, c -> c.applyAsLong(1, 2), 3L);
    testWrapper(LongConsumer.class, Timer::wrapLongConsumer, c -> c.accept(1), "");
    testWrapper(LongFunction.class, Timer::wrapLongFunction, c -> c.apply(1), 1L);
    testWrapper(LongPredicate.class, Timer::wrapLongPredicate, c -> c.test(1), false);
    testWrapper(LongSupplier.class, Timer::wrapLongSupplier, LongSupplier::getAsLong, 1L);
    testWrapper(LongToDoubleFunction.class, Timer::wrapLongToDoubleFunction, c -> c.applyAsDouble(1), 1.0);
    testWrapper(LongToIntFunction.class, Timer::wrapLongToIntFunction, c -> c.applyAsInt(1), 1);
    testWrapper(LongUnaryOperator.class, Timer::wrapLongUnaryOperator, c -> c.applyAsLong(1), 1L);
    testWrapper(ObjDoubleConsumer.class, Timer::wrapObjDoubleConsumer, c -> c.accept("", 1.0), "");
    testWrapper(ObjIntConsumer.class, Timer::wrapObjIntConsumer, c -> c.accept("", 1), "");
    testWrapper(ObjLongConsumer.class, Timer::wrapObjLongConsumer, c -> c.accept("", 1L), "");
    testWrapper(Predicate.class, Timer::wrapPredicate, c -> c.test(""), false);
    testWrapper(Supplier.class, Timer::wrapSupplier, Supplier::get, "");
    testWrapper(ToDoubleBiFunction.class, Timer::wrapToDoubleBiFunction, c -> c.applyAsDouble("", ""), 1.0);
    testWrapper(ToDoubleFunction.class, Timer::wrapToDoubleFunction, c -> c.applyAsDouble(""), 1.0);
    testWrapper(ToIntBiFunction.class, Timer::wrapToIntBiFunction, c -> c.applyAsInt("", ""), 1);
    testWrapper(ToIntFunction.class, Timer::wrapToIntFunction, c -> c.applyAsInt(""), 1);
    testWrapper(ToLongBiFunction.class, Timer::wrapToLongBiFunction, c -> c.applyAsLong("", ""), 1L);
    testWrapper(ToLongFunction.class, Timer::wrapToLongFunction, c -> c.applyAsLong(""), 1L);
    testWrapper(UnaryOperator.class, Timer::wrapUnaryOperator, c -> c.apply(""), "");
  }

  @Test
  public void wrapWithStreamApi() {
    Timer t = new DefaultTimer(clock, NoopId.INSTANCE);
    clock.setMonotonicTime(100L);

    int sum = Arrays
        .stream(new int[] {1, 2, 3, 4, 5, 6})
        .map(t.wrapIntUnaryOperator(i -> i + 1))
        .sum();
    Assertions.assertEquals(27, sum);

    sum = Arrays
        .stream(new String[] {"foo", "bar", "baz"})
        .mapToInt(t.wrapToIntFunction(String::length))
        .sum();
    Assertions.assertEquals(9, sum);
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
