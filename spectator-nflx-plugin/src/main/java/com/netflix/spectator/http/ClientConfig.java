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

import com.netflix.spectator.api.Spectator;

import java.net.URI;

/** Configuration settings to use for making the request. */
class ClientConfig {

  private final String name;
  private final String vipAddress;
  private final URI originalUri;
  private final URI uri;

  /** Create a new instance. */
  ClientConfig(String name, String vipAddress, URI originalUri, URI uri) {
    this.name = name;
    this.vipAddress = vipAddress;
    this.originalUri = originalUri;
    this.uri = uri;
  }

  private String prop(String k) {
    return name + ".niws.client." + k;
  }

  /** Name of the client. */
  String name() {
    return name;
  }

  /** Original URI specified before selecting a specific server. */
  URI originalUri() {
    return originalUri;
  }

  /** URI for the request. */
  URI uri() {
    return uri;
  }

  /** Port to use for the connection. */
  int port(int dflt) {
    return Spectator.config().getInt(prop("Port"), dflt);
  }

  /** Maximum time to wait for a connection attempt in milliseconds. */
  int connectTimeout() {
    return Spectator.config().getInt(prop("ConnectTimeout"), 1000);
  }

  /** Maximum time to wait for reading data in milliseconds. */
  int readTimeout() {
    return Spectator.config().getInt(prop("ReadTimeout"), 30000);
  }

  /** Maximum number of redirects to follow. Set to 0 to disable. */
  int followRedirects() {
    return Spectator.config().getInt(prop("FollowRedirects"), 3);
  }

  /** Should HTTPS be used for the request? */
  boolean isSecure() {
    final boolean https = "https".equals(uri.getScheme());
    return https || Spectator.config().getBoolean(prop("IsSecure"), false);
  }

  /**
   * When getting a server list from eureka should the host name or ip address be used? The
   * default is to use the ip address and avoid the dns lookup.
   */
  boolean useIpAddress() {
    return Spectator.config().getBoolean(prop("UseIpAddress"), false);
  }

  /**
   * Should it attempt to compress the request body and automatically decompress the response
   * body?
   */
  boolean gzipEnabled() {
    return Spectator.config().getBoolean(prop("GzipEnabled"), true);
  }

  /** Max number of retries. */
  int numRetries() {
    return Spectator.config().getInt(prop("MaxAutoRetriesNextServer"), 2);
  }

  /**
   * Initial delay to use between retries if a throttled response (429 or 503) is received. The
   * delay will be doubled between each throttled attempt.
   */
  int retryDelay() {
    return Spectator.config().getInt(prop("RetryDelay"), 500);
  }

  /** Max size of the request body. Defaults to 10MB. */
  int aggregationLimit() {
    return Spectator.config().getInt(prop("AggregationLimit"), 10 * 1024 * 1024);
  }

  /** User agent string to use when making the request. */
  String userAgent() {
    return Spectator.config().get(prop("UserAgent"), "RxHttp");
  }

  /** VIP used to lookup a set of servers in eureka. */
  String vip() {
    return (vipAddress == null)
        ? Spectator.config().get(prop("DeploymentContextBasedVipAddresses"))
        : vipAddress;
  }
}
