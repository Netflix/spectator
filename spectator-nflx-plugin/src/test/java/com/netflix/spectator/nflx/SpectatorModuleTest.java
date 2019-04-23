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

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.ExtendedRegistry;
import com.netflix.spectator.api.ManualClock;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Spectator;
import com.netflix.spectator.atlas.AtlasRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SpectatorModuleTest {

  @Test
  public void atlasRegistryIsBound() {
    Injector injector = Guice.createInjector(new SpectatorModule());
    Assertions.assertTrue(injector.getInstance(Registry.class) instanceof AtlasRegistry);
  }

  @Test
  public void extendedRegistryIsBound() {
    Injector injector = Guice.createInjector(new SpectatorModule());
    Assertions.assertNotNull(injector.getInstance(ExtendedRegistry.class));
  }

  @Test
  public void injectedRegistryAddedToGlobal() {
    final ManualClock clock = new ManualClock();
    Injector injector = Guice.createInjector(
        new AbstractModule() {
          @Override protected void configure() {
            bind(Clock.class).toInstance(clock);
          }
        },
        new SpectatorModule());
    Registry registry = injector.getInstance(Registry.class);
    Spectator.globalRegistry().counter("test").increment();
    clock.setWallTime(60000);
    Assertions.assertEquals(1, registry.counter("test").count());
  }

  @Test
  public void equals() {
    Assertions.assertEquals(new SpectatorModule(), new SpectatorModule());
  }

  @Test
  public void hashCodeTest() {
    Assertions.assertEquals(new SpectatorModule().hashCode(), new SpectatorModule().hashCode());
  }

  @Test
  public void optionalInjectWorksWithOptionalBinder() {
    Injector injector = Guice.createInjector(new SpectatorModule());
    OptionalInject obj = injector.getInstance(OptionalInject.class);
    Assertions.assertNotNull(obj.registry);
  }

  private static class OptionalInject {
    @Inject(optional = true)
    Registry registry;
  }
}
