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
    Assert.assertEquals("true", System.getProperty("spectator.nflx.initialized"));
    AutoPlugin ap = injector.getInstance(AutoPlugin.class);
    Assert.assertEquals(ap.getPlugin(), injector.getInstance(Plugin.class));
    lcMgr.close();
  }

}
