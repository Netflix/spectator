/*
 * Copyright 2014-2022 Netflix, Inc.
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
package com.netflix.spectator.sidecar;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Helper for accessing common tags that are specific to a process and thus cannot be
 * managed by a shared spectatord instance.
 */
final class CommonTags {

  private CommonTags() {
  }

  /**
   * Extract common infrastructure tags from the Netflix environment variables.
   *
   * @param getenv
   *     Function used to retrieve the value of an environment variable.
   * @return
   *     Common tags based on the environment.
   */
  static Map<String, String> commonTags(Function<String, String> getenv) {
    Map<String, String> tags = new HashMap<>();
    putIfNotEmptyOrNull(getenv, tags, "nf.container", "TITUS_CONTAINER_NAME");
    putIfNotEmptyOrNull(getenv, tags, "nf.process", "NETFLIX_PROCESS_NAME");
    return tags;
  }

  private static void putIfNotEmptyOrNull(
      Function<String, String> getenv, Map<String, String> tags, String key, String... envVars) {
    for (String envVar : envVars) {
      String value = getenv.apply(envVar);
      if (value != null) {
        value = value.trim();
        if (!value.isEmpty()) {
          tags.put(key, value.trim());
          break;
        }
      }
    }
  }
}
