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
package com.netflix.spectator.spark;

import com.netflix.spectator.api.DefaultId;
import com.netflix.spectator.api.Id;

import java.util.HashSet;
import java.util.Set;

/**
 * Maps hierarchical spark names into tagged ids. Spark names generally follow a pattern like:
 *
 * <pre>[appId].[executorId].[role].[name]</pre>
 */
public class SparkNameFunction implements NameFunction {

  private static final String PREFIX = "spark.";

  private static final Set<String> ROLES = new HashSet<>();
  static {
    ROLES.add("master");
    ROLES.add("worker");
    ROLES.add("executor");
    ROLES.add("driver");
    //ROLES.add("application");
  }

  private static final Id DROP_METRIC = null;

  @Override public Id apply(String name) {
    int p = name.indexOf('.');
    return (p == -1)
        ? DROP_METRIC
        : apply(name.substring(0, p), name.substring(p + 1));
  }

  private Id apply(String p1, String name) {
    Id id = null;
    if (ROLES.contains(p1)) {
      id = new DefaultId(PREFIX + name).withTag("role", p1);
    } else {
      int p = name.indexOf('.');
      id = (p == -1)
          ? DROP_METRIC
          : apply(p1, name.substring(0, p), name.substring(p + 1));
    }
    return id;
  }

  private Id apply(String appId, String p2, String name) {
    Id id = null;
    if (ROLES.contains(p2)) {
      id = new DefaultId(PREFIX + name).withTag("role", p2).withTag("appId", appId);
    } else {
      int p = name.indexOf('.');
      id = (p == -1)
          ? DROP_METRIC
          : apply(appId, p2, name.substring(0, p), name.substring(p + 1));
    }
    return id;
  }

  @SuppressWarnings("PMD.UseObjectForClearerAPI")
  private Id apply(String appId, String executorId, String p3, String name) {
    Id id = null;
    if (ROLES.contains(p3)) {
      id = new DefaultId(PREFIX + name)
          .withTag("role", p3)
          .withTag("appId", appId)
          .withTag("executorId", executorId);
    } else {
      id = DROP_METRIC;
    }
    return id;
  }
}
