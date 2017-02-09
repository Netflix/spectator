package com.netflix.spectator.api;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A counter that also keeps track of the time since last update.
 */
public final class ActivityCounter implements Counter {

  /** Create a new instance. */
  public static ActivityCounter get(Registry registry, Id id) {
    return new ActivityCounter(registry, id);
  }

  private static final Tag INTERVAL_TAG = new BasicTag("statistic", "interval");

  private final Registry registry;
  private final Id id;
  private final Counter counter;
  private final AtomicLong lastUpdated;

  /**
   * Create a new ActivityCounter using the given registry and base id.
   */
  ActivityCounter(Registry registry, Id id) {
    this.registry = registry;
    this.id = id;
    this.counter = registry.counter(id.withTag(Statistic.count));
    this.lastUpdated = new AtomicLong(0L);
    registry.gauge(id.withTag(INTERVAL_TAG), lastUpdated, Functions.age(registry.clock()));
  }

  @Override
  public void increment() {
    counter.increment();
    lastUpdated.set(registry.clock().wallTime());
  }

  @Override
  public void increment(long amount) {
    counter.increment(amount);
    lastUpdated.set(registry.clock().wallTime());
  }

  @Override
  public long count() {
    return counter.count();
  }

  @Override
  public Id id() {
    return id;
  }

  @Override
  public Iterable<Measurement> measure() {
    return Collections.emptyList();
  }

  @Override
  public boolean hasExpired() {
    return counter.hasExpired();
  }
}
