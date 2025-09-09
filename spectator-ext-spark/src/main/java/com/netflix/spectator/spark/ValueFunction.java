/*
 * Copyright 2014-2019 Netflix, Inc.
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
package com.netflix.spectator.spark;

/**
 * Maps a value based on the name. This is typically used to perform simple unit conversions so
 * that data can be made to follow common conventions with other sources (e.g. always use base
 * units and do conversions as part of presentation).
 */
@FunctionalInterface
public interface ValueFunction {
  /** Convert the value for a given metric name. */
  double convert(String name, double v);
}
