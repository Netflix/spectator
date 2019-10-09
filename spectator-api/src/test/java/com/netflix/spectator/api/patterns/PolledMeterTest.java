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
package com.netflix.spectator.api.patterns;

import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

public class PolledMeterTest {

  @Test
  public void removeAndAddRepeatedlyCounter() {
    Registry r = new DefaultRegistry();
    Id id = r.createId("test");

    AtomicLong value = new AtomicLong();
    for (int i = 0; i < 10; ++i) {
      PolledMeter.using(r).withId(id).monitorMonotonicCounter(value);
      PolledMeter.update(r);
      value.incrementAndGet();
      PolledMeter.update(r);
      PolledMeter.remove(r, id);
    }

    Assertions.assertEquals(10, r.counter("test").count());
  }

  @Test
  public void removeAndAddRepeatedlyGauge() {
    Registry r = new DefaultRegistry();
    Id id = r.createId("test");

    AtomicLong value = new AtomicLong();
    for (int i = 0; i < 10; ++i) {
      PolledMeter.using(r).withId(id).monitorValue(value);
      value.set(i);
      PolledMeter.update(r);
      PolledMeter.remove(r, id);
    }

    Assertions.assertEquals(9.0, r.gauge("test").value(), 1e-12);
  }
}
