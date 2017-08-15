/*
 * Copyright 2014-2017 Netflix, Inc.
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
 * A meter with a single value that can only be sampled at a point in time. A typical example is
 * a queue size.
 */
public interface Gauge extends Meter {

  /**
   * Set the current value of the gauge.
   *
   * @param value
   *     Most recent measured value.
   */
  default void set(double value) {
    // For backwards compatibility with older versions of spectator prior to set being
    // required on the gauge. Default implementation should be removed in a future release.
  }

  /** Returns the current value. */
  double value();
}
