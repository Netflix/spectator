package com.netflix.spectator.jvm;

import com.netflix.spectator.api.*;

import java.lang.management.BufferPoolMXBean;
import java.util.ArrayList;
import java.util.List;

/**
 * Wraps a {@link java.lang.management.BufferPoolMXBean} so it can be registered with spectator.
 */
class BufferPoolMeter extends AbstractMeter<BufferPoolMXBean> {

  private static Id meterId(Registry registry, String id) {
    return registry.createId("jvm.buffer").withTag("id", id);
  }

  private static Id bufferCountId(Registry registry, String id) {
    return registry.createId("jvm.buffer.count").withTag("id", id);
  }

  private static Id bufferMemoryUsedId(Registry registry, String id) {
    return registry.createId("jvm.buffer.memoryUsed").withTag("id", id);
  }

  private final Id countId;
  private final Id memoryUsedId;

  /**
   * Creates a new instance.
   *
   * @param registry
   *     Spectator registry to use for naming and clock source.
   * @param mbean
   *     Mbean to collect the data from.
   */
  BufferPoolMeter(Registry registry, BufferPoolMXBean mbean) {
    super(registry.clock(), meterId(registry, mbean.getName()), mbean);
    countId = bufferCountId(registry, mbean.getName());
    memoryUsedId = bufferMemoryUsedId(registry, mbean.getName());
  }

  @Override public Iterable<Measurement> measure() {
    final long timestamp = clock.wallTime();
    final BufferPoolMXBean mbean = ref.get();
    final List<Measurement> ms = new ArrayList<>();
    if (mbean != null) {
      ms.add(new Measurement(countId, timestamp, mbean.getCount()));
      ms.add(new Measurement(memoryUsedId, timestamp, mbean.getMemoryUsed()));
    }
    return ms;
  }
}
