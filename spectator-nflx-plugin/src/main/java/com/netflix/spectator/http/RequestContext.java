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

import com.netflix.spectator.sandbox.HttpLogEntry;
import iep.io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.netty.buffer.ByteBuf;

/**
 * Tuple for the handful of fields we need to pass around for the requests.
 */
final class RequestContext {

  private final RxHttp rxHttp;
  private final HttpLogEntry entry;
  private final HttpClientRequest<ByteBuf> req;
  private final ClientConfig config;
  private final Server server;

  /** Create a new instance. */
  RequestContext(
      RxHttp rxHttp,
      HttpLogEntry entry,
      HttpClientRequest<ByteBuf> req,
      ClientConfig config,
      Server server) {
    this.rxHttp = rxHttp;
    this.entry = entry;
    this.req = req;
    this.config = config;
    this.server = server;
  }

  /** Return the RxHttp instance being used for the request. */
  RxHttp rxHttp() {
    return rxHttp;
  }

  /** Return the log entry for the request. */
  HttpLogEntry entry() {
    return entry;
  }

  /** Return the RxNetty request object. */
  HttpClientRequest<ByteBuf> request() {
    return req;
  }

  /** Return the configuration settings for the request. */
  ClientConfig config() {
    return config;
  }

  /** Return the server to call for the request. */
  Server server() {
    return server;
  }

  /** Return a new context with the specified request. */
  RequestContext withRequest(HttpClientRequest<ByteBuf> r) {
    return new RequestContext(rxHttp, entry, r, config, server);
  }

  /** Return a new context with the specified server. */
  RequestContext withServer(Server s) {
    return new RequestContext(rxHttp, entry, req, config, s);
  }
}
