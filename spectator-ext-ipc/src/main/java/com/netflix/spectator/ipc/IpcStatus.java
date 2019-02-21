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

import com.netflix.spectator.api.Tag;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

/**
 * Dimension indicating the high level status for the request.
 *
 * @see IpcTagKey#status
 */
public enum IpcStatus implements Tag {

  /**
   * The request was successfully processed and responded to, as far as the client or server
   * know.
   */
  success,

  /**
   * There was a problem with the clients' request causing it not to be fulfilled.
   */
  bad_request,

  /**
   * The client or server encountered an unexpected error processing the request.
   */
  unexpected_error,

  /**
   * There was an error with the underlying network connection either during establishment
   * or while in use.
   */
  connection_error,

  /**
   * There were no servers available to process the request.
   */
  unavailable,

  /**
   * The request was rejected due to the client or server considering the server to be
   * above capacity.
   */
  throttled,

  /**
   * The request could not or would not be complete within the configured threshold (either
   * on client or server).
   */
  timeout,

  /**
   * The client cancelled the request before it was completed.
   */
  cancelled,

  /**
   * The request was denied access for authentication or authorization reasons.
   */
  access_denied;

  @Override public String key() {
    return IpcTagKey.status.key();
  }

  @Override public String value() {
    return name();
  }

  /**
   * Return the corresponding IPC result.
   */
  public IpcResult result() {
    return this == success ? IpcResult.success : IpcResult.failure;
  }

  /**
   * Maps HTTP status codes to the appropriate status. Note, this method follows the historical
   * convention in Netflix where services would use the service unavailable,
   * <a href="https://tools.ietf.org/html/rfc7231#section-6.6.4">503</a> status code, to indicate
   * throttling. To get behavior inline with the RFCs use {@link #forHttpStatusStandard(int)}
   * instead.
   *
   * @param httpStatus
   *     HTTP status for the request.
   * @return
   *     Status value corresponding to the HTTP status code.
   */
  public static IpcStatus forHttpStatus(int httpStatus) {
    return forHttpStatusStandard(httpStatus == 503 ? 429 : httpStatus);
  }

  /**
   * Maps HTTP status codes to the appropriate status based on the standard RFC definitions.
   * In particular, <a href="https://tools.ietf.org/html/rfc7231#section-6.6.4">503</a> maps
   * to {@link #unavailable} and <a href="https://tools.ietf.org/html/rfc6585#section-4">429</a>
   * maps to {@link #throttled}.
   *
   * @param httpStatus
   *     HTTP status for the request.
   * @return
   *     Status value corresponding to the HTTP status code.
   */
  public static IpcStatus forHttpStatusStandard(int httpStatus) {
    IpcStatus status;
    switch (httpStatus) {
      case 200: status = success;       break; // OK
      case 401: status = access_denied; break; // Unauthorized
      case 402: status = access_denied; break; // Payment Required
      case 403: status = access_denied; break; // Forbidden
      case 404: status = success;       break; // Not Found
      case 405: status = bad_request;   break; // Method Not Allowed
      case 406: status = bad_request;   break; // Not Acceptable
      case 407: status = access_denied; break; // Proxy Authentication Required
      case 408: status = timeout;       break; // Request Timeout
      case 429: status = throttled;     break; // Too Many Requests
      case 503: status = unavailable;   break; // Unavailable
      case 511: status = access_denied; break; // Network Authentication Required
      default:
        if (httpStatus < 100) {
          // Shouldn't get here, but since the input value isn't validated it is a possibility
          status = unexpected_error;
        } else if (httpStatus < 400) {
          // All 1xx, 2xx, and 3xx unless otherwise specified will be marked as a success
          status = success;
        } else if (httpStatus < 500) {
          // All 4xx unless otherwise specified will be marked as a bad request
          status = bad_request;
        } else {
          // Anything else is unexpected
          status = unexpected_error;
        }
        break;
    }
    return status;
  }

  /**
   * Maps common exceptions from the JDK to the appropriate status.
   *
   * @param t
   *     Exception to map to a status.
   * @return
   *     Status corresponding to the passed in exception.
   */
  public static IpcStatus forException(Throwable t) {
    IpcStatus status;
    if (t instanceof SSLException) {
      status = access_denied;
    } else if (t instanceof SocketTimeoutException || t instanceof TimeoutException) {
      status = timeout;
    } else if (t instanceof IOException) {
      status = connection_error;
    } else if (t instanceof IllegalArgumentException || t instanceof IllegalStateException) {
      status = bad_request;
    } else {
      status = unexpected_error;
    }
    return status;
  }
}
