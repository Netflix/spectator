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
package com.netflix.spectator.perf;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;

import java.util.Random;
import java.util.function.Consumer;

/**
 * Tests the memory overhead of many tag permutations. In general this covers: 1) the overhead
 * per meter, and 2) whether or not strings are interned when added to the tag sets.
 */
final class ManyTags implements Consumer<Registry> {

  @Override public void accept(Registry registry) {
    Random random = new Random(42);
    for (int i = 0; i < 10000; ++i) {
      Id id = registry.createId("manyTagsTest");
      for (int j = 0; j < 20; ++j) {
        String v = "" + random.nextInt(10);
        id = id.withTag("" + j, v);
      }
      registry.counter(id).increment();
    }
  }
}
