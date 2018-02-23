/*
 * Copyright 2014-2018 Netflix, Inc.
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

import com.google.inject.Inject;
import com.google.inject.Scopes;
import com.google.inject.multibindings.OptionalBinder;
import com.netflix.archaius.api.Config;
import com.netflix.archaius.config.EmptyConfig;
import com.netflix.spectator.servo.ServoRegistry;

import javax.annotation.PreDestroy;
import javax.inject.Provider;

import com.google.inject.AbstractModule;
import com.netflix.spectator.api.ExtendedRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Spectator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * <p><b>Governator</b></p>
 * <p>One of the classes requires an {@link javax.annotation.PostConstruct} block to initialize
 * so governator should be used to manage the lifecycle as guice doesn't support it directly.</p>
 * <pre>
 * Injector injector = LifecycleInjector.builder()
 *   .withModules(new SpectatorModule())
 *   .build()
 *   .createInjector();
 * injector.getInstance(LifecycleManager.class).start();
 * </pre>
 */
public final class SpectatorModule extends AbstractModule {

  private static final Logger LOGGER = LoggerFactory.getLogger(SpectatorModule.class);

  @Override protected void configure() {
    bind(Plugin.class).toProvider(PluginProvider.class).asEagerSingleton();
    bind(StaticManager.class).asEagerSingleton();
    OptionalBinder.newOptionalBinder(binder(), ExtendedRegistry.class)
        .setDefault()
        .toInstance(Spectator.registry());
    OptionalBinder.newOptionalBinder(binder(), Registry.class)
        .setDefault()
        .toProvider(RegistryProvider.class)
        .in(Scopes.SINGLETON);
  }

  @Override public boolean equals(Object obj) {
    return obj != null && getClass().equals(obj.getClass());
  }

  @Override public int hashCode() {
    return getClass().hashCode();
  }

  private static class PluginProvider implements Provider<Plugin> {
    private final Plugin plugin;

    @Inject
    PluginProvider(Registry registry, OptionalInjections opts) {
      plugin = new Plugin(registry, opts.config());
    }

    @Override public Plugin get() {
      return plugin;
    }
  }

  private static class OptionalInjections {
    @Inject(optional = true)
    private Config config;

    Config config() {
      if (config == null) {
        LOGGER.warn("no archaius2 binding found, using empty configuration");
        config = EmptyConfig.INSTANCE;
      }
      return config;
    }
  }

  private static class StaticManager {
    private final Registry registry;

    @Inject
    StaticManager(Registry registry) {
      this.registry = registry;
      Spectator.globalRegistry().add(registry);
    }

    @PreDestroy
    void onShutdown() {
      Spectator.globalRegistry().remove(registry);
    }
  }

  private static class RegistryProvider implements Provider<Registry> {

    private ServoRegistry registry = new ServoRegistry();

    @Override public Registry get() {
      return registry;
    }
  }
}
