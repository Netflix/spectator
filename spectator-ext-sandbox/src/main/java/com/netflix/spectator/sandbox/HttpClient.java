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
package com.netflix.spectator.sandbox;

import java.net.URI;

/**
 * Simple blocking http client using {@link HttpLogEntry} and
 * {@link java.net.HttpURLConnection}. This can be used as an example of the logging
 * or for light use-cases where it is more desirable not to have dependencies on
 * a more robust HTTP library. Usage:
 *
 * <pre>
 * HttpClient client = HttpClient.DEFAULT;
 * HttpResponse response = client.get(URI.create("http://example.com")).send();
 * </pre>
 *
 * For testing an alternative client implementation can be used to customize the
 * send. For example to create a client that will always fail:
 *
 * <pre>
 * HttpClient client = (n, u) -> new HttpRequestBuilder(n, u) {
 *   {@literal @}Override protected HttpResponse sendImpl() throws IOException {
 *     throw new ConnectException("could not connect to " + u.getHost());
 *   }
 * };
 * </pre>
 *
 * @deprecated Moved to {@code com.netflix.spectator.ipc.http} package. This is now just a
 * thin wrapper to preserve compatibility. This class is scheduled for removal in a future release.
 */
@Deprecated
public interface HttpClient {

  /** Client implementation based on {@link java.net.HttpURLConnection}. */
  HttpClient DEFAULT = HttpRequestBuilder::new;

  /**
   * Create a new request builder.
   *
   * @param clientName
   *     Name used to identify this client.
   * @param uri
   *     URI to use for the request.
   * @return
   *     Builder for creating and executing a request.
   */
  HttpRequestBuilder newRequest(String clientName, URI uri);

  /**
   * Create a new GET request builder. The client name will be selected based
   * on a prefix of the host name.
   *
   * @param uri
   *     URI to use for the request.
   * @return
   *     Builder for creating and executing a request.
   */
  default HttpRequestBuilder get(URI uri) {
    return newRequest(HttpUtils.clientNameForURI(uri), uri).withMethod("GET");
  }

  /**
   * Create a new POST request builder. The client name will be selected based
   * on a prefix of the host name.
   *
   * @param uri
   *     URI to use for the request.
   * @return
   *     Builder for creating and executing a request.
   */
  default HttpRequestBuilder post(URI uri) {
    return newRequest(HttpUtils.clientNameForURI(uri), uri).withMethod("POST");
  }

  /**
   * Create a new PUT request builder. The client name will be selected based
   * on a prefix of the host name.
   *
   * @param uri
   *     URI to use for the request.
   * @return
   *     Builder for creating and executing a request.
   */
  default HttpRequestBuilder put(URI uri) {
    return newRequest(HttpUtils.clientNameForURI(uri), uri).withMethod("PUT");
  }

  /**
   * Create a new DELETE request builder. The client name will be selected based
   * on a prefix of the host name.
   *
   * @param uri
   *     URI to use for the request.
   * @return
   *     Builder for creating and executing a request.
   */
  default HttpRequestBuilder delete(URI uri) {
    return newRequest(HttpUtils.clientNameForURI(uri), uri).withMethod("DELETE");
  }
}
