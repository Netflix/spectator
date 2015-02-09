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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Configuration settings to use for making the request. */
class ClientConfig {

  private static final Pattern NIWS_URI = Pattern.compile("niws://([^/]+).*");

  private static final Pattern VIP_URI = Pattern.compile("vip://([^:]+):([^/]+).*");

  /** Create relative uri string with the path and query. */
  static String relative(URI uri) {
    String r = uri.getRawPath();
    if (uri.getRawQuery() != null) {
      r += "?" + uri.getRawQuery();
    }
    return r;
  }

  private static String fixPath(String path) {
    return (path.startsWith("/http://") || path.startsWith("/https://"))
        ? path.substring(1)
        : path;
  }

  /** Create a client config instance based on a URI. */
  static ClientConfig fromUri(URI uri) {
    Matcher m = null;
    ClientConfig cfg = null;
    switch (uri.getScheme()) {
      case "niws":
        m = NIWS_URI.matcher(uri.toString());
        if (m.matches()) {
          final URI newUri = URI.create(fixPath(relative(uri)));
          cfg = new ClientConfig(m.group(1), null, uri, newUri);
        } else {
          throw new IllegalArgumentException("invalid niws uri: " + uri);
        }
        break;
      case "vip":
        m = VIP_URI.matcher(uri.toString());
        if (m.matches()) {
          cfg = new ClientConfig(m.group(1), m.group(2), uri, URI.create(relative(uri)));
        } else {
          throw new IllegalArgumentException("invalid vip uri: " + uri);
        }
        break;
      default:
        cfg = new ClientConfig("default", null, uri, uri);
        break;
    }
    return cfg;
  }

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

  private String dfltProp(String k) {
    return "niws.client." + k;
  }

  private String prop(String k) {
    return name + "." + dfltProp(k);
  }

  private String getString(String k, String dflt) {
    String v = Spectator.config().get(prop(k));
    return (v == null) ? Spectator.config().get(dfltProp(k), dflt) : v;
  }

  private int getInt(String k, int dflt) {
    String v = getString(k, null);
    return (v == null) ? dflt : Integer.parseInt(v);
  }

  private boolean getBoolean(String k, boolean dflt) {
    String v = getString(k, null);
    return (v == null) ? dflt : Boolean.parseBoolean(v);
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
    return getInt("Port", dflt);
  }

  /** Maximum time to wait for a connection attempt in milliseconds. */
  int connectTimeout() {
    return getInt("ConnectTimeout", 1000);
  }

  /** Maximum time to wait for reading data in milliseconds. */
  int readTimeout() {
    return getInt("ReadTimeout", 30000);
  }

  /** Maximum number of redirects to follow. Set to 0 to disable. */
  int followRedirects() {
    return getInt("FollowRedirects", 3);
  }

  /** Should HTTPS be used for the request? */
  boolean isSecure() {
    final boolean https = "https".equals(uri.getScheme());
    return https || getBoolean("IsSecure", false);
  }

  /**
   * When getting a server list from eureka should the host name or ip address be used? The
   * default is to use the ip address and avoid the dns lookup.
   */
  boolean useIpAddress() {
    return getBoolean("UseIpAddress", false);
  }

  /**
   * Should it attempt to compress the request body and automatically decompress the response
   * body?
   */
  boolean gzipEnabled() {
    return getBoolean("GzipEnabled", true);
  }

  /** Max number of retries. */
  int numRetries() {
    return getInt("MaxAutoRetriesNextServer", 2);
  }

  /**
   * Initial delay to use between retries if a throttled response (429 or 503) is received. The
   * delay will be doubled between each throttled attempt.
   */
  int retryDelay() {
    return getInt("RetryDelay", 500);
  }

  /** User agent string to use when making the request. */
  String userAgent() {
    return getString("UserAgent", "RxHttp");
  }

  /** VIP used to lookup a set of servers in eureka. */
  String vip() {
    return (vipAddress == null)
        ? getString("DeploymentContextBasedVipAddresses", null)
        : vipAddress;
  }
}
