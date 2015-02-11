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

import com.netflix.servo.monitor.AbstractMonitor;
import com.netflix.servo.monitor.MonitorConfig;
import com.netflix.servo.monitor.NumericMonitor;

/**
 * Reports a constant value passed into the constructor.
 */
final class ServoGauge<T extends Number> extends AbstractMonitor<Double>
    implements NumericMonitor<Double> {
  private final double value;

  /**
   * Create a new monitor that returns {@code value}.
   */
  ServoGauge(MonitorConfig config, double value) {
    super(config);
    this.value = value;
  }

  @Override public Double getValue(int pollerIndex) {
    return value;
  }
}
