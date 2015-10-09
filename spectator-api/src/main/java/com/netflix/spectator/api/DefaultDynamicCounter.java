package com.netflix.spectator.api;

/**
 * Counter implementation that delegates the value tracking to component counters
 * based on the current value of the tags associated with the DynamicId when the
 * increment methods are called.
 */
class DefaultDynamicCounter implements Counter {
  private final DynamicId id;
  private final Registry registry;

  DefaultDynamicCounter(DynamicId id, Registry registry) {
    this.id = id;
    this.registry = registry;
  }

  private Counter resolveToCurrentCounter() {
    return registry.counter(id.resolveToId());
  }

  @Override
  public void increment() {
    resolveToCurrentCounter().increment();
  }

  @Override
  public void increment(long amount) {
    resolveToCurrentCounter().increment(amount);
  }

  @Override
  public long count() {
    return resolveToCurrentCounter().count();
  }

  @Override
  public Id id() {
    return resolveToCurrentCounter().id();
  }

  @Override
  public Iterable<Measurement> measure() {
    return resolveToCurrentCounter().measure();
  }

  @Override
  public boolean hasExpired() {
    // Without tracking all of the regular counters that are created from this
    // dynamic counter we don't have any way of knowing whether the "master"
    // counter has expired.  Instead of adding the tracking, we choose to
    // rely on the regular expiration mechanism for the underlying counters.
    return false;
  }
}
