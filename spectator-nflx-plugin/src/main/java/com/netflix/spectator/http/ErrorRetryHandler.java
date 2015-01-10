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
import iep.io.reactivex.netty.protocol.http.client.HttpClientRequest;
import iep.io.reactivex.netty.protocol.http.client.HttpClientResponse;
import iep.rx.Observable;
import iep.rx.functions.Func1;
import io.netty.handler.timeout.ReadTimeoutException;

import java.net.ConnectException;

/**
 * Helper for handling retries.
 */
class ErrorRetryHandler implements
    Func1<Throwable, Observable<? extends HttpClientResponse<ByteBuf>>> {

  private final HttpLogEntry entry;
  private final RxHttp.ClientConfig config;
  private final Server server;
  private final HttpClientRequest<ByteBuf> req;

  private final int attempt;

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
   * @param attempt
   *     The number of this attempt.
   */
  ErrorRetryHandler(
      HttpLogEntry entry,
      RxHttp.ClientConfig config,
      Server server,
      HttpClientRequest<ByteBuf> req,
      int attempt) {
    this.entry = entry;
    this.config = config;
    this.server = server;
    this.req = req;
    this.attempt = attempt;
  }

  @Override
  public Observable<? extends HttpClientResponse<ByteBuf>> call(Throwable throwable) {
    if (throwable instanceof ConnectException || throwable instanceof ReadTimeoutException) {
      entry.withAttempt(attempt);
      return RxHttp.execute(entry, config, server, req);
    }
    return Observable.error(throwable);
  }
}
