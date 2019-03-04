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
package com.netflix.spectator.atlas;

import com.netflix.spectator.impl.AsciiSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


public class AtlasConfigTest {

  @Test
  public void enabledByDefault() {
    Map<String, String> props = Collections.emptyMap();
    AtlasConfig config = props::get;
    Assertions.assertTrue(config.enabled());
  }

  @Test
  public void explicitlyEnabled() {
    Map<String, String> props = new HashMap<>();
    props.put("atlas.enabled", "true");
    AtlasConfig config = props::get;
    Assertions.assertTrue(config.enabled());
  }

  @Test
  public void explicitlyDisabled() {
    Map<String, String> props = new HashMap<>();
    props.put("atlas.enabled", "false");
    AtlasConfig config = props::get;
    Assertions.assertFalse(config.enabled());
  }

  @Test
  public void enabledBadValue() {
    Map<String, String> props = new HashMap<>();
    props.put("atlas.enabled", "abc");
    AtlasConfig config = props::get;
    Assertions.assertFalse(config.enabled());
  }

  @Test
  public void lwcDisabledByDefault() {
    Map<String, String> props = Collections.emptyMap();
    AtlasConfig config = props::get;
    Assertions.assertFalse(config.lwcEnabled());
  }

  @Test
  public void lwcExplicitlyEnabled() {
    Map<String, String> props = new HashMap<>();
    props.put("atlas.lwc.enabled", "true");
    AtlasConfig config = props::get;
    Assertions.assertTrue(config.lwcEnabled());
  }

  @Test
  public void lwcExplicitlyDisabled() {
    Map<String, String> props = new HashMap<>();
    props.put("atlas.lwc.enabled", "false");
    AtlasConfig config = props::get;
    Assertions.assertFalse(config.lwcEnabled());
  }

  @Test
  public void lwcEnabledBadValue() {
    Map<String, String> props = new HashMap<>();
    props.put("atlas.lwc.enabled", "abc");
    AtlasConfig config = props::get;
    Assertions.assertFalse(config.lwcEnabled());
  }

  @Test
  public void defaultValidCharsTilde() {
    Map<String, String> props = Collections.emptyMap();
    AtlasConfig config = props::get;
    AsciiSet set = AsciiSet.fromPattern(config.validTagCharacters());
    // quick sanity check of the allowed values
    Assertions.assertTrue(set.contains('7'));
    Assertions.assertTrue(set.contains('c'));
    Assertions.assertTrue(set.contains('C'));
    Assertions.assertTrue(set.contains('~'));
    Assertions.assertTrue(set.contains('_'));
    Assertions.assertFalse(set.contains('!'));
    Assertions.assertFalse(set.contains('%'));
    Assertions.assertFalse(set.contains('/'));
    Assertions.assertFalse(set.contains(':'));
  }
}
