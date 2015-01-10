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
package com.netflix.spectator.nflx;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.netflix.governator.guice.LifecycleInjector;
import com.netflix.governator.lifecycle.LifecycleManager;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.ExtendedRegistry;
import com.netflix.spectator.api.Spectator;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SpectatorModuleTest {

  public static class Foo {
    private final Counter counter;

    @Inject
    public Foo(ExtendedRegistry registry) {
      counter = registry.counter("foo.doSomething");
    }

    public void doSomething() {
      counter.increment();
    }
  }

  private Injector injector;

  @Before
  public void setup() throws Exception {
    injector = LifecycleInjector.builder()
        .withModules(new SpectatorModule())
        .build()
        .createInjector();
    injector.getInstance(LifecycleManager.class).start();
  }

  @After
  public void cleanup() {
    injector.getInstance(LifecycleManager.class).close();
  }

  @Test
  public void checkCount() {
    ExtendedRegistry r = injector.getInstance(ExtendedRegistry.class);
    Counter c = r.counter("foo.doSomething");
    long before = c.count();

    Foo f = injector.getInstance(Foo.class);
    f.doSomething();
    Assert.assertEquals(before + 1, c.count());
  }

  @Test
  public void isGlobal() {
    ExtendedRegistry r = injector.getInstance(ExtendedRegistry.class);
    Assert.assertSame(Spectator.registry(), r);
  }
}
