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
package com.netflix.spectator.api;

import java.util.Collections;

/** Counter implementation for the no-op registry. */
enum NoopCounter implements Counter {
  /** Singleton instance. */
  INSTANCE;

  @Override public Id id() {
    return NoopId.INSTANCE;
  }

  @Override public boolean hasExpired() {
    return false;
  }

  @Override public void add(double amount) {
  }

  @Override public Iterable<Measurement> measure() {
    return Collections.emptyList();
  }

  @Override public double actualCount() {
    return 0.0;
  }
}
