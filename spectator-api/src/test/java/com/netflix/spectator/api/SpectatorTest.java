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
package com.netflix.spectator.api;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SpectatorTest {
  @Test
  public void testRegistry() {
    Assertions.assertNotNull(Spectator.registry());
  }

  @Test
  public void globalIterator() {
    Registry dflt = new DefaultRegistry();
    CompositeRegistry global = Spectator.globalRegistry();
    global.removeAll();
    global.add(dflt);

    boolean found = false;
    Counter counter = dflt.counter("testCounter");
    for (Meter m : global) {
      found |= m.id().equals(counter.id());
    }
    Assertions.assertTrue(found, "id for sub-registry could not be found in global iterator");
  }

  @Test
  public void globalIteratorWithDifferences() {
    Registry r1 = new DefaultRegistry();
    Registry r2 = new DefaultRegistry();
    CompositeRegistry global = Spectator.globalRegistry();
    global.removeAll();
    global.add(r1);
    global.add(r2);

    boolean found = false;
    Counter counter = r2.counter("testCounter");
    for (Meter m : global) {
      found |= m.id().equals(counter.id());
    }
    Assertions.assertTrue(found, "id for sub-registry could not be found in global iterator");
  }
}
