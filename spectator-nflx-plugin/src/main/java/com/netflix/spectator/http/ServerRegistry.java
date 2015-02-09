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
package com.netflix.spectator.http;

import java.util.List;

/**
 * Represents the server registry used for client-side load balancing. Primarily so we don't
 * need to create an actual DiscoveryClient instance in unit tests.
 */
public interface ServerRegistry {

  /**
   * Check if a server is still available.
   *
   * @param server
   *     Server to search for in the registry.
   * @return
   *     True if the server is still available.
   */
  boolean isStillAvailable(Server server);

  /**
   * Get a list of all available servers for the specified vip.
   *
   * @param vip
   *     ID used to lookup a group of servers. Vip name comes from eureka concept.
   * @param clientCfg
   *     Config associated with the client. This can be used for some additional filtering such
   *     as restricting it to only servers with a secure port.
   * @return
   *     List of all matching servers.
   */
  List<Server> getServers(String vip, ClientConfig clientCfg);
}
