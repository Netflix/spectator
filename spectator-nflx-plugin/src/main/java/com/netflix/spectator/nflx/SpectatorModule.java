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
import com.netflix.spectator.api.ExtendedRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Spectator;

/**
 * Guice module to configure the appropriate bindings for running an application. Note that this
 * will configure it to use the global registry bindings for reporting. This also means that test
 * cases will not be completely isolated. For unit tests see
 * {@link com.netflix.spectator.nflx.TestModule}. Typical usage:
 *
 * <p><b>User code</b></p>
 * <pre>
 * public class Foo {
 *   private final Counter counter;
 *
 *  {@literal @}Inject
 *   public Foo(ExtendedRegistry registry) {
 *     counter = registry.counter("foo.doSomething");
 *   }
 *
 *   public void doSomething() {
 *     counter.increment();
 *   }
 * }
 * </pre>
 *
 * <p><b>Guice</b></p>
 * <pre>
 * Injector injector = Guice.createInjector(new SpectatorModule());
 * ExtendedRegistry registry = injector.getInstance(ExtendedRegistry.class);
 * </pre>
 *
 * <p><b>Governator</b></p>
 * <pre>
 * Injector injector = LifecycleInjector.builder()
 *   .withModules(new SpectatorModule())
 *   .build()
 *   .createInjector();
 * </pre>
 */
public class SpectatorModule extends AbstractModule {
  @Override protected void configure() {
    bind(Plugin.class).asEagerSingleton();
    bind(ExtendedRegistry.class).toInstance(Spectator.registry());
    bind(Registry.class).toInstance(Spectator.registry());
  }
}
