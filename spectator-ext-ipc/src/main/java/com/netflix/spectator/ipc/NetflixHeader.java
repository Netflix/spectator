/*
 * Copyright 2014-2018 Netflix, Inc.
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

/**
 * Headers in use at Netflix to relay information between the client and the server. This
 * information provides additional context for logs and is used to tag the IPC metrics
 * consistently between the client and the server.
 */
public enum NetflixHeader {

  /**
   * Server group name for the client or the server. It should follow the naming conventions
   * expected by Frigga. See {@link ServerGroup} for more information.
   */
  ASG,

  /**
   * Availability zone of the client or server instance.
   */
  Zone,

  /**
   * Instance id of the client or server.
   */
  Node,

  /**
   * Route or route handler for a given path. It should have a fixed cardinality. For HTTP
   * this would need to come from the server so there is agreement and the client will report
   * the same value.
   */
  Endpoint,

  /**
   * VIP that was used to lookup instances for a service when using a client side load balancer.
   * This should be set on the client request to the vip used for the lookup. In the case of NIWS,
   * that would be the VIP used for the DeploymentContextBasedVipAddresses. If multiple VIPs are
   * used, then the first VIP that caused a given server instance to be selected should be used.
   *
   * For server side load balancers the VIP header should be omitted.
   */
  Vip;

  private final String headerName = "Netflix-" + name();

  /** Return the fully qualified header name. */
  public String headerName() {
    return headerName;
  }
}
