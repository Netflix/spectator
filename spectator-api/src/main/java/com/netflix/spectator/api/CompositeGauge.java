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

import java.util.Collection;
import java.util.Iterator;

/** Counter implementation for the composite registry. */
final class CompositeGauge extends CompositeMeter<Gauge> implements Gauge {

  /** Create a new instance. */
  CompositeGauge(Id id, Collection<Gauge> gauges) {
    super(id, gauges);
  }

  @Override public void set(double v) {
    for (Gauge g : meters) {
      g.set(v);
    }
  }

  @Override public double value() {
    Iterator<Gauge> it = meters.iterator();
    return it.hasNext() ? it.next().value() : Double.NaN;
  }
}
