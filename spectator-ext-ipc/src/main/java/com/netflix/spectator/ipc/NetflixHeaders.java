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
package com.netflix.spectator.ipc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Helper for extracting Netflix header values from the environment variables available
 * on the base ami. For more information see:
 *
 * https://github.com/Netflix/iep/tree/master/iep-nflxenv
 */
public final class NetflixHeaders {

  private NetflixHeaders() {
  }

  private static final String[] NETFLIX_ASG = {
      "NETFLIX_AUTO_SCALE_GROUP",
      "CLOUD_AUTO_SCALE_GROUP"
  };

  private static final String[] NETFLIX_NODE = {
      "TITUS_TASK_ID",
      "EC2_INSTANCE_ID"
  };

  private static final String[] NETFLIX_ZONE = {
      "EC2_AVAILABILITY_ZONE"
  };

  private static void addHeader(
      Map<String, String> headers,
      Function<String, String> env,
      NetflixHeader header,
      String[] names) {
    for (String name : names) {
      String value = env.apply(name);
      if (value != null && !value.isEmpty()) {
        headers.put(header.headerName(), value);
        break;
      }
    }
  }

  /**
   * Extract common Netflix headers from the specified function for retrieving the value
   * of an environment variable.
   */
  public static Map<String, String> extractFrom(Function<String, String> env) {
    Map<String, String> headers = new LinkedHashMap<>();
    addHeader(headers, env, NetflixHeader.ASG, NETFLIX_ASG);
    addHeader(headers, env, NetflixHeader.Node, NETFLIX_NODE);
    addHeader(headers, env, NetflixHeader.Zone, NETFLIX_ZONE);
    return headers;
  }

  /**
   * Extract common Netflix headers from environment variables on the system.
   */
  public static Map<String, String> extractFromEnvironment() {
    return extractFrom(System::getenv);
  }
}
