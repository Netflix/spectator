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

import com.netflix.config.DynamicStringProperty;
import com.netflix.spectator.api.AbstractConfigMap;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Config map implementation backed by Archaius to support dynamic properties.
 */
public class ArchaiusConfigMap extends AbstractConfigMap {

  private final ConcurrentHashMap<String, DynamicStringProperty> dynProps =
      new ConcurrentHashMap<>();

  @Override public String get(String key) {
    DynamicStringProperty p = dynProps.get(key);
    if (p == null) {
      DynamicStringProperty tmp = new DynamicStringProperty(key, null);
      p = dynProps.putIfAbsent(key, tmp);
      if (p == null) {
        p = tmp;
      }
    }
    return p.get();
  }
}
