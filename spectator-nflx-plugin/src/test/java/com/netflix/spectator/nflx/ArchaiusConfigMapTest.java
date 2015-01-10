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

import com.netflix.config.ConfigurationManager;
import com.netflix.spectator.api.ConfigMap;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ArchaiusConfigMapTest {

  @Before
  public void initProps() {
    ConfigurationManager.getConfigInstance().setProperty("archaius.configmap.string", "foo");
  }

  @Test
  public void getString() {
    ConfigMap cm = new ArchaiusConfigMap();
    Assert.assertEquals("foo", cm.get("archaius.configmap.string"));
  }

  @Test
  public void getStringMissing() {
    ConfigMap cm = new ArchaiusConfigMap();
    Assert.assertNull(cm.get("archaius.configmap.string-missing"));
  }
}
