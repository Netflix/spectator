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

import com.netflix.spectator.api.Tag;

/**
 * Data types for messages sent to the sidecar metrics endpoint.
 */
public enum DataType implements Tag {

  /** Value reported as is, the most recent value received by the sidecar will get used. */
  GAUGE,

  /** Value is a delta to use when incrementing the counter. */
  COUNTER,

  /** Value is an amount in milliseconds that will be recorded on the timer. */
  TIMER;

  @Override public String key() {
    return "type";
  }

  @Override public String value() {
    return name();
  }
}
