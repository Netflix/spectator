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

import com.netflix.spectator.api.RegistryConfig;

import java.util.Map;

/**
 * Configuration for sidecar registry.
 */
@FunctionalInterface
public interface SidecarConfig extends RegistryConfig {

  /**
   * Returns the location for where to emit the data. The default is
   * {@code udp://127.0.0.1:1234}. Supported values include:
   *
   * <ul>
   *   <li><code>none</code>: to disable output.</li>
   *   <li><code>stdout</code>: write to standard out for the process.</li>
   *   <li><code>stderr</code>: write to standard error for the process.</li>
   *   <li><code>file://$path_to_file</code>: write to a file.</li>
   *   <li><code>udp://$host:$port</code>: write to a UDP socket.</li>
   * </ul>
   */
  default String outputLocation() {
    String v = get("sidecar.output-location");
    return (v == null) ? "udp://127.0.0.1:1234" : v;
  }

  /**
   * Returns the common tags to apply to all metrics. By default these are
   * <code>nf.container</code> from <code>NETFLIX_CONTAINER_NAME</code> (falling back to
   * <code>TITUS_CONTAINER_NAME</code>) and <code>nf.process</code> from
   * <code>NETFLIX_PROCESS_NAME</code>; blank values are skipped. They are merged last onto every
   * meter id, so they override meter tags with the same key. Implementations that override this
   * method should start from <code>SidecarConfig.super.commonTags()</code> to keep them.
   */
  default Map<String, String> commonTags() {
    return CommonTags.commonTags(System::getenv);
  }
}
