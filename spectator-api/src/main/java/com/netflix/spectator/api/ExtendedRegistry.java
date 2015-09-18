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
package com.netflix.spectator.api;

import com.netflix.spectator.impl.Preconditions;

import java.util.Iterator;

/**
 * Wraps a registry and provides additional helper methods to make it easier to use.
 *
 * @deprecated This class was used prior to java 8 for adding extension methods to the registry
 * without breaking all classes implementing the interface. The extension methods have now been
 * moved to Registry interface as default methods.
 */
@Deprecated
public final class ExtendedRegistry implements Registry {

  private final Registry impl;

  /** Create a new instance. */
  public ExtendedRegistry(Registry impl) {
    this.impl = Preconditions.checkNotNull(impl, "impl");
  }

  /** Returns the underlying registry implementation that is being wrapped. */
  public Registry underlying() {
    return impl;
  }

  @Override public Clock clock() {
    return impl.clock();
  }

  @Override public Id createId(String name) {
    return impl.createId(name);
  }

  @Override public Id createId(String name, Iterable<Tag> tags) {
    return impl.createId(name, tags);
  }

  @Override
  public DynamicId createDynamicId(String name) {
    return impl.createDynamicId(name);
  }

  @Override
  public DynamicId createDynamicId(String name, Iterable<TagFactory> tagFactories) {
    return impl.createDynamicId(name, tagFactories);
  }

  @Override public void register(Meter meter) {
    impl.register(meter);
  }

  @Override public Counter counter(Id id) {
    return impl.counter(id);
  }

  @Override public DistributionSummary distributionSummary(Id id) {
    return impl.distributionSummary(id);
  }

  @Override public Timer timer(Id id) {
    return impl.timer(id);
  }

  @Override public Meter get(Id id) {
    return impl.get(id);
  }

  @Override public Iterator<Meter> iterator() {
    return impl.iterator();
  }

  /////////////////////////////////////////////////////////////////
  // Additional helper methods below

  @Override
  public String toString() {
    return "ExtendedRegistry(impl=" + impl + ')';
  }
}
