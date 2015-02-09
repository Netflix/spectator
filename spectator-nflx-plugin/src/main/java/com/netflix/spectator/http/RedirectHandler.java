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

  private final RequestContext context;

  private int redirect;

  /**
   * Create a new instance.
   *
   * @param context
   *     Context associated with the request.
   */
  RedirectHandler(RequestContext context) {
    this.context = context;
  }

  @Override
  public Observable<HttpClientResponse<ByteBuf>> call(HttpClientResponse<ByteBuf> res) {
    final int code = res.getStatus().code();
    Observable<HttpClientResponse<ByteBuf>> resObs;
    if (code > 300 && code <= 307) {
      final HttpClientRequest<ByteBuf> req = context.request();
      res.getContent().subscribe();
      final URI loc = URI.create(res.getHeaders().get(HttpHeaders.Names.LOCATION));
      context.entry().withRedirect(loc);
      if (loc.isAbsolute()) {
        // Should we allow redirect from https to http?
        final boolean secure = context.server().isSecure() || "https".equals(loc.getScheme());
        final Server s = new Server(loc.getHost(), RxHttp.getPort(loc), secure);
        final HttpClientRequest<ByteBuf> redirReq = RxHttp.copy(req, ClientConfig.relative(loc));
        resObs = context.rxHttp().execute(context.withRequest(redirReq).withServer(s));
      } else {
        final HttpClientRequest<ByteBuf> redirReq = RxHttp.copy(req, ClientConfig.relative(loc));
        resObs = context.rxHttp().execute(context.withRequest(redirReq));
      }

      ++redirect;
      if (redirect < context.config().followRedirects()) {
        resObs = resObs.flatMap(this);
      }
    } else {
      resObs = Observable.just(res);
    }
    return resObs;
  }
}
