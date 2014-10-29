/**
 * Copyright 2014 Netflix, Inc.
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
package com.netflix.spectator.api;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.NoSuchElementException;

@RunWith(JUnit4.class)
public class SystemConfigMapTest {

  @Before
  public void initProps() {
    System.setProperty("system.configmap.string", "foo");
    System.setProperty("system.configmap.int", "42");
    System.setProperty("system.configmap.long", "1234567890987654321");
    System.setProperty("system.configmap.double", "1e3");
    System.setProperty("system.configmap.true", "true");
    System.setProperty("system.configmap.false", "false");
  }

  @Test
  public void getString() {
    ConfigMap cm = new SystemConfigMap();
    Assert.assertEquals("foo", cm.get("system.configmap.string"));
  }

  @Test
  public void getStringMissing() {
    ConfigMap cm = new SystemConfigMap();
    Assert.assertNull(cm.get("system.configmap.string-missing"));
  }

  @Test
  public void getStringWithDefault() {
    ConfigMap cm = new SystemConfigMap();
    Assert.assertEquals("foo", cm.get("system.configmap.string", "bar"));
    Assert.assertEquals("bar", cm.get("system.configmap.string-missing", "bar"));
  }

  @Test
  public void getInt() {
    ConfigMap cm = new SystemConfigMap();
    Assert.assertEquals(42, cm.getInt("system.configmap.int"));
  }

  @Test(expected = NoSuchElementException.class)
  public void getIntMissing() {
    ConfigMap cm = new SystemConfigMap();
    cm.getInt("system.configmap.int-missing");
  }

  @Test
  public void getIntWithDefault() {
    ConfigMap cm = new SystemConfigMap();
    Assert.assertEquals(42, cm.getInt("system.configmap.int", 5));
    Assert.assertEquals(42, cm.getInt("system.configmap.int-missing", 42));
  }

  @Test
  public void getLong() {
    ConfigMap cm = new SystemConfigMap();
    Assert.assertEquals(1234567890987654321L, cm.getLong("system.configmap.long"));
  }

  @Test(expected = NoSuchElementException.class)
  public void getLongMissing() {
    ConfigMap cm = new SystemConfigMap();
    cm.getLong("system.configmap.long-missing");
  }

  @Test
  public void getLongWithDefault() {
    ConfigMap cm = new SystemConfigMap();
    Assert.assertEquals(1234567890987654321L, cm.getLong("system.configmap.long", 42));
    Assert.assertEquals(42L, cm.getLong("system.configmap.long-missing", 42));
  }

  @Test
  public void getDouble() {
    ConfigMap cm = new SystemConfigMap();
    Assert.assertEquals(1000.0, cm.getDouble("system.configmap.double"), 1e-12);
  }

  @Test(expected = NoSuchElementException.class)
  public void getDoubleMissing() {
    ConfigMap cm = new SystemConfigMap();
    cm.getDouble("system.configmap.double-missing");
  }

  @Test
  public void getDoubleWithDefault() {
    ConfigMap cm = new SystemConfigMap();
    Assert.assertEquals(1000.0, cm.getDouble("system.configmap.double", 42.0), 1e-12);
    Assert.assertEquals(42.0, cm.getDouble("system.configmap.double-missing", 42.0), 1e-12);
  }

  @Test
  public void getBooleanTrue() {
    ConfigMap cm = new SystemConfigMap();
    Assert.assertEquals(true, cm.getBoolean("system.configmap.true"));
  }

  @Test
  public void getBooleanFalse() {
    ConfigMap cm = new SystemConfigMap();
    Assert.assertEquals(false, cm.getBoolean("system.configmap.false"));
  }

  @Test(expected = NoSuchElementException.class)
  public void getBooleanMissing() {
    ConfigMap cm = new SystemConfigMap();
    cm.getBoolean("system.configmap.true-missing");
  }

  @Test
  public void getBooleanWithDefault() {
    ConfigMap cm = new SystemConfigMap();
    Assert.assertEquals(true, cm.getBoolean("system.configmap.true", false));
    Assert.assertEquals(true, cm.getBoolean("system.configmap.true-missing", true));
  }
}
