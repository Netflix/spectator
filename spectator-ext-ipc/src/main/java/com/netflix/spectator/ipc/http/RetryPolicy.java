/*
 * Copyright 2014-2020 Netflix, Inc.
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
package com.netflix.spectator.ipc.http;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Locale;

/**
 * Policy that determines if a request can be retried based on the response.
 */
public interface RetryPolicy {

  /** Retry all requests. */
  RetryPolicy ALL = new RetryPolicy() {
    @Override public boolean shouldRetry(String method, Throwable t) {
      return true;
    }

    @Override public boolean shouldRetry(String method, HttpResponse response) {
      return true;
    }
  };

  /**
   * Retry operations that are known to be safe without impacting the results and the operation
   * will potentially have a different response.
   */
  RetryPolicy SAFE = new RetryPolicy() {
    /**
     * For modifications, only retries on connection exceptions. Others such as a read timeout
     * may have already started doing some work. Reads can be retried on all exceptions.
     */
    @Override public boolean shouldRetry(String method, Throwable t) {
      return isConnectException(t) || allowedMethod(method);
    }

    private boolean isConnectException(Throwable t) {
      return t instanceof ConnectException || isConnectTimeout(t);
    }

    /**
     * This is fragile and based on the message, but not sure of a better way. Expecting:
     * <pre>
     * java.net.SocketTimeoutException: connect timed out
     * </pre>
     */
    private boolean isConnectTimeout(Throwable t) {
      return t instanceof SocketTimeoutException
          && t.getMessage() != null
          && t.getMessage().toLowerCase(Locale.US).contains("connect");
    }

    @Override public boolean shouldRetry(String method, HttpResponse response) {
      return isThrottled(response.status()) || isAllowed(method, response);
    }

    private boolean isThrottled(int status) {
      return status == 429 || status == 503;
    }

    private boolean isAllowed(String method, HttpResponse response) {
      return allowedMethod(method) && allowedStatus(response.status());
    }

    private boolean allowedMethod(String method) {
      return "GET".equals(method) || "HEAD".equals(method);
    }

    private boolean allowedStatus(int status) {
      return status >= 500;
    }
  };

  /**
   * Returns true if the request should be retried when it fails with an exception.
   */
  boolean shouldRetry(String method, Throwable t);

  /**
   * Returns true if the request should be retried when it fails with an HTTP error code.
   */
  boolean shouldRetry(String method, HttpResponse response);
}
