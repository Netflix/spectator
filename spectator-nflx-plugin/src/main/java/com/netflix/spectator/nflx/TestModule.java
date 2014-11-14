/**
 * Copyright 2014 Netflix, Inc.
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
package com.netflix.spectator.nflx;

import com.google.inject.AbstractModule;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.ExtendedRegistry;
import com.netflix.spectator.api.Registry;

/**
 * Guice module to configure the appropriate bindings for unit tests. Note that this module will
 * create a registry that only keeps data in-memory and is scoped to the injector. If used when
 * running the application you will not be able to see the data and it will not get reported off
 * the instance. In particular, it is completely independent of the main registry accessed by
 * calling {@link com.netflix.spectator.api.Spectator#registry()}. Use the
 * {@link com.netflix.spectator.nflx.SpectatorModule} when running code outside of unit tests.
 */
public class TestModule extends AbstractModule {
  @Override protected void configure() {
    final ExtendedRegistry registry = new ExtendedRegistry(new DefaultRegistry());
    bind(ExtendedRegistry.class).toInstance(registry);
    bind(Registry.class).toInstance(registry);
  }
}
