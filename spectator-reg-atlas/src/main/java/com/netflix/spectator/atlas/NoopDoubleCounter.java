/*
 * Copyright 2014-2018 Netflix, Inc.
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
package com.netflix.spectator.atlas;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.NoopRegistry;

import java.util.Collections;

/**
 * <p><b>Experimental:</b> This type may be removed in a future release.</p>
 *
 * NOOP implementation of double counter.
 */
enum NoopDoubleCounter implements DoubleCounter {

  /** Singleton instance. */
  INSTANCE;

  private static final Id ID = new NoopRegistry().createId("NoopDoubleCounter");

  @Override public void add(double amount) {
  }

  @Override public double actualCount() {
    return 0.0;
  }

  @Override public Id id() {
    return ID;
  }

  @Override
  public Iterable<Measurement> measure() {
    return Collections.emptyList();
  }

  @Override
  public boolean hasExpired() {
    return true;
  }
}
