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
package com.netflix.spectator.metrics3;

import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * <p>Aggregate gauge used to compute the sum of gauges with the same Id.</p>
 * <p>This aggregate gauge is only used to register with Metrics, the register with spectator is for each one of
 * the registered gauges in this Aggregate gauge.</p>
 *
 * @author Kennedy Oliveira
 */
class MetricsGaugeAggr implements com.codahale.metrics.Gauge<Double> {

  private ConcurrentLinkedQueue<MetricsGauge> gauges;

  /**
   * Creates a new Aggregate Gauge.
   */
  MetricsGaugeAggr() {
    this.gauges = new ConcurrentLinkedQueue<>();
  }

  @Override
  public Double getValue() {
    double aggregatedValue = Double.NaN;

    final Iterator<MetricsGauge> iterator = gauges.iterator();
    while (iterator.hasNext()) {
      final MetricsGauge gauge = iterator.next();

      if (gauge.hasExpired()) {
        iterator.remove();
      } else {
        final double gaugeVal = gauge.value();

        // When it's NaN means the gauge can be unregistered due to it's reference to the value to extract value
        // was garbage collected or simple the gauge returned a NaN so i don't count it
        if (!Double.isNaN(gaugeVal))
          aggregatedValue = (Double.isNaN(aggregatedValue) ? 0 : aggregatedValue) + gaugeVal;
      }
    }

    return aggregatedValue;
  }

  /**
   * Add a gauge to be aggragate to the others.
   *
   * @param gauge Gauge to be added
   */
  void addGauge(MetricsGauge gauge) {
    this.gauges.add(gauge);
  }
}
