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
package com.netflix.spectator.api;

import java.util.function.ToDoubleFunction;

/**
 * Function to extract a double value from an object.
 *
 * @deprecated Use {@link ToDoubleFunction} instead.
 */
@Deprecated
public interface ValueFunction<T> extends ToDoubleFunction<T> {
  /**
   * Returns a double value based on the object {@code ref}.
   *
   * @param ref
   *     An object to use for extracting the value.
   * @return
   *     Double value based on the object.
   */
  double apply(T ref);

  @Override default double applyAsDouble(T ref) {
    return apply(ref);
  }
}
