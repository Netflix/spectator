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
package com.netflix.spectator.micrometer;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Timer;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Wraps a Micrometer Timer to make it conform to the Spectator API.
 */
class MicrometerTimer extends MicrometerMeter implements Timer {

  private final io.micrometer.core.instrument.Timer impl;

  /** Create a new instance. */
  MicrometerTimer(Id id, io.micrometer.core.instrument.Timer impl) {
    super(id);
    this.impl = impl;
  }

  @Override public void record(long amount, TimeUnit unit) {
    impl.record(amount, unit);
  }

  @Override public <T> T record(Callable<T> f) throws Exception {
    return impl.recordCallable(f);
  }

  @Override public void record(Runnable f) {
    impl.record(f);
  }

  @Override public long count() {
    return impl.count();
  }

  @Override public long totalTime() {
    return (long) impl.totalTime(TimeUnit.NANOSECONDS);
  }

  @Override
  public Iterable<Measurement> measure() {
    return convert(impl.measure());
  }
}
