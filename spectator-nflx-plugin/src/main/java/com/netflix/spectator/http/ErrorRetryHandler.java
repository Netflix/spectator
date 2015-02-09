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

  private final RequestContext context;

  private final int attempt;

  /**
   * Create a new instance.
   *
   * @param context
   *     Context associated with the request.
   * @param attempt
   *     The number of this attempt.
   */
  ErrorRetryHandler(RequestContext context, int attempt) {
    this.context = context;
    this.attempt = attempt;
  }

  @Override
  public Observable<? extends HttpClientResponse<ByteBuf>> call(Throwable throwable) {
    if (throwable instanceof ConnectException || throwable instanceof ReadTimeoutException) {
      context.entry().withAttempt(attempt);
      return context.rxHttp().execute(context);
    }
    return Observable.error(throwable);
  }
}
