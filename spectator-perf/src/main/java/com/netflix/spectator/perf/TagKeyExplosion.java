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
package com.netflix.spectator.perf;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;

import java.util.function.Consumer;

/**
 * Tests the ability to cope with an explosion of tag keys.
 */
final class TagKeyExplosion implements Consumer<Registry> {

  @Override public void accept(Registry registry) {
    for (int i = 0; i < 1000000; ++i) {
      Id id = registry.createId("tagKeyExplosion").withTag("k-" + i, "foo");
      registry.counter(id).increment();
    }
  }
}
