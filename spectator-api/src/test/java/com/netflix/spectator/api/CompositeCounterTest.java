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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class CompositeCounterTest {

  private final ManualClock clock = new ManualClock();

  private final Id id = new DefaultId("foo");
  private List<Registry> registries;

  private Counter newCounter() {
    List<Counter> cs = registries.stream()
        .map(r -> r.counter(id))
        .collect(Collectors.toList());
    return new CompositeCounter(id, cs);
  }

  private void assertCountEquals(Counter c, long expected) {
    Assertions.assertEquals(c.count(), expected);
    for (Registry r : registries) {
      Assertions.assertEquals(r.counter(id).count(), expected);
    }
  }

  @BeforeEach
  public void before() {
    registries = new ArrayList<>();
    for (int i = 0; i < 5; ++i) {
      registries.add(new DefaultRegistry(clock));
    }
  }

  @Test
  public void empty() {
    Counter c = new CompositeCounter(NoopId.INSTANCE, Collections.emptyList());
    assertCountEquals(c, 0L);
    c.increment();
    assertCountEquals(c, 0L);
  }

  @Test
  public void init() {
    Counter c = newCounter();
    assertCountEquals(c, 0L);
  }

  @Test
  public void increment() {
    Counter c = newCounter();
    c.increment();
    assertCountEquals(c, 1L);
    c.increment();
    c.increment();
    assertCountEquals(c, 3L);
  }

  @Test
  public void incrementAmount() {
    Counter c = newCounter();
    c.increment(42);
    assertCountEquals(c, 42L);
  }

  @Test
  public void measure() {
    Counter c = newCounter();
    c.increment(42);
    clock.setWallTime(3712345L);
    for (Measurement m : c.measure()) {
      Assertions.assertEquals(m.id(), c.id());
      Assertions.assertEquals(m.timestamp(), 3712345L);
      Assertions.assertEquals(m.value(), 42.0, 0.1e-12);
    }
  }

}
