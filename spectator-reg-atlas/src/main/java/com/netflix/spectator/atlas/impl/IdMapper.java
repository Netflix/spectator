/*
 * Copyright 2014-2023 Netflix, Inc.
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
package com.netflix.spectator.atlas.impl;

import com.netflix.spectator.api.Id;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Default mapping function from an Id to a Map.
 *
 * <p><b>Classes in this package are only intended for use internally within spectator. They may
 * change at any time and without notice.</b>
 */
public final class IdMapper implements BiFunction<Id, Set<String>, Map<String, String>> {

  @Override
  public Map<String, String> apply(Id id, Set<String> keys) {
    int size = id.size();
    Map<String, String> tags = new HashMap<>(keys.size());

    // Start at 1 as name will be added last
    for (int i = 1; i < size; ++i) {
      String k = id.getKey(i);
      if (keys.contains(k)) {
        tags.put(k, id.getValue(i));
      }
    }

    // Add the name, it is added last so it will have precedence if the user tried to
    // use a tag key of "name".
    if (keys.contains("name")) {
      tags.put("name", id.name());
    }

    return tags;
  }
}
