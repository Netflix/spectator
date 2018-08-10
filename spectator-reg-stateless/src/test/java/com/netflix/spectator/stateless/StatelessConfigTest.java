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
package com.netflix.spectator.stateless;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


@RunWith(JUnit4.class)
public class StatelessConfigTest {

  @Test
  public void enabledByDefault() {
    Map<String, String> props = Collections.emptyMap();
    StatelessConfig config = props::get;
    Assert.assertTrue(config.enabled());
  }

  @Test
  public void explicitlyEnabled() {
    Map<String, String> props = new HashMap<>();
    props.put("stateless.enabled", "true");
    StatelessConfig config = props::get;
    Assert.assertTrue(config.enabled());
  }

  @Test
  public void explicitlyDisabled() {
    Map<String, String> props = new HashMap<>();
    props.put("stateless.enabled", "false");
    StatelessConfig config = props::get;
    Assert.assertFalse(config.enabled());
  }

  @Test
  public void enabledBadValue() {
    Map<String, String> props = new HashMap<>();
    props.put("stateless.enabled", "abc");
    StatelessConfig config = props::get;
    Assert.assertFalse(config.enabled());
  }
}
