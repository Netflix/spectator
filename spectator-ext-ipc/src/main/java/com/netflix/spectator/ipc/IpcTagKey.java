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
   * Indicates where the result was ultimately sourced from such as cache, direct,
   * proxy, fallback, etc. See {@link IpcSource} for possible values.
   */
  source("ipc.source"),

  /**
   * Dimension indicating a high level status for the request. These values are the same
   * for all implementations to make it easier to query across services. See {@link IpcStatus}
   * for permitted values.
   */
  status("ipc.status"),

  /**
   * Optional dimension indicating a more detailed status. The values for this are
   * implementation specific. For example, the {@link #status} may be
   * {@code connection_error} and {@code statusDetail} would be {@code no_servers},
   * {@code connect_timeout}, {@code ssl_handshake_failure}, etc.
   */
  statusDetail("ipc.status.detail"),

  /**
   * Dimension indicating a high level cause for the request failure. These groups
   * are the same for all implementations to make it easier to query across all
   * services and client implementations. See {@link IpcErrorGroup} for permitted
   * values.
   *
   * @deprecated Use {@link #status} instead. This value is scheduled for removal
   * in a future release.
   */
  @Deprecated
  errorGroup("ipc.error.group"),

  /**
   * Implementation specific error code.
   *
   * @deprecated Use {@link #statusDetail} instead. This value is scheduled for removal
   * in a future release.
   */
  @Deprecated
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
   * Method used to make the IPC request. See {@link IpcMethod} for possible values.
   */
  method("ipc.method"),

  /**
   * Region where the client is located.
   *
   * @deprecated Not included in the spec. Common IPC tags should bias towards consistency
   * across integrations and only use tags that are part of the spec. This value is scheduled
   * for removal in a future release.
   */
  @Deprecated
  clientRegion("ipc.client.region"),

  /**
   * Availability zone where the client is located.
   *
   * @deprecated Not included in the spec. Common IPC tags should bias towards consistency
   * across integrations and only use tags that are part of the spec. This value is scheduled
   * for removal in a future release.
   */
  @Deprecated
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
   *
   * @deprecated Not included in the spec. Common IPC tags should bias towards consistency
   * across integrations and only use tags that are part of the spec. This value is scheduled
   * for removal in a future release.
   */
  @Deprecated
  serverRegion("ipc.server.region"),

  /**
   * Availability zone where the server is located.
   *
   * @deprecated Not included in the spec. Common IPC tags should bias towards consistency
   * across integrations and only use tags that are part of the spec. This value is scheduled
   * for removal in a future release.
   */
  @Deprecated
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
   *
   * @deprecated Not included in the spec. Common IPC tags should bias towards consistency
   * across integrations and only use tags that are part of the spec. This value is scheduled
   * for removal in a future release.
   */
  @Deprecated
  serverNode("ipc.server.node"),

  /**
   * The port number connected to on the server.
   *
   * @deprecated Not included in the spec. Common IPC tags should bias towards consistency
   * across integrations and only use tags that are part of the spec. This value is scheduled
   * for removal in a future release.
   */
  @Deprecated
  serverPort("ipc.server.port"),

  /**
   * Indicates that an artificial failure was injected into the request processing for testing
   * purposes. The outcome of that failure will be reflected in the other error tags.
   * See {@link IpcFailureInjection} for permitted values.
   */
  failureInjected("ipc.failure.injected"),

  /**
   * HTTP status code. In most cases it is preferred to use {@link #statusDetail} instead.
   * This tag key is optionally used to include the HTTP status code when the status detail
   * is overridden with application specific values.
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
