/**
 * Copyright 2014 Netflix, Inc.
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

/**
 * Boolean valued function for a single argument. Basically a poor man's substitute for
 * {@link java.util.function.Predicate} as we still need to support java 7.
 */
public interface Predicate<T> {
  /**
   * Evaluates this predicate on a given value.
   *
   * @param value
   *     Input value to test.
   * @return
   *     True if the input matches.
   */
  boolean apply(T value);
}
