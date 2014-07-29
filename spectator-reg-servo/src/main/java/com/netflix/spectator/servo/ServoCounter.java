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
package com.netflix.spectator.servo;

import com.netflix.servo.monitor.Monitor;
import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;

/** Counter implementation for the servo registry. */
class ServoCounter implements Counter, ServoMeter {

  private final Clock clock;
  private final com.netflix.servo.monitor.BasicCounter impl;

  /** Create a new instance. */
  ServoCounter(Clock clock, com.netflix.servo.monitor.BasicCounter impl) {
    this.clock = clock;
    this.impl = impl;
  }

  @Override public Monitor<?> monitor() {
    return impl;
  }

  @Override public Id id() {
    return new ServoId(impl.getConfig());
  }

  @Override public boolean hasExpired() {
    return false;
  }

  @Override public Iterable<Measurement> measure() {
    long now = clock.wallTime();
    long v = count();
    return Collections.singleton(new Measurement(id(), now, v));
  }

  @Override public void increment() {
    impl.increment();
  }

  @Override public void increment(long amount) {
    impl.increment(amount);
  }

  @Override public long count() {
    return impl.getValue(0).longValue();
  }
}
