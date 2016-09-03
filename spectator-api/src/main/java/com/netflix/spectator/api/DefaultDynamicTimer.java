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

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Timer implementation that delegates the value tracking to component timers
 * based on the current value of the tags associated with the DynamicId when the
 * interface methods are called.
 *
 * @deprecated Use {@code spectator-ext-placeholders} library instead.
 */
@Deprecated
class DefaultDynamicTimer extends AbstractDefaultDynamicMeter<Timer> implements Timer {
  /**
   * Constructs a new timer with the specified dynamic id.
   *
   * @param id the dynamic (template) id for generating the individual timers
   * @param registry the registry to use to instantiate the individual timers
   */
  DefaultDynamicTimer(DynamicId id, Registry registry) {
    super(id, registry::timer);
  }

  @Override
  public void record(long amount, TimeUnit unit) {
    resolveToCurrentMeter().record(amount, unit);
  }

  @Override
  public <T> T record(Callable<T> f) throws Exception {
    return resolveToCurrentMeter().record(f);
  }

  @Override
  public void record(Runnable f) {
    resolveToCurrentMeter().record(f);
  }

  @Override
  public long count() {
    return resolveToCurrentMeter().count();
  }

  @Override
  public long totalTime() {
    return resolveToCurrentMeter().totalTime();
  }
}
