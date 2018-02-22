/*
 * Copyright 2014-2017 Netflix, Inc.
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
import com.google.inject.Injector;
import com.netflix.archaius.api.Config;
import com.netflix.archaius.config.MapConfig;
import com.netflix.governator.guice.LifecycleInjector;
import com.netflix.governator.lifecycle.LifecycleManager;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.lang.management.ManagementFactory;

@RunWith(JUnit4.class)
public class AutoPluginTest {

  private static boolean isJava8() {
    String version = ManagementFactory.getRuntimeMXBean().getSpecVersion();
    return version.startsWith("1.8");
  }

  @Test
  public void inject() throws Exception {
    Assume.assumeTrue("requires java 8", isJava8());
    // On JDK 9:
    // Caused by: java.lang.ClassNotFoundException: javax.annotation.Resource
    // at java.base/jdk.internal.loader.BuiltinClassLoader.loadClass(BuiltinClassLoader.java:582)
    // at java.base/jdk.internal.loader.ClassLoaders$AppClassLoader.loadClass(ClassLoaders.java:185)
    // at java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:496)
    Injector injector = LifecycleInjector.builder()
        .usingBasePackages("com.netflix")
        .withAdditionalModules(new AbstractModule() {
          @Override
          protected void configure() {
            Config config = MapConfig.builder()
                .put("spectator.nflx.enabled", "false")
                .build();
            bind(Config.class).toInstance(config);
          }
        })
        .build()
        .createInjector();
    LifecycleManager lcMgr = injector.getInstance(LifecycleManager.class);
    lcMgr.start();
    Assert.assertNotNull(injector.getInstance(Plugin.class));
    lcMgr.close();
  }

}
