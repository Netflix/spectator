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

import com.netflix.spectator.api.BasicTag;
import com.netflix.spectator.api.Tag;

/**
 * Standard dimensions used for IPC metrics.
 */
public enum IpcTagKey {

  /** The library that produced the metric. */
  owner("owner"),

  /**
   * Indicates whether or not the request was successful. See {@link IpcResult}
   * for permitted values.
   */
  result("ipc.result"),

  /**
   * Dimension indicating a high level cause for the request failure. These groups
   * are the same for all implementations to make it easier to query across all
   * services and client implementations. See {@link IpcErrorGroup} for permitted
   * values.
   */
  errorGroup("ipc.error.group"),

  /**
   * Implementation specific error code.
   */
  errorReason("ipc.error.reason"),

  /**
   * Indicates the attempt number for the request.
   */
  attempt("ipc.attempt"),

  /**
   * Indicates if this is the final attempt for the request. For example, if the client
   * is configured to allow 1 retry, then the second attempt would be final. Acceptable
   * values are {@code true} and {@code false}.
   */
  attemptFinal("ipc.attempt.final"),

  /**
   * Identifier for the client instance making the request. This is typically the name used
   * to see the associated configuration settings for the client.
   */
  id("id"),

  /**
   * Eureka VIP used to select the server. If there are multiple vips in the list, then it
   * should be the first vip that caused the server to get selected.
   */
  vip("ipc.vip"),

  /**
   * The server side endpoint that is being requested. This should be a bounded set that
   * will have a small cardinality. <b>Do not pass in the unsanitized path value for a
   * URI.</b>
   */
  endpoint("ipc.endpoint"),

  /**
   * Protocol used for the request. For example {@code http_1} or {@code http_2}.
   */
  protocol("ipc.protocol"),

  /**
   * Region where the client is located.
   */
  clientRegion("ipc.client.region"),

  /**
   * Availability zone where the client is located.
   */
  clientZone("ipc.client.zone"),

  /**
   * Application that is running the client.
   */
  clientApp("ipc.client.app"),

  /**
   * Cluster that is running the client.
   */
  clientCluster("ipc.client.cluster"),

  /**
   * Server group that is running the client.
   */
  clientAsg("ipc.client.asg"),

  /**
   * Region where the server is located.
   */
  serverRegion("ipc.server.region"),

  /**
   * Availability zone where the server is located.
   */
  serverZone("ipc.server.zone"),

  /**
   * Application name for the server.
   */
  serverApp("ipc.server.app"),

  /**
   * Cluster name for the server.
   */
  serverCluster("ipc.server.cluster"),

  /**
   * Server group name for the server.
   */
  serverAsg("ipc.server.asg"),

  /**
   * Instance id for the server. <b>Do not use this unless you know it will not
   * cause a metrics explosion. If there is any doubt, then do not enable it.</b>
   */
  serverNode("ipc.server.node"),

  /**
   * The port number connected to on the server.
   */
  serverPort("ipc.server.port"),

  /**
   * HTTP status code.
   */
  httpStatus("http.status"),

  /**
   * HTTP method such as GET, PUT, or POST.
   */
  httpMethod("http.method");

  private final String key;

  /** Create a new instance. */
  IpcTagKey(String key) {
    this.key = key;
  }

  /** String to use as the tag key. */
  public String key() {
    return key;
  }

  /** Create a new tag with the specified value and this key. */
  public Tag tag(String value) {
    return new BasicTag(key, value);
  }
}
