/*
 * Copyright 2014-2016 Netflix, Inc.
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

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

/** Timer implementation for the composite registry. */
final class CompositeTimer extends CompositeMeter implements Timer {

  private final Clock clock;

  /** Create a new instance. */
  CompositeTimer(Id id, Clock clock, Collection<Registry> registries) {
    super(id, registries);
    this.clock = clock;
  }

  @Override public Clock clock() {
    return clock;
  }

  @Override public void record(long amount, TimeUnit unit) {
    for (Registry r : registries) {
      r.timer(id).record(amount, unit);
    }
  }

  @Override public long count() {
    Iterator<Registry> it = registries.iterator();
    return it.hasNext() ? it.next().timer(id).count() : 0L;
  }

  @Override public long totalTime() {
    Iterator<Registry> it = registries.iterator();
    return it.hasNext() ? it.next().timer(id).totalTime() : 0L;
  }
}
