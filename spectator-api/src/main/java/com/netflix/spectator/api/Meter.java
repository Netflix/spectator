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

/**
 * A device for collecting a set of measurements. Note, this interface is only intended to be
 * implemented by registry implementations.
 */
public interface Meter {

  /**
   * Identifier used to lookup this meter in the registry.
   */
  Id id();

  /**
   * Get the set of measurements for this meter.
   */
  Iterable<Measurement> measure();

  /**
   * Indicates whether the meter is expired. For example, a counter might expire if there is no
   * activity within a given time frame.
   */
  boolean hasExpired();
}
