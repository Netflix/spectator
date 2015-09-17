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
package com.netflix.spectator.tdigest;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.netflix.spectator.api.Timer;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

@RunWith(JUnit4.class)
public class TestModuleTest {

  @Test
  public void writeData() throws Exception {
    Injector injector = Guice.createInjector(TDigestTestModule.create());
    injector.getInstance(Foo.class).doSomething();

    final TDigestRegistry r = injector.getInstance(TDigestRegistry.class);
    Assert.assertEquals(1, r.timer("foo").count());
    Assert.assertEquals(42, r.timer("foo").totalTime());
  }

  private static class Foo {
    private final Timer t;

    @Inject
    Foo(TDigestRegistry registry) {
      t = registry.timer("foo");
    }

    void doSomething() {
      t.record(42, TimeUnit.NANOSECONDS);
    }
  }

}
