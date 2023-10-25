/*
 * Copyright 2014-2023 Netflix, Inc.
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

import java.time.Duration;
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

/**
 * Timer intended to track a large number of short running events. Example would be something like
 * an http request. Though "short running" is a bit subjective the assumption is that it should be
 * under a minute.
 *
 * <p>The precise set of information maintained by the timer depends on the implementation. Most
 * should try to provide a consistent implementation of {@link #count()} and {@link #totalTime()},
 * but some implementations may not. In particular, the implementation from {@link NoopRegistry}
 * will always return 0.
 */
public interface Timer extends Meter {

  /**
   * The clock used for timing events.
   */
  default Clock clock() {
    return Clock.SYSTEM;
  }

  /**
   * Updates the statistics kept by the timer with the specified amount.
   *
   * @param amount
   *     Duration of a single event being measured by this timer. If the amount is less than 0
   *     the value will be dropped.
   * @param unit
   *     Time unit for the amount being recorded.
   */
  void record(long amount, TimeUnit unit);

  /**
   * Updates the statistics kept by the timer with the specified amount.
   *
   * @param amount
   *     Duration of a single event being measured by this timer.
   */
  default void record(Duration amount) {
    record(amount.toNanos(), TimeUnit.NANOSECONDS);
  }

  /**
   * Updates the statistics kept by the timer with the specified amounts. Behaves as if
   * `record()` was called in a loop, but may be faster in some cases.
   *
   * @param amounts
   *     Duration of events being measured by this timer. If the amount is less than 0
   *     for a value, the value will be dropped.
   * @param n
   *     The number of elements to write from the amounts array (starting from 0). If n is
   *     &lt;= 0 no entries will be recorded. If n is greater than amounts.length, all amounts
   *     will be recorded.
   * @param unit
   *     Time unit for the amounts being recorded.
   *
   * @see #record(long, TimeUnit)
   */
  default void record(long[] amounts, int n, TimeUnit unit) {
    final int limit = Math.min(amounts.length, n);
    for (int i = 0; i < limit; i++) {
      record(amounts[i], unit);
    }
  }

  /**
   * Updates the statistics kept by the timer with the specified amounts. Behaves as if
   * `record()` was called in a loop, but may be faster in some cases.
   *
   * @param amounts
   *     Duration of events being measured by this timer. If the amount is less than 0
   *     for a value, the value will be dropped.
   * @param n
   *     The number of elements to write from the amounts array (starting from 0). If n is
   *     <= 0 no entries will be recorded. If n is greater than amounts.length, all amounts
   *     will be recorded.
   *
   * @see #record(Duration)
   */
  default void record(Duration[] amounts, int n) {
    final int limit = Math.min(amounts.length, n);
    for (int i = 0; i < limit; i++) {
      record(amounts[i]);
    }
  }

  /**
   * Executes the callable `f` and records the time taken.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     The return value of `f`.
   *
   * @deprecated Use {@link #recordCallable(Callable)} instead.
   */
  @Deprecated
  default <T> T record(Callable<T> f) throws Exception {
    return recordCallable(f);
  }

  /**
   * Executes the runnable `f` and records the time taken.
   *
   * @param f
   *     Function to execute and measure the execution time.
   *
   * @deprecated Use {@link #recordRunnable(Runnable)} instead.
   */
  @Deprecated
  default void record(Runnable f) {
    recordRunnable(f);
  }

  /**
   * Executes the callable `f` and records the time taken.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     The return value of `f`.
   */
  default <T> T recordCallable(Callable<T> f) throws Exception {
    final Clock clock = clock();
    long s = clock.monotonicTime();
    try {
      return f.call();
    } finally {
      long e = clock.monotonicTime();
      record(e - s, TimeUnit.NANOSECONDS);
    }
  }

  /**
   * Executes the runnable `f` and records the time taken.
   *
   * @param f
   *     Function to execute and measure the execution time.
   */
  default void recordRunnable(Runnable f) {
    final Clock clock = clock();
    long s = clock.monotonicTime();
    try {
      f.run();
    } finally {
      long e = clock.monotonicTime();
      record(e - s, TimeUnit.NANOSECONDS);
    }
  }

  /**
   * Executes the supplier `f` and records the time taken.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     The return value of `f`.
   */
  default <T> T recordSupplier(Supplier<T> f) {
    final Clock clock = clock();
    long s = clock.monotonicTime();
    try {
      return f.get();
    } finally {
      long e = clock.monotonicTime();
      record(e - s, TimeUnit.NANOSECONDS);
    }
  }

  /**
   * Executes the supplier `f` and records the time taken.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     The return value of `f`.
   */
  default boolean recordBooleanSupplier(BooleanSupplier f) {
    final Clock clock = clock();
    long s = clock.monotonicTime();
    try {
      return f.getAsBoolean();
    } finally {
      long e = clock.monotonicTime();
      record(e - s, TimeUnit.NANOSECONDS);
    }
  }

  /**
   * Executes the supplier `f` and records the time taken.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     The return value of `f`.
   */
  default double recordDoubleSupplier(DoubleSupplier f) {
    final Clock clock = clock();
    long s = clock.monotonicTime();
    try {
      return f.getAsDouble();
    } finally {
      long e = clock.monotonicTime();
      record(e - s, TimeUnit.NANOSECONDS);
    }
  }

  /**
   * Executes the supplier `f` and records the time taken.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     The return value of `f`.
   */
  default int recordIntSupplier(IntSupplier f) {
    final Clock clock = clock();
    long s = clock.monotonicTime();
    try {
      return f.getAsInt();
    } finally {
      long e = clock.monotonicTime();
      record(e - s, TimeUnit.NANOSECONDS);
    }
  }

  /**
   * Executes the supplier `f` and records the time taken.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     The return value of `f`.
   */
  default long recordLongSupplier(LongSupplier f) {
    final Clock clock = clock();
    long s = clock.monotonicTime();
    try {
      return f.getAsLong();
    } finally {
      long e = clock.monotonicTime();
      record(e - s, TimeUnit.NANOSECONDS);
    }
  }

  /**
   * Wraps the lambda `f` to update this timer each time the wrapper is invoked.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     Wrapper that invokes `f` and records the time taken.
   */
  default <V> Callable<V> wrapCallable(Callable<V> f) {
    return () -> {
      final Clock clock = clock();
      long s = clock.monotonicTime();
      try {
        return f.call();
      } finally {
        long e = clock.monotonicTime();
        record(e - s, TimeUnit.NANOSECONDS);
      }
    };
  }

  /**
   * Wraps the lambda `f` to update this timer each time the wrapper is invoked.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     Wrapper that invokes `f` and records the time taken.
   */
  default Runnable wrapRunnable(Runnable f) {
    return () -> {
      final Clock clock = clock();
      long s = clock.monotonicTime();
      try {
        f.run();
      } finally {
        long e = clock.monotonicTime();
        record(e - s, TimeUnit.NANOSECONDS);
      }
    };
  }

  /**
   * Wraps the lambda `f` to update this timer each time the wrapper is invoked.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     Wrapper that invokes `f` and records the time taken.
   */
  default <T, U> BiConsumer<T, U> wrapBiConsumer(BiConsumer<T, U> f) {
    return (t, u) -> {
      final Clock clock = clock();
      long s = clock.monotonicTime();
      try {
        f.accept(t, u);
      } finally {
        long e = clock.monotonicTime();
        record(e - s, TimeUnit.NANOSECONDS);
      }
    };
  }

  /**
   * Wraps the lambda `f` to update this timer each time the wrapper is invoked.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     Wrapper that invokes `f` and records the time taken.
   */
  default <T, U, R> BiFunction<T, U, R> wrapBiFunction(BiFunction<T, U, R> f) {
    return (t, u) -> {
      final Clock clock = clock();
      long s = clock.monotonicTime();
      try {
        return f.apply(t, u);
      } finally {
        long e = clock.monotonicTime();
        record(e - s, TimeUnit.NANOSECONDS);
      }
    };
  }

  /**
   * Wraps the lambda `f` to update this timer each time the wrapper is invoked.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     Wrapper that invokes `f` and records the time taken.
   */
  default <T> BinaryOperator<T> wrapBinaryOperator(BinaryOperator<T> f) {
    return (t, u) -> {
      final Clock clock = clock();
      long s = clock.monotonicTime();
      try {
        return f.apply(t, u);
      } finally {
        long e = clock.monotonicTime();
        record(e - s, TimeUnit.NANOSECONDS);
      }
    };
  }

  /**
   * Wraps the lambda `f` to update this timer each time the wrapper is invoked.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     Wrapper that invokes `f` and records the time taken.
   */
  default <T, U> BiPredicate<T, U> wrapBiPredicate(BiPredicate<T, U> f) {
    return (t, u) -> {
      final Clock clock = clock();
      long s = clock.monotonicTime();
      try {
        return f.test(t, u);
      } finally {
        long e = clock.monotonicTime();
        record(e - s, TimeUnit.NANOSECONDS);
      }
    };
  }

  /**
   * Wraps the lambda `f` to update this timer each time the wrapper is invoked.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     Wrapper that invokes `f` and records the time taken.
   */
  default BooleanSupplier wrapBooleanSupplier(BooleanSupplier f) {
    return () -> {
      final Clock clock = clock();
      long s = clock.monotonicTime();
      try {
        return f.getAsBoolean();
      } finally {
        long e = clock.monotonicTime();
        record(e - s, TimeUnit.NANOSECONDS);
      }
    };
  }

  /**
   * Wraps the lambda `f` to update this timer each time the wrapper is invoked.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     Wrapper that invokes `f` and records the time taken.
   */
  default <T> Consumer<T> wrapConsumer(Consumer<T> f) {
    return (t) -> {
      final Clock clock = clock();
      long s = clock.monotonicTime();
      try {
        f.accept(t);
      } finally {
        long e = clock.monotonicTime();
        record(e - s, TimeUnit.NANOSECONDS);
      }
    };
  }

  /**
   * Wraps the lambda `f` to update this timer each time the wrapper is invoked.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     Wrapper that invokes `f` and records the time taken.
   */
  default DoubleBinaryOperator wrapDoubleBinaryOperator(DoubleBinaryOperator f) {
    return (t, u) -> {
      final Clock clock = clock();
      long s = clock.monotonicTime();
      try {
        return f.applyAsDouble(t, u);
      } finally {
        long e = clock.monotonicTime();
        record(e - s, TimeUnit.NANOSECONDS);
      }
    };
  }

  /**
   * Wraps the lambda `f` to update this timer each time the wrapper is invoked.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     Wrapper that invokes `f` and records the time taken.
   */
  default DoubleConsumer wrapDoubleConsumer(DoubleConsumer f) {
    return (t) -> {
      final Clock clock = clock();
      long s = clock.monotonicTime();
      try {
        f.accept(t);
      } finally {
        long e = clock.monotonicTime();
        record(e - s, TimeUnit.NANOSECONDS);
      }
    };
  }

  /**
   * Wraps the lambda `f` to update this timer each time the wrapper is invoked.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     Wrapper that invokes `f` and records the time taken.
   */
  default <R> java.util.function.DoubleFunction<R> wrapDoubleFunction(java.util.function.DoubleFunction<R> f) {
    return (t) -> {
      final Clock clock = clock();
      long s = clock.monotonicTime();
      try {
        return f.apply(t);
      } finally {
        long e = clock.monotonicTime();
        record(e - s, TimeUnit.NANOSECONDS);
      }
    };
  }

  /**
   * Wraps the lambda `f` to update this timer each time the wrapper is invoked.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     Wrapper that invokes `f` and records the time taken.
   */
  default DoublePredicate wrapDoublePredicate(DoublePredicate f) {
    return (t) -> {
      final Clock clock = clock();
      long s = clock.monotonicTime();
      try {
        return f.test(t);
      } finally {
        long e = clock.monotonicTime();
        record(e - s, TimeUnit.NANOSECONDS);
      }
    };
  }

  /**
   * Wraps the lambda `f` to update this timer each time the wrapper is invoked.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     Wrapper that invokes `f` and records the time taken.
   */
  default DoubleSupplier wrapDoubleSupplier(DoubleSupplier f) {
    return () -> {
      final Clock clock = clock();
      long s = clock.monotonicTime();
      try {
        return f.getAsDouble();
      } finally {
        long e = clock.monotonicTime();
        record(e - s, TimeUnit.NANOSECONDS);
      }
    };
  }

  /**
   * Wraps the lambda `f` to update this timer each time the wrapper is invoked.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     Wrapper that invokes `f` and records the time taken.
   */
  default DoubleToIntFunction wrapDoubleToIntFunction(DoubleToIntFunction f) {
    return (t) -> {
      final Clock clock = clock();
      long s = clock.monotonicTime();
      try {
        return f.applyAsInt(t);
      } finally {
        long e = clock.monotonicTime();
        record(e - s, TimeUnit.NANOSECONDS);
      }
    };
  }

  /**
   * Wraps the lambda `f` to update this timer each time the wrapper is invoked.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     Wrapper that invokes `f` and records the time taken.
   */
  default DoubleToLongFunction wrapDoubleToLongFunction(DoubleToLongFunction f) {
    return (t) -> {
      final Clock clock = clock();
      long s = clock.monotonicTime();
      try {
        return f.applyAsLong(t);
      } finally {
        long e = clock.monotonicTime();
        record(e - s, TimeUnit.NANOSECONDS);
      }
    };
  }

  /**
   * Wraps the lambda `f` to update this timer each time the wrapper is invoked.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     Wrapper that invokes `f` and records the time taken.
   */
  default DoubleUnaryOperator wrapDoubleUnaryOperator(DoubleUnaryOperator f) {
    return (t) -> {
      final Clock clock = clock();
      long s = clock.monotonicTime();
      try {
        return f.applyAsDouble(t);
      } finally {
        long e = clock.monotonicTime();
        record(e - s, TimeUnit.NANOSECONDS);
      }
    };
  }

  /**
   * Wraps the lambda `f` to update this timer each time the wrapper is invoked.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     Wrapper that invokes `f` and records the time taken.
   */
  default <T, R> Function<T, R> wrapFunction(Function<T, R> f) {
    return (t) -> {
      final Clock clock = clock();
      long s = clock.monotonicTime();
      try {
        return f.apply(t);
      } finally {
        long e = clock.monotonicTime();
        record(e - s, TimeUnit.NANOSECONDS);
      }
    };
  }

  /**
   * Wraps the lambda `f` to update this timer each time the wrapper is invoked.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     Wrapper that invokes `f` and records the time taken.
   */
  default IntBinaryOperator wrapIntBinaryOperator(IntBinaryOperator f) {
    return (t, u) -> {
      final Clock clock = clock();
      long s = clock.monotonicTime();
      try {
        return f.applyAsInt(t, u);
      } finally {
        long e = clock.monotonicTime();
        record(e - s, TimeUnit.NANOSECONDS);
      }
    };
  }

  /**
   * Wraps the lambda `f` to update this timer each time the wrapper is invoked.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     Wrapper that invokes `f` and records the time taken.
   */
  default IntConsumer wrapIntConsumer(IntConsumer f) {
    return (t) -> {
      final Clock clock = clock();
      long s = clock.monotonicTime();
      try {
        f.accept(t);
      } finally {
        long e = clock.monotonicTime();
        record(e - s, TimeUnit.NANOSECONDS);
      }
    };
  }

  /**
   * Wraps the lambda `f` to update this timer each time the wrapper is invoked.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     Wrapper that invokes `f` and records the time taken.
   */
  default <R> IntFunction<R> wrapIntFunction(IntFunction<R> f) {
    return (t) -> {
      final Clock clock = clock();
      long s = clock.monotonicTime();
      try {
        return f.apply(t);
      } finally {
        long e = clock.monotonicTime();
        record(e - s, TimeUnit.NANOSECONDS);
      }
    };
  }

  /**
   * Wraps the lambda `f` to update this timer each time the wrapper is invoked.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     Wrapper that invokes `f` and records the time taken.
   */
  default IntPredicate wrapIntPredicate(IntPredicate f) {
    return (t) -> {
      final Clock clock = clock();
      long s = clock.monotonicTime();
      try {
        return f.test(t);
      } finally {
        long e = clock.monotonicTime();
        record(e - s, TimeUnit.NANOSECONDS);
      }
    };
  }

  /**
   * Wraps the lambda `f` to update this timer each time the wrapper is invoked.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     Wrapper that invokes `f` and records the time taken.
   */
  default IntSupplier wrapIntSupplier(IntSupplier f) {
    return () -> {
      final Clock clock = clock();
      long s = clock.monotonicTime();
      try {
        return f.getAsInt();
      } finally {
        long e = clock.monotonicTime();
        record(e - s, TimeUnit.NANOSECONDS);
      }
    };
  }

  /**
   * Wraps the lambda `f` to update this timer each time the wrapper is invoked.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     Wrapper that invokes `f` and records the time taken.
   */
  default IntToDoubleFunction wrapIntToDoubleFunction(IntToDoubleFunction f) {
    return (t) -> {
      final Clock clock = clock();
      long s = clock.monotonicTime();
      try {
        return f.applyAsDouble(t);
      } finally {
        long e = clock.monotonicTime();
        record(e - s, TimeUnit.NANOSECONDS);
      }
    };
  }

  /**
   * Wraps the lambda `f` to update this timer each time the wrapper is invoked.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     Wrapper that invokes `f` and records the time taken.
   */
  default IntToLongFunction wrapIntToLongFunction(IntToLongFunction f) {
    return (t) -> {
      final Clock clock = clock();
      long s = clock.monotonicTime();
      try {
        return f.applyAsLong(t);
      } finally {
        long e = clock.monotonicTime();
        record(e - s, TimeUnit.NANOSECONDS);
      }
    };
  }

  /**
   * Wraps the lambda `f` to update this timer each time the wrapper is invoked.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     Wrapper that invokes `f` and records the time taken.
   */
  default IntUnaryOperator wrapIntUnaryOperator(IntUnaryOperator f) {
    return (t) -> {
      final Clock clock = clock();
      long s = clock.monotonicTime();
      try {
        return f.applyAsInt(t);
      } finally {
        long e = clock.monotonicTime();
        record(e - s, TimeUnit.NANOSECONDS);
      }
    };
  }

  /**
   * Wraps the lambda `f` to update this timer each time the wrapper is invoked.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     Wrapper that invokes `f` and records the time taken.
   */
  default LongBinaryOperator wrapLongBinaryOperator(LongBinaryOperator f) {
    return (t, u) -> {
      final Clock clock = clock();
      long s = clock.monotonicTime();
      try {
        return f.applyAsLong(t, u);
      } finally {
        long e = clock.monotonicTime();
        record(e - s, TimeUnit.NANOSECONDS);
      }
    };
  }

  /**
   * Wraps the lambda `f` to update this timer each time the wrapper is invoked.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     Wrapper that invokes `f` and records the time taken.
   */
  default LongConsumer wrapLongConsumer(LongConsumer f) {
    return (t) -> {
      final Clock clock = clock();
      long s = clock.monotonicTime();
      try {
        f.accept(t);
      } finally {
        long e = clock.monotonicTime();
        record(e - s, TimeUnit.NANOSECONDS);
      }
    };
  }

  /**
   * Wraps the lambda `f` to update this timer each time the wrapper is invoked.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     Wrapper that invokes `f` and records the time taken.
   */
  default <R> LongFunction<R> wrapLongFunction(LongFunction<R> f) {
    return (t) -> {
      final Clock clock = clock();
      long s = clock.monotonicTime();
      try {
        return f.apply(t);
      } finally {
        long e = clock.monotonicTime();
        record(e - s, TimeUnit.NANOSECONDS);
      }
    };
  }

  /**
   * Wraps the lambda `f` to update this timer each time the wrapper is invoked.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     Wrapper that invokes `f` and records the time taken.
   */
  default LongPredicate wrapLongPredicate(LongPredicate f) {
    return (t) -> {
      final Clock clock = clock();
      long s = clock.monotonicTime();
      try {
        return f.test(t);
      } finally {
        long e = clock.monotonicTime();
        record(e - s, TimeUnit.NANOSECONDS);
      }
    };
  }

  /**
   * Wraps the lambda `f` to update this timer each time the wrapper is invoked.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     Wrapper that invokes `f` and records the time taken.
   */
  default LongSupplier wrapLongSupplier(LongSupplier f) {
    return () -> {
      final Clock clock = clock();
      long s = clock.monotonicTime();
      try {
        return f.getAsLong();
      } finally {
        long e = clock.monotonicTime();
        record(e - s, TimeUnit.NANOSECONDS);
      }
    };
  }

  /**
   * Wraps the lambda `f` to update this timer each time the wrapper is invoked.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     Wrapper that invokes `f` and records the time taken.
   */
  default LongToIntFunction wrapLongToIntFunction(LongToIntFunction f) {
    return (t) -> {
      final Clock clock = clock();
      long s = clock.monotonicTime();
      try {
        return f.applyAsInt(t);
      } finally {
        long e = clock.monotonicTime();
        record(e - s, TimeUnit.NANOSECONDS);
      }
    };
  }

  /**
   * Wraps the lambda `f` to update this timer each time the wrapper is invoked.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     Wrapper that invokes `f` and records the time taken.
   */
  default LongToDoubleFunction wrapLongToDoubleFunction(LongToDoubleFunction f) {
    return (t) -> {
      final Clock clock = clock();
      long s = clock.monotonicTime();
      try {
        return f.applyAsDouble(t);
      } finally {
        long e = clock.monotonicTime();
        record(e - s, TimeUnit.NANOSECONDS);
      }
    };
  }

  /**
   * Wraps the lambda `f` to update this timer each time the wrapper is invoked.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     Wrapper that invokes `f` and records the time taken.
   */
  default LongUnaryOperator wrapLongUnaryOperator(LongUnaryOperator f) {
    return (t) -> {
      final Clock clock = clock();
      long s = clock.monotonicTime();
      try {
        return f.applyAsLong(t);
      } finally {
        long e = clock.monotonicTime();
        record(e - s, TimeUnit.NANOSECONDS);
      }
    };
  }

  /**
   * Wraps the lambda `f` to update this timer each time the wrapper is invoked.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     Wrapper that invokes `f` and records the time taken.
   */
  default <T> ObjDoubleConsumer<T> wrapObjDoubleConsumer(ObjDoubleConsumer<T> f) {
    return (t, u) -> {
      final Clock clock = clock();
      long s = clock.monotonicTime();
      try {
        f.accept(t, u);
      } finally {
        long e = clock.monotonicTime();
        record(e - s, TimeUnit.NANOSECONDS);
      }
    };
  }

  /**
   * Wraps the lambda `f` to update this timer each time the wrapper is invoked.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     Wrapper that invokes `f` and records the time taken.
   */
  default <T> ObjIntConsumer<T> wrapObjIntConsumer(ObjIntConsumer<T> f) {
    return (t, u) -> {
      final Clock clock = clock();
      long s = clock.monotonicTime();
      try {
        f.accept(t, u);
      } finally {
        long e = clock.monotonicTime();
        record(e - s, TimeUnit.NANOSECONDS);
      }
    };
  }

  /**
   * Wraps the lambda `f` to update this timer each time the wrapper is invoked.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     Wrapper that invokes `f` and records the time taken.
   */
  default <T> ObjLongConsumer<T> wrapObjLongConsumer(ObjLongConsumer<T> f) {
    return (t, u) -> {
      final Clock clock = clock();
      long s = clock.monotonicTime();
      try {
        f.accept(t, u);
      } finally {
        long e = clock.monotonicTime();
        record(e - s, TimeUnit.NANOSECONDS);
      }
    };
  }

  /**
   * Wraps the lambda `f` to update this timer each time the wrapper is invoked.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     Wrapper that invokes `f` and records the time taken.
   */
  default <T> Predicate<T> wrapPredicate(Predicate<T> f) {
    return (t) -> {
      final Clock clock = clock();
      long s = clock.monotonicTime();
      try {
        return f.test(t);
      } finally {
        long e = clock.monotonicTime();
        record(e - s, TimeUnit.NANOSECONDS);
      }
    };
  }

  /**
   * Wraps the lambda `f` to update this timer each time the wrapper is invoked.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     Wrapper that invokes `f` and records the time taken.
   */
  default <T> Supplier<T> wrapSupplier(Supplier<T> f) {
    return () -> {
      final Clock clock = clock();
      long s = clock.monotonicTime();
      try {
        return f.get();
      } finally {
        long e = clock.monotonicTime();
        record(e - s, TimeUnit.NANOSECONDS);
      }
    };
  }

  /**
   * Wraps the lambda `f` to update this timer each time the wrapper is invoked.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     Wrapper that invokes `f` and records the time taken.
   */
  default <T, U> ToDoubleBiFunction<T, U> wrapToDoubleBiFunction(ToDoubleBiFunction<T, U> f) {
    return (t, u) -> {
      final Clock clock = clock();
      long s = clock.monotonicTime();
      try {
        return f.applyAsDouble(t, u);
      } finally {
        long e = clock.monotonicTime();
        record(e - s, TimeUnit.NANOSECONDS);
      }
    };
  }

  /**
   * Wraps the lambda `f` to update this timer each time the wrapper is invoked.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     Wrapper that invokes `f` and records the time taken.
   */
  default <T> ToDoubleFunction<T> wrapToDoubleFunction(ToDoubleFunction<T> f) {
    return (t) -> {
      final Clock clock = clock();
      long s = clock.monotonicTime();
      try {
        return f.applyAsDouble(t);
      } finally {
        long e = clock.monotonicTime();
        record(e - s, TimeUnit.NANOSECONDS);
      }
    };
  }

  /**
   * Wraps the lambda `f` to update this timer each time the wrapper is invoked.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     Wrapper that invokes `f` and records the time taken.
   */
  default <T, U> ToIntBiFunction<T, U> wrapToIntBiFunction(ToIntBiFunction<T, U> f) {
    return (t, u) -> {
      final Clock clock = clock();
      long s = clock.monotonicTime();
      try {
        return f.applyAsInt(t, u);
      } finally {
        long e = clock.monotonicTime();
        record(e - s, TimeUnit.NANOSECONDS);
      }
    };
  }

  /**
   * Wraps the lambda `f` to update this timer each time the wrapper is invoked.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     Wrapper that invokes `f` and records the time taken.
   */
  default <T> ToIntFunction<T> wrapToIntFunction(ToIntFunction<T> f) {
    return (t) -> {
      final Clock clock = clock();
      long s = clock.monotonicTime();
      try {
        return f.applyAsInt(t);
      } finally {
        long e = clock.monotonicTime();
        record(e - s, TimeUnit.NANOSECONDS);
      }
    };
  }

  /**
   * Wraps the lambda `f` to update this timer each time the wrapper is invoked.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     Wrapper that invokes `f` and records the time taken.
   */
  default <T, U> ToLongBiFunction<T, U> wrapToLongBiFunction(ToLongBiFunction<T, U> f) {
    return (t, u) -> {
      final Clock clock = clock();
      long s = clock.monotonicTime();
      try {
        return f.applyAsLong(t, u);
      } finally {
        long e = clock.monotonicTime();
        record(e - s, TimeUnit.NANOSECONDS);
      }
    };
  }

  /**
   * Wraps the lambda `f` to update this timer each time the wrapper is invoked.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     Wrapper that invokes `f` and records the time taken.
   */
  default <T> ToLongFunction<T> wrapToLongFunction(ToLongFunction<T> f) {
    return (t) -> {
      final Clock clock = clock();
      long s = clock.monotonicTime();
      try {
        return f.applyAsLong(t);
      } finally {
        long e = clock.monotonicTime();
        record(e - s, TimeUnit.NANOSECONDS);
      }
    };
  }

  /**
   * Wraps the lambda `f` to update this timer each time the wrapper is invoked.
   *
   * @param f
   *     Function to execute and measure the execution time.
   * @return
   *     Wrapper that invokes `f` and records the time taken.
   */
  default <T> UnaryOperator<T> wrapUnaryOperator(UnaryOperator<T> f) {
    return (t) -> {
      final Clock clock = clock();
      long s = clock.monotonicTime();
      try {
        return f.apply(t);
      } finally {
        long e = clock.monotonicTime();
        record(e - s, TimeUnit.NANOSECONDS);
      }
    };
  }

  /**
   * The number of times that record has been called since this timer was last reset.
   * How often a timer is reset depends on the underlying registry implementation.
   */
  long count();

  /**
   * The total time in nanoseconds of all recorded events since this timer was last reset.
   * How often a timer is reset depends on the underlying registry implementation.
   */
  long totalTime();

  /**
   * Returns a helper that can be used to more efficiently update the timer within a
   * single thread. For example, if you need to update a meter within a loop where the
   * rest of the loop body is fairly cheap, the instrumentation code may add considerable
   * overhead if done in the loop body. A batched updater can offset a fair amount of that
   * cost, but the updates may be delayed a bit in reaching the meter. The updates will only
   * be seen after the updater is explicitly flushed.
   *
   * <p>The caller should ensure that the updater is closed after using to guarantee any resources
   * associated with it are cleaned up. In some cases failure to close the updater could result
   * in a memory leak.
   *
   * @param batchSize
   *     Number of updates to batch before forcing a flush to the meter.
   * @return
   *     Batch updater implementation for this meter.
   */
  default BatchUpdater batchUpdater(int batchSize) {
    return new TimerBatchUpdater(this, batchSize);
  }

  /** See {@link #batchUpdater(int)}. */
  interface BatchUpdater extends AutoCloseable {
    /**
     * Updates the statistics kept by the timer with the specified amount.
     *
     * @param amount
     *     Duration of a single event being measured by this timer. If the amount is less than 0
     *     the value will be dropped.
     * @param unit
     *     Time unit for the amount being recorded.
     */
    void record(long amount, TimeUnit unit);

    /**
     * Updates the statistics kept by the timer with the specified amount.
     *
     * @param amount
     *     Duration of a single event being measured by this timer.
     */
    default void record(Duration amount) {
      record(amount.toNanos(), TimeUnit.NANOSECONDS);
    }

    /** Push updates to the associated timer. */
    void flush();
  }
}
