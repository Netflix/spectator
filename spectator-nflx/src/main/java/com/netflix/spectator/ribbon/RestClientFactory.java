/**
 * Copyright 2014 Netflix, Inc.
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
package com.netflix.spectator.ribbon;

import com.netflix.client.ClientFactory;
import com.netflix.niws.client.http.RestClient;

/**
 * Helper for creating a {@link com.netflix.niws.client.http.RestClient} using the spectator
 * client config implementation.
 */
public final class RestClientFactory {
  private RestClientFactory() {
  }

  /**
   * Get or create a {@link com.netflix.niws.client.http.RestClient} with the specified name. The
   * client will use the {@link com.netflix.spectator.ribbon.RibbonClientConfigImpl} that changes
   * some of the defaults to make the common cases work easier:
   *
   * <ul>
   *   <li>Namespace for the clients defaults to {@code niws.client} to avoid property name changes
   *       if switching between internal {@code platform-ipc} and {@code ribbon}.</li>
   *   <li>The default server list class is set to {@code DiscoveryEnabledNIWSServerList}.</li>
   *   <li>An instrumented RestClient class is returned.</li>
   * </ul>
   *
   * @param name
   *     Name of the client to retrieve.
   * @return
   *     Rest client for the specified name.
   */
  public static RestClient getClient(String name) {
    return (RestClient) ClientFactory.getNamedClient(name, RibbonClientConfigImpl.class);
  }
}
