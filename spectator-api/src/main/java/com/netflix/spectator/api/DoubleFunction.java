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
 * Function to extract a double value from an object.
 */
public abstract class DoubleFunction implements ValueFunction {

  @Override public double apply(Object obj) {
    return (obj instanceof Number)
      ? apply(((Number) obj).doubleValue())
      : Double.NaN;
  }

  /**
   * Apply a transform to the value `v`.
   *
   * @param v
   *     Double value to transform.
   * @return
   *     Result of applying this function to `v`.
   */
  public abstract double apply(double v);
}
