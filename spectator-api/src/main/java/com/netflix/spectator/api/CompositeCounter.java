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
package com.netflix.spectator.api;

import java.util.Collection;
import java.util.Iterator;

/** Counter implementation for the composite registry. */
final class CompositeCounter extends CompositeMeter<Counter> implements Counter {

  /** Create a new instance. */
  CompositeCounter(Id id, Collection<Counter> counters) {
    super(id, counters);
  }

  @Override public void add(double amount) {
    for (Counter c : meters) {
      c.add(amount);
    }
  }

  @Override public double actualCount() {
    Iterator<Counter> it = meters.iterator();
    return it.hasNext() ? it.next().actualCount() : 0.0;
  }
}
