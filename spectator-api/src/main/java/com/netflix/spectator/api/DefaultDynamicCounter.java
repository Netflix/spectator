package com.netflix.spectator.api;

/**
 * Counter implementation that delegates the value tracking to component counters
 * based on the current value of the tags associated with the DynamicId when the
 * increment methods are called.
 */
class DefaultDynamicCounter extends AbstractDefaultDynamicMeter<Counter> implements Counter {
  /**
   * Constructs a new counter with the specified dynamic id.
   *
   * @param id the dynamic (template) id for generating the individual counters
   * @param registry the registry to use to instantiate the individual counters
   */
  DefaultDynamicCounter(DynamicId id, Registry registry) {
    super(id, registry::counter);
  }

  @Override
  public void increment() {
    resolveToCurrentMeter().increment();
  }

  @Override
  public void increment(long amount) {
    resolveToCurrentMeter().increment(amount);
  }

  @Override
  public long count() {
    return resolveToCurrentMeter().count();
  }
}
