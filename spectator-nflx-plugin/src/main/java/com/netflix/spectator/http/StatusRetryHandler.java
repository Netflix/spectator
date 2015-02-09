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
import iep.io.reactivex.netty.protocol.http.client.HttpClientResponse;
import iep.rx.Observable;
import iep.rx.functions.Func1;

import java.util.concurrent.TimeUnit;

/**
 * Helper for handling retries based on the status code of the response.
 */
class StatusRetryHandler implements
    Func1<HttpClientResponse<ByteBuf>, Observable<HttpClientResponse<ByteBuf>>> {

  private final RequestContext context;

  private final int attempt;
  private final long delay;

  /**
   * Create a new instance.
   *
   * @param context
   *     Context associated with the request.
   * @param attempt
   *     The number of this attempt.
   * @param delay
   *     How long to wait before making another attempt. Unit is in milliseconds. If a
   *     {@code Retry-After} header is set on the response it will take precedence over the
   *     default delay.
   */
  StatusRetryHandler(RequestContext context, int attempt, long delay) {
    this.context = context;
    this.attempt = attempt;
    this.delay = delay;
  }

  private static long getRetryDelay(HttpClientResponse<ByteBuf> res, long dflt) {
    try {
      if (res.getHeaders().contains(HttpHeaders.Names.RETRY_AFTER)) {
        // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.37
        int delaySeconds = res.getHeaders().getIntHeader(HttpHeaders.Names.RETRY_AFTER);
        return TimeUnit.MILLISECONDS.convert(delaySeconds, TimeUnit.SECONDS);
      }
    } catch (NumberFormatException e) {
      // We don't support the date version, so use dflt in this case
      return dflt;
    }
    return dflt;
  }

  @Override
  public Observable<HttpClientResponse<ByteBuf>> call(HttpClientResponse<ByteBuf> res) {
    final int code = res.getStatus().code();
    Observable<HttpClientResponse<ByteBuf>> resObs;
    if (code == 429 || code == 503) {
      final long retryDelay = getRetryDelay(res, delay);
      res.getContent().subscribe();
      context.entry().withAttempt(attempt);
      resObs = context.rxHttp().execute(context);
      if (retryDelay > 0) {
        resObs = resObs.delaySubscription(retryDelay, TimeUnit.MILLISECONDS);
      }
    } else if (code >= 500) {
      res.getContent().subscribe();
      context.entry().withAttempt(attempt);
      resObs = context.rxHttp().execute(context);
    } else {
      resObs = Observable.just(res);
    }
    return resObs;
  }
}
