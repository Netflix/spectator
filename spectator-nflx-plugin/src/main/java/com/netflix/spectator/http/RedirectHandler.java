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
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpHeaders;
import iep.io.reactivex.netty.protocol.http.client.HttpClientRequest;
import iep.io.reactivex.netty.protocol.http.client.HttpClientResponse;
import iep.rx.Observable;
import iep.rx.functions.Func1;

import java.net.URI;

/**
 * Helper for handling redirects.
 */
class RedirectHandler implements
    Func1<HttpClientResponse<ByteBuf>, Observable<HttpClientResponse<ByteBuf>>> {

  private final HttpLogEntry entry;
  private final HttpClientRequest<ByteBuf> req;
  private final RxHttp.ClientConfig config;
  private final Server server;

  private int redirect;

  /**
   * Create a new instance.
   *
   * @param entry
   *     Log entry to update for each request.
   * @param config
   *     Config settings to use, FollowRedirects setting will determine how deep to go before
   *     giving up.
   * @param server
   *     Server for the original request. In the case of relative URI in the Location header this
   *     is the server that will be used for the redirect.
   * @param req
   *     Original request.
   */
  RedirectHandler(
      HttpLogEntry entry,
      RxHttp.ClientConfig config,
      Server server,
      HttpClientRequest<ByteBuf> req) {
    this.entry = entry;
    this.config = config;
    this.server = server;
    this.req = req;
  }

  @Override
  public Observable<HttpClientResponse<ByteBuf>> call(HttpClientResponse<ByteBuf> res) {
    final int code = res.getStatus().code();
    Observable<HttpClientResponse<ByteBuf>> resObs;
    if (code > 300 && code <= 307) {
      res.getContent().subscribe();
      final URI loc = URI.create(res.getHeaders().get(HttpHeaders.Names.LOCATION));
      entry.withRedirect(loc);
      if (loc.isAbsolute()) {
        // Should we allow redirect from https to http?
        final boolean secure = server.isSecure() || "https".equals(loc.getScheme());
        final Server s = new Server(loc.getHost(), RxHttp.getPort(loc), secure);
        final HttpClientRequest<ByteBuf> redirReq = RxHttp.copy(req, RxHttp.relative(loc));
        resObs = RxHttp.execute(entry, config, s, redirReq);
      } else {
        final HttpClientRequest<ByteBuf> redirReq = RxHttp.copy(req, RxHttp.relative(loc));
        resObs = RxHttp.execute(entry, config, server, redirReq);
      }

      ++redirect;
      if (redirect < config.followRedirects()) {
        resObs = resObs.flatMap(this);
      }
    } else {
      resObs = Observable.just(res);
    }
    return resObs;
  }
}
