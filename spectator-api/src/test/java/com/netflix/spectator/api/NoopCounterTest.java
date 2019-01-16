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
import org.junit.jupiter.api.Test;

public class NoopCounterTest {
  @Test
  public void testId() {
    Assertions.assertEquals(NoopCounter.INSTANCE.id(), NoopId.INSTANCE);
    Assertions.assertFalse(NoopCounter.INSTANCE.hasExpired());
  }

  @Test
  public void testIncrement() {
    NoopCounter c = NoopCounter.INSTANCE;
    c.increment();
    Assertions.assertEquals(c.count(), 0L);
  }

  @Test
  public void testIncrementAmount() {
    NoopCounter c = NoopCounter.INSTANCE;
    c.increment(42);
    Assertions.assertEquals(c.count(), 0L);
  }

  @Test
  public void testMeasure() {
    NoopCounter c = NoopCounter.INSTANCE;
    c.increment(42);
    Assertions.assertFalse(c.measure().iterator().hasNext());
  }

}
