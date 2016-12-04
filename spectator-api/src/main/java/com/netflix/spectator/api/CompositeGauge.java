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

/** Counter implementation for the composite registry. */
final class CompositeGauge extends CompositeMeter implements Gauge {

  /** Create a new instance. */
  CompositeGauge(Id id, Collection<Registry> registries) {
    super(id, registries);
  }

  @Override public void set(double v) {
    for (Registry r : registries) {
      r.gauge(id).set(v);
    }
  }

  @Override public double value() {
    Iterator<Registry> it = registries.iterator();
    return it.hasNext() ? it.next().gauge(id).value() : Double.NaN;
  }
}
