package com.netflix.spectator.jvm;

import com.netflix.spectator.api.*;

import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;

/**
 * Wraps a {@link java.lang.management.MemoryPoolMXBean} so it can be registered with spectator.
 */
class MemoryPoolMeter extends AbstractMeter<MemoryPoolMXBean> {

  private final Id usedId;
  private final Id committedId;
  private final Id maxId;

  /**
   * Creates a new instance.
   *
   * @param registry
   *     Spectator registry to use for naming and clock source.
   * @param mbean
   *     Mbean to collect the data from.
   */
  MemoryPoolMeter(Registry registry, MemoryPoolMXBean mbean) {
    super(registry.clock(), registry.createId("jvm.memory").withTag("id", mbean.getName()), mbean);
    usedId = registry.createId("jvm.memory.used").withTag("id", mbean.getName());
    committedId = registry.createId("jvm.memory.committed").withTag("id", mbean.getName());
    maxId = registry.createId("jvm.memory.max").withTag("id", mbean.getName());
  }

  @Override public Iterable<Measurement> measure() {
    final long timestamp = clock.wallTime();
    final MemoryPoolMXBean mbean = ref.get();
    final List<Measurement> ms = new ArrayList<>();
    if (mbean != null) {
      final String typeKey = "memtype";
      final String type = mbean.getName();

      final MemoryUsage usage = mbean.getUsage();
      ms.add(new Measurement(usedId.withTag(typeKey, type), timestamp, usage.getUsed()));
      ms.add(new Measurement(committedId.withTag(typeKey, type), timestamp, usage.getCommitted()));
      ms.add(new Measurement(maxId.withTag(typeKey, type), timestamp, usage.getMax()));
    }
    return ms;
  }
}
