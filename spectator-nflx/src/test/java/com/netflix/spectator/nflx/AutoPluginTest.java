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

import com.google.inject.Injector;
import com.netflix.governator.guice.LifecycleInjector;
import com.netflix.governator.guice.LifecycleInjectorBuilder;
import com.netflix.governator.lifecycle.LifecycleManager;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RunWith(JUnit4.class)
public class AutoPluginTest {

  @Before
  public void init() {
    System.setProperty("spectator.nflx.enabled", "false");
  }

  @Test
  public void inject() throws Exception {
    Injector injector = LifecycleInjector.builder()
        .usingBasePackages("com.netflix")
        .build()
        .createInjector();
    LifecycleManager lcMgr = injector.getInstance(LifecycleManager.class);
    lcMgr.start();
    AutoPlugin ap = injector.getInstance(AutoPlugin.class);
    Assert.assertEquals(ap.getPlugin(), injector.getInstance(Plugin.class));
    lcMgr.close();
  }

}
