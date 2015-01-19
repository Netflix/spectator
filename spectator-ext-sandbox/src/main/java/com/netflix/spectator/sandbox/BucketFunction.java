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

/**
 * Function to map an amount passed to a distribution summary or timer to a bucket.
 */
public interface BucketFunction {
  /**
   * Returns a bucket for the specified amount.
   *
   * @param amount
   *     Amount for an event being measured.
   * @return
   *     Bucket name to use for the amount.
   */
  String apply(long amount);
}
