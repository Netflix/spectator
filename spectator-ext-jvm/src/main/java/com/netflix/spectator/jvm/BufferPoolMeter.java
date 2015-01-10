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
