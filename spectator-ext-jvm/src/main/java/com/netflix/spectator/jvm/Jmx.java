package com.netflix.spectator.jvm;

import com.netflix.spectator.api.Registry;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;

/**
 * Helpers for working with JMX mbeans.
 */
public final class Jmx {

  private Jmx() {
  }

  /**
   * Add meters for the standard MXBeans provided by the jvm. This method will use
   * {@link java.lang.management.ManagementFactory#getPlatformMXBeans(Class)} to get the set of
   * mbeans from the local jvm.
   */
  public static void registerStandardMXBeans(Registry registry) {
    for (MemoryPoolMXBean mbean : ManagementFactory.getPlatformMXBeans(MemoryPoolMXBean.class)) {
      registry.register(new MemoryPoolMeter(registry, mbean));
    }

    for (BufferPoolMXBean mbean : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
      registry.register(new BufferPoolMeter(registry, mbean));
    }
  }
}
