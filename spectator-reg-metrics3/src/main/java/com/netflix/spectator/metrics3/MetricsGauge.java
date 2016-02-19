package com.netflix.spectator.metrics3;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.netflix.spectator.api.AbstractMeter;
import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;

import java.util.Collections;
import java.util.function.ToDoubleFunction;

/**
 * Gauge Implementation for metric3 registry.
 *
 * @author Kennedy Oliveira
 */
class MetricsGauge<T> extends AbstractMeter<T> {

  private final MetricRegistry metricRegistry;
  private final String metricName;
  private final Gauge<Double> gauge;

  /**
   * Create a new instance.
   *
   * @param clock                Clock to use for getting measurement timestamps. Typically should be the clock used by
   *                             the registry (see {@link com.netflix.spectator.api.Registry#clock()}).
   * @param id                   Identifier for the meter.
   * @param obj                  Object that {@code extractValueFunction} will be applid to get the Value.
   * @param metricRegistry       The {@link MetricRegistry} for registering the Gauge
   * @param extractValueFunction The function that will receive {@code obj} to extract a value for the Gauge.
   */
  MetricsGauge(Clock clock, Id id, T obj, MetricRegistry metricRegistry, ToDoubleFunction<T> extractValueFunction) {
    super(clock, id, obj);
    this.metricRegistry = metricRegistry;
    this.metricName = NameUtils.toMetricName(id);
    this.gauge = () -> {
      final T refObj = ref.get();
      return (refObj != null) ? extractValueFunction.applyAsDouble(refObj) : Double.NaN;
    };
    this.metricRegistry.register(metricName, gauge);
  }

  @Override
  public Iterable<Measurement> measure() {
    return Collections.singleton(new Measurement(id, clock.wallTime(), gauge.getValue()));
  }

  @Override
  public boolean hasExpired() {
    final boolean hasExpired = super.hasExpired();

    // When this method is called and hasExpired is true, the Gauge will be removed from
    // Spectator registry, so we remove from the Metrics registry too
    if (hasExpired) {
      this.metricRegistry.remove(metricName);
    }

    return hasExpired;
  }
}
