/*
 * Copyright 2014-2019 Netflix, Inc.
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
import com.google.inject.name.Names;
import com.netflix.archaius.api.Config;
import com.netflix.servo.SpectatorContext;
import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.atlas.AtlasConfig;
import com.netflix.spectator.atlas.AtlasRegistry;

import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import com.google.inject.AbstractModule;
import com.netflix.spectator.api.ExtendedRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Spectator;

import java.util.Map;

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
 *   public Foo(Registry registry) {
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

  @Override protected void configure() {
    bind(Plugin.class).asEagerSingleton();
    bind(StaticManager.class).asEagerSingleton();
    bind(Config.class)
        .annotatedWith(Names.named("spectator"))
        .toProvider(ConfigProvider.class);
    bind(AtlasConfig.class).to(AtlasConfiguration.class);
    OptionalBinder.newOptionalBinder(binder(), ExtendedRegistry.class)
        .setDefault()
        .toInstance(Spectator.registry());
    OptionalBinder.newOptionalBinder(binder(), Clock.class)
        .setDefault()
        .toInstance(Clock.SYSTEM);
    OptionalBinder.newOptionalBinder(binder(), Registry.class)
        .setDefault()
        .to(AtlasRegistry.class)
        .in(Scopes.SINGLETON);
  }

  @Override public boolean equals(Object obj) {
    return obj != null && getClass().equals(obj.getClass());
  }

  @Override public int hashCode() {
    return getClass().hashCode();
  }

  @Singleton
  private static class ConfigProvider implements Provider<Config> {
    @Inject(optional = true)
    private Config config;

    private Config atlasConfig;

    @Override public Config get() {
      if (atlasConfig == null) {
        atlasConfig = NetflixConfig.createConfig(config);
      }
      return atlasConfig;
    }
  }

  private static class StaticManager implements AutoCloseable {
    private final Registry registry;

    @Inject
    StaticManager(Registry registry) {
      this.registry = registry;
      Spectator.globalRegistry().add(registry);
      SpectatorContext.setRegistry(registry);
    }

    @Override public void close() {
      Spectator.globalRegistry().remove(registry);
      SpectatorContext.setRegistry(new NoopRegistry());
    }
  }

  @Singleton
  private static class AtlasConfiguration implements AtlasConfig {

    private final Config cfg;

    @Inject
    AtlasConfiguration(@Named("spectator") Config cfg) {
      this.cfg = cfg;
    }

    private final Map<String, String> nflxCommonTags = NetflixConfig.commonTags();

    @Override public String get(String k) {
      final String prop = "netflix.spectator.registry." + k;
      return cfg.getString(prop, null);
    }

    @Override public boolean enabled() {
      String v = get("atlas.enabled");
      return v != null && Boolean.valueOf(v);
    }

    @Override public boolean autoStart() {
      return true;
    }

    @Override
    public Map<String, String> commonTags() {
      return nflxCommonTags;
    }
  }
}
