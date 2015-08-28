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
package com.netflix.spectator.api;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SpectatorTest {
  @Test
  public void testRegistry() {
    Assert.assertNotNull(Spectator.registry());
  }

  @Test
  public void testNewInstanceBadClass() {
    Spectator.globalRegistry().removeAll();
    System.setProperty("spectator.api.propagateWarnings", "false");
    Spectator.addRegistries("fubar");
  }

  @Test(expected = RuntimeException.class)
  public void testNewInstanceBadClassPropagate() {
    System.setProperty("spectator.api.propagateWarnings", "true");
    Spectator.addRegistries("fubar");
  }
}
