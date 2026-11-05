/*
 * Copyright 2014-2026 Netflix, Inc.
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

/**
 * Helpers used by the percentile meters to drop negative amounts. The percentile meters
 * filter negative amounts before updating either the underlying meter or the buckets, so
 * that the count of the underlying meter is consistent with the sum of the bucket counts
 * regardless of how the underlying meter handles negative values.
 */
final class NonNegativeAmounts {

  private NonNegativeAmounts() {
  }

  /**
   * Number of entries that should be recorded for a batch. If n is less than 0 then no
   * entries will be recorded. If n is greater than the length of the array, then all
   * entries will be recorded.
   */
  static int limit(long[] amounts, int n) {
    return Math.max(0, Math.min(amounts.length, n));
  }

  /**
   * Filter negative values from the first {@code limit} entries of the array. If there are
   * no negative values, then the input array will be returned. Otherwise, a new array with
   * just the non-negative values will be returned. Negative values are expected to be rare,
   * so the common case will not allocate.
   *
   * @param amounts
   *     Amounts to filter.
   * @param limit
   *     Number of entries to consider, see {@link #limit(long[], int)}.
   * @return
   *     The input array if the first {@code limit} entries are all non-negative, otherwise
   *     a new array with exactly the non-negative values.
   */
  static long[] filter(long[] amounts, int limit) {
    int negatives = 0;
    for (int i = 0; i < limit; ++i) {
      if (amounts[i] < 0L) {
        ++negatives;
      }
    }

    if (negatives == 0) {
      return amounts;
    }

    long[] result = new long[limit - negatives];
    int j = 0;
    for (int i = 0; i < limit; ++i) {
      if (amounts[i] >= 0L) {
        result[j++] = amounts[i];
      }
    }
    return result;
  }
}
