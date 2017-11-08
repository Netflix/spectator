/*
 * Copyright 2014-2016 Netflix, Inc.
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

import com.netflix.spectator.impl.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper for executing simple HTTP client requests using {@link HttpURLConnection}
 * and logging via {@link HttpLogEntry}. This is mostly used for simple use-cases
 * where it is undesirable to have additional dependencies on a more robust HTTP
 * library.
 */
public class HttpRequestBuilder {
  private static final Logger LOGGER = LoggerFactory.getLogger(HttpRequestBuilder.class);

  private final URI uri;
  private final HttpLogEntry entry;
  private String method = "GET";
  private Map<String, String> reqHeaders = new LinkedHashMap<>();
  private byte[] entity = HttpUtils.EMPTY;

  private int connectTimeout = 1000;
  private int readTimeout = 30000;

  private long initialRetryDelay = 1000L;
  private int numAttempts = 1;

  private HostnameVerifier hostVerifier = null;
  private SSLSocketFactory sslFactory = null;

  /** Create a new instance for the specified URI. */
  public HttpRequestBuilder(String clientName, URI uri) {
    this.uri = uri;
    this.entry = new HttpLogEntry()
        .withRequestUri(uri)
        .withClientName(clientName);
  }

  /** Set the request method (GET, PUT, POST, DELETE). */
  public HttpRequestBuilder withMethod(String m) {
    this.method = m;
    entry.withMethod(method);
    return this;
  }

  /**
   * Add a header to the request. Note the content type will be set automatically
   * when providing the content payload and should not be set here.
   */
  public HttpRequestBuilder addHeader(String name, String value) {
    reqHeaders.put(name, value);
    entry.withRequestHeader(name, value);
    return this;
  }

  /** Add user-agent header. */
  public HttpRequestBuilder userAgent(String agent) {
    return addHeader("User-Agent", agent);
  }

  /** Add header to accept {@code application/json} data. */
  public HttpRequestBuilder acceptJson() {
    return addHeader("Accept", "application/json");
  }

  /** Add accept header. */
  public HttpRequestBuilder accept(String type) {
    return addHeader("Accept", type);
  }

  /** Add header to accept-encoding of gzip. */
  public HttpRequestBuilder acceptGzip() {
    return acceptEncoding("gzip");
  }

  /** Add accept-encoding header. */
  public HttpRequestBuilder acceptEncoding(String enc) {
    return addHeader("Accept-Encoding", enc);
  }

  /** Set the connection timeout for the request in milliseconds. */
  public HttpRequestBuilder withConnectTimeout(int timeout) {
    this.connectTimeout = timeout;
    return this;
  }

  /** Set the read timeout for the request milliseconds. */
  public HttpRequestBuilder withReadTimeout(int timeout) {
    this.readTimeout = timeout;
    return this;
  }

  /** Set the request body as JSON. */
  public HttpRequestBuilder withJsonContent(String content) {
    return withContent("application/json", content);
  }

  /** Set the request body. */
  public HttpRequestBuilder withContent(String type, String content) {
    return withContent(type, content.getBytes(StandardCharsets.UTF_8));
  }

  /** Set the request body. */
  public HttpRequestBuilder withContent(String type, byte[] content) {
    addHeader("Content-Type", type);
    entity = content;
    return this;
  }

  /** Compress the request body. The content must have already been set on the builder. */
  public HttpRequestBuilder compress() throws IOException {
    addHeader("Content-Encoding", "gzip");
    entity = HttpUtils.gzip(entity);
    return this;
  }

  /** How many times to retry if the intial attempt fails? */
  public HttpRequestBuilder withRetries(int n) {
    Preconditions.checkArg(n >= 0, "number of retries must be >= 0");
    this.numAttempts = n + 1;
    entry.withMaxAttempts(numAttempts);
    return this;
  }

  /**
   * How long to delay before retrying if the request is throttled. This will get doubled
   * for each attempt that is throttled. Unit is milliseconds.
   */
  public HttpRequestBuilder withInitialRetryDelay(long delay) {
    Preconditions.checkArg(delay >= 0L, "initial retry delay must be >= 0");
    this.initialRetryDelay = delay;
    return this;
  }

  private void requireHttps(String msg) {
    Preconditions.checkState("https".equals(uri.getScheme()), msg);
  }

  /** Sets the policy used to verify hostnames when using HTTPS. */
  public HttpRequestBuilder withHostnameVerifier(HostnameVerifier verifier) {
    requireHttps("hostname verification cannot be used with http, switch to https");
    this.hostVerifier = verifier;
    return this;
  }

  /**
   * Specify that all hosts are allowed. Using this option effectively disables hostname
   * verification. Use with caution.
   */
  public HttpRequestBuilder allowAllHosts() {
    return withHostnameVerifier((host, session) -> true);
  }

  /** Sets the socket factory to use with HTTPS. */
  public HttpRequestBuilder withSSLSocketFactory(SSLSocketFactory factory) {
    requireHttps("ssl cannot be used with http, use https");
    this.sslFactory = factory;
    return this;
  }

  /** Send the request and log/update metrics for the results. */
  @SuppressWarnings("PMD.ExceptionAsFlowControl")
  public HttpResponse send() throws IOException {
    HttpResponse response = null;
    for (int attempt = 1; attempt <= numAttempts; ++attempt) {
      entry.withAttempt(attempt);
      try {
        response = sendImpl();
        int s = response.status();
        if (s == 429 || s == 503) {
          // Request is getting throttled, exponentially back off
          // - 429 client sending too many requests
          // - 503 server unavailable
          try {
            long delay = initialRetryDelay << (attempt - 1);
            LOGGER.debug("request throttled, delaying for {}ms: {} {}", delay, method, uri);
            Thread.sleep(delay);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("request failed " + method + " " + uri, e);
          }
        } else if (s < 500) {
          // 4xx errors other than 429 are not considered retriable, so for anything
          // less than 500 just return the response to the user
          return response;
        }
      } catch (IOException e) {
        // All exceptions are considered retriable. Some like UnknownHostException are
        // debatable, but we have seen them in some cases if there is a high latency for
        // DNS lookups. So for now assume all exceptions are transient issues.
        if (attempt == numAttempts) {
          throw e;
        } else {
          LOGGER.warn("attempt {} of {} failed: {} {}", attempt, numAttempts, method, uri);
        }
      }
    }

    if (response == null) {
      // Should not get here
      throw new IOException("request failed " + method + " " + uri);
    }
    return response;
  }

  private void configureHTTPS(HttpURLConnection http) {
    if (http instanceof HttpsURLConnection) {
      HttpsURLConnection https = (HttpsURLConnection) http;
      if (hostVerifier != null) {
        https.setHostnameVerifier(hostVerifier);
      }
      if (sslFactory != null) {
        https.setSSLSocketFactory(sslFactory);
      }
    }
  }

  /** Send the request and log/update metrics for the results. */
  protected HttpResponse sendImpl() throws IOException {
    HttpURLConnection con = (HttpURLConnection) uri.toURL().openConnection();
    con.setConnectTimeout(connectTimeout);
    con.setReadTimeout(readTimeout);
    con.setRequestMethod(method);
    for (Map.Entry<String, String> h : reqHeaders.entrySet()) {
      con.setRequestProperty(h.getKey(), h.getValue());
    }
    configureHTTPS(con);

    boolean canRetry = true;
    try {
      con.setDoInput(true);

      // HttpURLConnection will change method to POST if there is a body associated
      // with a GET request. Only try to write entity if it is not empty.
      entry.withRequestContentLength(entity.length).mark("start");
      if (entity.length > 0) {
        con.setDoOutput(true);
        try (OutputStream out = con.getOutputStream()) {
          out.write(entity);
        }
      }

      int status = con.getResponseCode();
      canRetry = (status >= 500 || status == 429);
      entry.mark("complete").withStatusCode(status);

      // A null key is used to return the status line, remove it before sending to
      // the log entry or creating the response object
      Map<String, List<String>> headers = new LinkedHashMap<>(con.getHeaderFields());
      headers.remove(null);
      for (Map.Entry<String, List<String>> h : headers.entrySet()) {
        for (String v : h.getValue()) {
          entry.withResponseHeader(h.getKey(), v);
        }
      }

      try (InputStream in = (status >= 400) ? con.getErrorStream() : con.getInputStream()) {
        byte[] data = readAll(in);
        entry.withResponseContentLength(data.length);
        return new HttpResponse(status, headers, data);
      }
    } catch (IOException e) {
      entry.mark("complete").withException(e);
      throw e;
    } finally {
      entry.withCanRetry(canRetry);
      HttpLogEntry.logClientRequest(entry);
    }
  }

  @SuppressWarnings("PMD.AssignmentInOperand")
  private byte[] readAll(InputStream in) throws IOException {
    if (in == null) {
      // For error status codes with a content-length of 0 we see this case
      return new byte[0];
    } else {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      byte[] buffer = new byte[4096];
      int length;
      while ((length = in.read(buffer)) > 0) {
        baos.write(buffer, 0, length);
      }
      return baos.toByteArray();
    }
  }
}
