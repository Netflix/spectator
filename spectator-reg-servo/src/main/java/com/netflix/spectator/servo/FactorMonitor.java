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

import com.netflix.servo.monitor.AbstractMonitor;
import com.netflix.servo.monitor.Monitor;
import com.netflix.servo.monitor.NumericMonitor;

/**
 * Multiplies the value from the wrapped monitor by a given factor. Primarily used to perform
 * time unit conversions for timer monitors that internally store in nanoseconds but report in
 * the base time unit of seconds.
 */
final class FactorMonitor<T extends Number> extends AbstractMonitor<Double>
    implements NumericMonitor<Double> {
  private final Monitor<T> wrapped;
  private final double factor;

  /**
   * Create a new monitor that returns {@code wrapped.getValue() * factor}.
   *
   * @param wrapped
   *     A simple monitor that will have its value converted.
   * @param factor
   *     Conversion factor to apply to the wrapped monitors value.
   */
  FactorMonitor(Monitor<T> wrapped, double factor) {
    super(wrapped.getConfig());
    this.wrapped = wrapped;
    this.factor = factor;
  }

  @Override public Double getValue(int pollerIndex) {
    final double v = wrapped.getValue(pollerIndex).doubleValue();
    System.out.printf("%f * %f = %f%n", v, factor, v * factor);
    return v * factor;
  }
}
