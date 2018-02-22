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

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.ExtendedRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Spectator;
import com.netflix.spectator.servo.ServoRegistry;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SpectatorModuleTest {

  @Test
  public void servoRegistryIsBound() {
    Injector injector = Guice.createInjector(new SpectatorModule());
    Assert.assertTrue(injector.getInstance(Registry.class) instanceof ServoRegistry);
  }

  @Test
  public void extendedRegistryIsBound() {
    Injector injector = Guice.createInjector(new SpectatorModule());
    Assert.assertNotNull(injector.getInstance(ExtendedRegistry.class));
  }

  @Test
  public void injectedRegistryAddedToGlobal() {
    Injector injector = Guice.createInjector(new SpectatorModule());
    Registry registry = injector.getInstance(Registry.class);
    Spectator.globalRegistry().counter("test").increment();
    Assert.assertEquals(1, registry.counter("test").count());
  }

  @Test
  public void equals() {
    Assert.assertEquals(new SpectatorModule(), new SpectatorModule());
  }

  @Test
  public void hashCodeTest() {
    Assert.assertEquals(new SpectatorModule().hashCode(), new SpectatorModule().hashCode());
  }

  @Test
  public void optionalInjectWorksWithOptionalBinder() {
    Injector injector = Guice.createInjector(new SpectatorModule());
    OptionalInject obj = injector.getInstance(OptionalInject.class);
    Assert.assertNotNull(obj.registry);
  }

  private static class OptionalInject {
    @Inject(optional = true)
    Registry registry;
  }
}
