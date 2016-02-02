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
package com.netflix.spectator.api.histogram;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.LongFunction;
import java.util.function.LongUnaryOperator;

/**
 * Helpers for creating bucketing functions.
 */
public final class BucketFunctions {

  /**
   * Predefined time formatters used to create the bucket labels.
   */
  static final List<ValueFormatter> TIME_FORMATTERS = new ArrayList<>();

  /**
   * Predefined binary formatters used to create bucket labels. For more information see
   * <a href="https://en.wikipedia.org/wiki/Binary_prefix">binary prefixes wiki</a>.
   */
  static final List<ValueFormatter> BINARY_FORMATTERS = new ArrayList<>();

  /**
   * Predefined decimal formatters used to created bucket labels. For more informations see
   * <a href="https://en.wikipedia.org/wiki/Metric_prefix">metric prefixes wiki</a>.
   */
  static final List<ValueFormatter> DECIMAL_FORMATTERS = new ArrayList<>();

  static {
    TIME_FORMATTERS.add(fmt(TimeUnit.NANOSECONDS.toNanos(10),     1, "ns",  TimeUnit.NANOSECONDS));
    TIME_FORMATTERS.add(fmt(TimeUnit.NANOSECONDS.toNanos(100),    2, "ns",  TimeUnit.NANOSECONDS));
    TIME_FORMATTERS.add(fmt(TimeUnit.MICROSECONDS.toNanos(1),     3, "ns",  TimeUnit.NANOSECONDS));
    TIME_FORMATTERS.add(fmt(TimeUnit.MICROSECONDS.toNanos(8),     4, "ns",  TimeUnit.NANOSECONDS));
    TIME_FORMATTERS.add(fmt(TimeUnit.MICROSECONDS.toNanos(10),    1, "us",  TimeUnit.MICROSECONDS));
    TIME_FORMATTERS.add(fmt(TimeUnit.MICROSECONDS.toNanos(100),   2, "us",  TimeUnit.MICROSECONDS));
    TIME_FORMATTERS.add(fmt(TimeUnit.MILLISECONDS.toNanos(1),     3, "us",  TimeUnit.MICROSECONDS));
    TIME_FORMATTERS.add(fmt(TimeUnit.MILLISECONDS.toNanos(8),     4, "us",  TimeUnit.MICROSECONDS));
    TIME_FORMATTERS.add(fmt(TimeUnit.MILLISECONDS.toNanos(10),    1, "ms",  TimeUnit.MILLISECONDS));
    TIME_FORMATTERS.add(fmt(TimeUnit.MILLISECONDS.toNanos(100),   2, "ms",  TimeUnit.MILLISECONDS));
    TIME_FORMATTERS.add(fmt(TimeUnit.SECONDS.toNanos(1),          3, "ms",  TimeUnit.MILLISECONDS));
    TIME_FORMATTERS.add(fmt(TimeUnit.SECONDS.toNanos(8),          4, "ms",  TimeUnit.MILLISECONDS));
    TIME_FORMATTERS.add(fmt(TimeUnit.SECONDS.toNanos(10),         1, "s",   TimeUnit.SECONDS));
    TIME_FORMATTERS.add(fmt(TimeUnit.SECONDS.toNanos(100),        2, "s",   TimeUnit.SECONDS));
    TIME_FORMATTERS.add(fmt(TimeUnit.MINUTES.toNanos(8),          3, "s",   TimeUnit.SECONDS));
    TIME_FORMATTERS.add(fmt(TimeUnit.MINUTES.toNanos(10),         1, "min", TimeUnit.MINUTES));
    TIME_FORMATTERS.add(fmt(TimeUnit.MINUTES.toNanos(100),        2, "min", TimeUnit.MINUTES));
    TIME_FORMATTERS.add(fmt(TimeUnit.HOURS.toNanos(8),            3, "min", TimeUnit.MINUTES));
    TIME_FORMATTERS.add(fmt(TimeUnit.HOURS.toNanos(10),           1, "h",   TimeUnit.HOURS));
    TIME_FORMATTERS.add(fmt(TimeUnit.HOURS.toNanos(100),          2, "h",   TimeUnit.HOURS));
    TIME_FORMATTERS.add(fmt(TimeUnit.DAYS.toNanos(8),             1, "h",   TimeUnit.HOURS));
    TIME_FORMATTERS.add(fmt(TimeUnit.DAYS.toNanos(10),            1, "d",   TimeUnit.DAYS));
    TIME_FORMATTERS.add(fmt(TimeUnit.DAYS.toNanos(100),           2, "d",   TimeUnit.DAYS));
    TIME_FORMATTERS.add(fmt(TimeUnit.DAYS.toNanos(1000),          3, "d",   TimeUnit.DAYS));
    TIME_FORMATTERS.add(fmt(TimeUnit.DAYS.toNanos(10000),         4, "d",   TimeUnit.DAYS));
    TIME_FORMATTERS.add(fmt(TimeUnit.DAYS.toNanos(100000),        5, "d",   TimeUnit.DAYS));
    TIME_FORMATTERS.add(fmt(Long.MAX_VALUE,                       6, "d",   TimeUnit.DAYS));
    // TimeUnit.NANOSECONDS.toDays(java.lang.Long.MAX_VALUE) == 106751

    final String[] binaryUnits = {"B", "KiB", "MiB", "GiB", "TiB", "PiB"};
    for (int i = 0; i < binaryUnits.length; ++i) {
      BINARY_FORMATTERS.add(bin(10,    i, 1, "_" + binaryUnits[i]));
      BINARY_FORMATTERS.add(bin(100,   i, 2, "_" + binaryUnits[i]));
      BINARY_FORMATTERS.add(bin(1000,  i, 3, "_" + binaryUnits[i]));
      BINARY_FORMATTERS.add(bin(10000, i, 4, "_" + binaryUnits[i]));
    }
    BINARY_FORMATTERS.add(new ValueFormatter(Long.MAX_VALUE, 4, "_PiB", v -> v >> 50));

    final String[] decimalUnits = {"", "_k", "_M", "_G", "_T", "_P"};
    for (int i = 0; i < decimalUnits.length; ++i) {
      final int pow = i * 3;
      DECIMAL_FORMATTERS.add(dec(10,   pow, 1, decimalUnits[i]));
      DECIMAL_FORMATTERS.add(dec(100,  pow, 2, decimalUnits[i]));
      DECIMAL_FORMATTERS.add(dec(1000, pow, 3, decimalUnits[i]));
    }
    DECIMAL_FORMATTERS.add(new ValueFormatter(Long.MAX_VALUE, 1, "_E", v -> v / pow10(1, 18)));
  }

  private static ValueFormatter fmt(long max, int width, String suffix, TimeUnit unit) {
    return new ValueFormatter(max, width, suffix, v -> unit.convert(v, TimeUnit.NANOSECONDS));
  }

  private static ValueFormatter bin(long max, int pow, int width, String suffix) {
    final int shift = pow * 10;
    final long maxBytes = (shift == 0) ? max : max << shift;
    return new ValueFormatter(maxBytes, width, suffix, v -> v >> shift);
  }

  private static ValueFormatter dec(long max, int pow, int width, String suffix) {
    final long factor = pow10(1, pow);
    final long maxBytes = max * factor;
    return new ValueFormatter(maxBytes, width, suffix, v -> v / factor);
  }

  private static long pow10(long a, int b) {
    long r = a;
    for (int i = 0; i < b; ++i) {
      r *= 10;
    }
    return r;
  }

  private BucketFunctions() {
  }

  private static ValueFormatter getFormatter(List<ValueFormatter> fmts, long max) {
    for (ValueFormatter f : fmts) {
      if (max < f.max) {
        return f;
      }
    }
    return fmts.get(fmts.size() - 1);
  }

  private static LongFunction<String> biasZero(
      String ltZero, String gtMax, long max, ValueFormatter f) {
    List<Bucket> buckets = new ArrayList<>();
    buckets.add(new Bucket(ltZero, 0L));
    buckets.add(f.newBucket(max / 8));
    buckets.add(f.newBucket(max / 4));
    buckets.add(f.newBucket(max / 2));
    buckets.add(f.newBucket(max));
    return new ListBucketFunction(buckets, gtMax);
  }

  private static LongFunction<String> timeBiasZero(
      String ltZero, String gtMax, long max, TimeUnit unit) {
    final long v = unit.toNanos(max);
    final ValueFormatter f = getFormatter(TIME_FORMATTERS, v);
    return biasZero(ltZero, gtMax, v, f);
  }

  private static LongFunction<String> biasMax(
      String ltZero, String gtMax, long max, ValueFormatter f) {
    List<Bucket> buckets = new ArrayList<>();
    buckets.add(new Bucket(ltZero, 0L));
    buckets.add(f.newBucket(max - max / 2));
    buckets.add(f.newBucket(max - max / 4));
    buckets.add(f.newBucket(max - max / 8));
    buckets.add(f.newBucket(max));
    return new ListBucketFunction(buckets, gtMax);
  }

  private static LongFunction<String> timeBiasMax(
      String ltZero, String gtMax, long max, TimeUnit unit) {
    final long v = unit.toNanos(max);
    final ValueFormatter f = getFormatter(TIME_FORMATTERS, v);
    return biasMax(ltZero, gtMax, v, f);
  }

  /**
   * Returns a function that maps age values to a set of buckets. Example use-case would be
   * tracking the age of data flowing through a processing pipeline. Values that are less than
   * 0 will be marked as "future". These typically occur due to minor variations in the clocks
   * across nodes. In addition to a bucket at the max, it will create buckets at max / 2, max / 4,
   * and max / 8.
   *
   * @param max
   *     Maximum expected age of data flowing through. Values greater than this max will be mapped
   *     to an "old" bucket.
   * @param unit
   *     Unit for the max value.
   * @return
   *     Function mapping age values to string labels. The labels for buckets will sort
   *     so they can be used with a simple group by.
   */
  public static LongFunction<String> age(long max, TimeUnit unit) {
    return timeBiasZero("future", "old", max, unit);
  }

  /**
   * Returns a function that maps latencies to a set of buckets. Example use-case would be
   * tracking the amount of time to process a request on a server. Values that are less than
   * 0 will be marked as "negative_latency". These typically occur due to minor variations in the
   * clocks, e.g., using {@link System#currentTimeMillis()} to measure the latency and having a
   * time adjustment between the start and end. In addition to a bucket at the max, it will create
   * buckets at max / 2, max / 4, and max / 8.
   *
   * @param max
   *     Maximum expected age of data flowing through. Values greater than this max will be mapped
   *     to an "old" bucket.
   * @param unit
   *     Unit for the max value.
   * @return
   *     Function mapping age values to string labels. The labels for buckets will sort
   *     so they can be used with a simple group by.
   */
  public static LongFunction<String> latency(long max, TimeUnit unit) {
    return timeBiasZero("negative_latency", "slow", max, unit);
  }

  /**
   * Returns a function that maps age values to a set of buckets. Example use-case would be
   * tracking the age of data flowing through a processing pipeline. Values that are less than
   * 0 will be marked as "future". These typically occur due to minor variations in the clocks
   * across nodes. In addition to a bucket at the max, it will create buckets at max - max / 8,
   * max - max / 4, and max - max / 2.
   *
   * @param max
   *     Maximum expected age of data flowing through. Values greater than this max will be mapped
   *     to an "old" bucket.
   * @param unit
   *     Unit for the max value.
   * @return
   *     Function mapping age values to string labels. The labels for buckets will sort
   *     so they can be used with a simple group by.
   */
  public static LongFunction<String> ageBiasOld(long max, TimeUnit unit) {
    return timeBiasMax("future", "old", max, unit);
  }

  /**
   * Returns a function that maps latencies to a set of buckets. Example use-case would be
   * tracking the amount of time to process a request on a server. Values that are less than
   * 0 will be marked as "negative_latency". These typically occur due to minor variations in the
   * clocks, e.g., using {@link System#currentTimeMillis()} to measure the latency and having a
   * time adjustment between the start and end. In addition to a bucket at the max, it will create
   * buckets at max - max / 8, max - max / 4, and max - max / 2.
   *
   * @param max
   *     Maximum expected age of data flowing through. Values greater than this max will be mapped
   *     to an "old" bucket.
   * @param unit
   *     Unit for the max value.
   * @return
   *     Function mapping age values to string labels. The labels for buckets will sort
   *     so they can be used with a simple group by.
   */
  public static LongFunction<String> latencyBiasSlow(long max, TimeUnit unit) {
    return timeBiasMax("negative_latency", "slow", max, unit);
  }

  /**
   * Returns a function that maps size values in bytes to a set of buckets. The buckets will
   * use <a href="https://en.wikipedia.org/wiki/Binary_prefix">binary prefixes</a> representing
   * powers of 1024. If you want powers of 1000, then see {@link #decimal(long)}.
   *
   * @param max
   *     Maximum expected size of data being recorded. Values greater than this amount will be
   *     mapped to a "large" bucket.
   * @return
   *     Function mapping size values to string labels. The labels for buckets will sort
   *     so they can be used with a simple group by.
   */
  public static LongFunction<String> bytes(long max) {
    ValueFormatter f = getFormatter(BINARY_FORMATTERS, max);
    return biasZero("negative", "large", max, f);
  }

  /**
   * Returns a function that maps size values to a set of buckets. The buckets will
   * use <a href="https://en.wikipedia.org/wiki/Metric_prefix">decimal prefixes</a> representing
   * powers of 1000. If you are measuring quantities in bytes and want powers of 1024, then see
   * {@link #bytes(long)}.
   *
   * @param max
   *     Maximum expected size of data being recorded. Values greater than this amount will be
   *     mapped to a "large" bucket.
   * @return
   *     Function mapping size values to string labels. The labels for buckets will sort
   *     so they can be used with a simple group by.
   */
  public static LongFunction<String> decimal(long max) {
    ValueFormatter f = getFormatter(DECIMAL_FORMATTERS, max);
    return biasZero("negative", "large", max, f);
  }

  /**
   * Format a value as a bucket label.
   */
  static class ValueFormatter {
    private final long max;
    private final String fmt;
    private final LongUnaryOperator cnv;

    /**
     * Create a new instance.
     *
     * @param max
     *     Maximum value intended to be passed into the apply method. Max value is in nanoseconds.
     * @param width
     *     Number of digits to use for the numeric part of the label.
     * @param suffix
     *     Unit suffix appended to the label.
     * @param cnv
     *     Value conversion function. Converts the input value to the right units for the label.
     */
    ValueFormatter(long max, int width, String suffix, LongUnaryOperator cnv) {
      this.max = max;
      this.fmt = "%0" + width + "d" + suffix;
      this.cnv = cnv;
    }

    /** Return the max value intended for this formatter. */
    long max() {
      return max;
    }

    /** Apply conversion function to value. */
    long convert(long v) {
      return cnv.applyAsLong(v);
    }

    /** Convert the value {@code v} into a bucket label string. */
    String apply(long v) {
      return String.format(fmt, cnv.applyAsLong(v));
    }

    /** Return a new bucket for the specified value. */
    Bucket newBucket(long v) {
      return new Bucket(apply(v), v);
    }
  }

  private static class ListBucketFunction implements LongFunction<String> {
    private final List<Bucket> buckets;
    private final String fallback;

    ListBucketFunction(List<Bucket> buckets, String fallback) {
      this.buckets = buckets;
      this.fallback = fallback;
    }

    @Override public String apply(long amount) {
      for (Bucket b : buckets) {
        if (amount <= b.upperBoundary) {
          return b.name;
        }
      }
      return fallback;
    }
  }

  private static class Bucket {
    private final String name;
    private final long upperBoundary;

    Bucket(String name, long upperBoundary) {
      this.name = name;
      this.upperBoundary = upperBoundary;
    }

    @Override public String toString() {
      return String.format("Bucket(%s,%d)", name, upperBoundary);
    }
  }
}
