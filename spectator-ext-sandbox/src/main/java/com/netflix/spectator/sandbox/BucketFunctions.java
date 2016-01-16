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
package com.netflix.spectator.sandbox;

import java.util.concurrent.TimeUnit;
import java.util.function.LongFunction;

/**
 * Helpers for creating bucketing functions.
 *
 * @deprecated Moved to {@code com.netflix.spectator.api.histogram} package. This is now just a
 * thin wrapper to preserve compatibility. Scheduled for removal after in Q3 2016.
 */
public final class BucketFunctions {

  private BucketFunctions() {
  }

  private static BucketFunction wrap(LongFunction<String> f) {
    return amount -> f.apply(amount);
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
  public static BucketFunction age(long max, TimeUnit unit) {
    return wrap(com.netflix.spectator.api.histogram.BucketFunctions.age(max, unit));
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
  public static BucketFunction latency(long max, TimeUnit unit) {
    return wrap(com.netflix.spectator.api.histogram.BucketFunctions.latency(max, unit));
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
  public static BucketFunction ageBiasOld(long max, TimeUnit unit) {
    return wrap(com.netflix.spectator.api.histogram.BucketFunctions.ageBiasOld(max, unit));
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
  public static BucketFunction latencyBiasSlow(long max, TimeUnit unit) {
    return wrap(com.netflix.spectator.api.histogram.BucketFunctions.latencyBiasSlow(max, unit));
  }
}
