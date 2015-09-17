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
package com.netflix.spectator.servo;

import com.google.common.base.Objects;
import com.netflix.servo.annotations.DataSourceType;
import com.netflix.servo.monitor.AbstractMonitor;
import com.netflix.servo.monitor.MonitorConfig;
import com.netflix.servo.monitor.NumericMonitor;
import com.netflix.servo.util.Clock;
import com.netflix.servo.util.ClockWithOffset;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A simple counter implementation backed by a StepLong. The value returned is a rate for the
 * previous interval as defined by the step.
 */
class DoubleCounter extends AbstractMonitor<Number> implements NumericMonitor<Number> {

  private final StepLong count;

  /**
   * Creates a new instance of the counter.
   */
  DoubleCounter(MonitorConfig config) {
    this(config, ClockWithOffset.INSTANCE);
  }

  /**
   * Creates a new instance of the counter.
   */
  DoubleCounter(MonitorConfig config, Clock clock) {
    // This class will reset the value so it is not a monotonically increasing value as
    // expected for type=COUNTER. This class looks like a counter to the user and a gauge to
    // the publishing pipeline receiving the value.
    super(config.withAdditionalTag(DataSourceType.NORMALIZED));
    count = new StepLong(0L, clock);
  }

  private void add(AtomicLong num, double amount) {
    long v;
    double d;
    long next;
    do {
      v = num.get();
      d = Double.longBitsToDouble(v);
      next = Double.doubleToLongBits(d + amount);
    } while (!num.compareAndSet(v, next));
  }

  /**
   * Increment the value by the specified amount.
   */
  void increment(double amount) {
    if (amount >= 0.0) {
      for (int i = 0; i < ServoPollers.NUM_POLLERS; ++i) {
        add(count.getCurrent(i), amount);
      }
    }
  }

  @Override public Number getValue(int pollerIndex) {
    final Long dp = count.poll(pollerIndex);
    final double stepSeconds = ServoPollers.POLLING_INTERVALS[pollerIndex] / 1000.0;
    return (dp == null) ? Double.NaN : Double.longBitsToDouble(dp.longValue()) / stepSeconds;
  }

  @Override public String toString() {
    return Objects.toStringHelper(this)
        .add("config", config)
        .add("count", getValue())
        .toString();
  }
}
