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

import java.util.Collections;
import java.util.Iterator;

/**
 * Registry implementation that does nothing. This is typically used to allow for performance tests
 * to see how much overhead is being added by instrumentation. This implementation tries to do the
 * minimum amount possible without requiring code changes for users.
 */
public final class NoopRegistry implements Registry {

  /** {@inheritDoc} */
  @Override
  public Clock clock() {
    return Clock.SYSTEM;
  }

  /** {@inheritDoc} */
  @Override
  public Id createId(String name) {
    return NoopId.INSTANCE;
  }

  /** {@inheritDoc} */
  @Override
  public Id createId(String name, Iterable<Tag> tags) {
    return NoopId.INSTANCE;
  }

  /** {@inheritDoc} */
  @Override
  public void register(Meter meter) {
  }

  /** {@inheritDoc} */
  @Override
  public Counter counter(Id id) {
    return NoopCounter.INSTANCE;
  }

  /** {@inheritDoc} */
  @Override
  public DistributionSummary distributionSummary(Id id) {
    return NoopDistributionSummary.INSTANCE;
  }

  /** {@inheritDoc} */
  @Override
  public Timer timer(Id id) {
    return NoopTimer.INSTANCE;
  }

  /** {@inheritDoc} */
  @Override
  public Meter get(Id id) {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public Iterator<Meter> iterator() {
    return Collections.<Meter>emptyList().iterator();
  }

  /** {@inheritDoc} */
  @Override
  public void addListener(RegistryListener listener) {
  }

  /** {@inheritDoc} */
  @Override
  public void removeListener(RegistryListener listener) {
  }
}
