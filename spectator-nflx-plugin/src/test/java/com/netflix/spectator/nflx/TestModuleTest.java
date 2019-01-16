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

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Spectator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TestModuleTest {

  public static class Foo {
    private final Counter counter;

    @Inject
    public Foo(Registry registry) {
      counter = registry.counter("foo.doSomething");
    }

    public void doSomething() {
      counter.increment();
    }
  }

  private Injector injector;

  @BeforeEach
  public void setup() {
    injector = Guice.createInjector(new TestModule());
  }

  @Test
  public void checkCount() {
    Registry r = injector.getInstance(Registry.class);
    Counter c = r.counter("foo.doSomething");
    Assertions.assertEquals(0, c.count());

    Foo f = injector.getInstance(Foo.class);
    f.doSomething();
    Assertions.assertEquals(1, c.count());
  }

  @Test
  public void notGlobal() {
    Registry r = injector.getInstance(Registry.class);
    Assertions.assertNotSame(Spectator.globalRegistry(), r);
  }

  @Test
  public void usableIfSpectatorModuleIsInstalled() {
    Registry r = Guice
        .createInjector(new SpectatorModule(), new TestModule())
        .getInstance(Registry.class);
    Assertions.assertTrue(r instanceof DefaultRegistry); // SpectatorModule installs ServoRegistry

    // Sanity checking global behavior, SpectatorModule will add it to the global
    // registry which is not normally done for TestModule because it is all static.
    Assertions.assertSame(Spectator.globalRegistry().underlying(DefaultRegistry.class), r);
  }
}
