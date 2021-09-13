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

import com.netflix.spectator.impl.Preconditions;
import com.netflix.spectator.ipc.IpcLogEntry;
import com.netflix.spectator.ipc.IpcLogger;
import com.netflix.spectator.ipc.NetflixHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.zip.Deflater;

/**
 * Helper for executing simple HTTP client requests using {@link HttpURLConnection}
 * and logging via {@link com.netflix.spectator.ipc.IpcLogger}. This is mostly used for simple
 * use-cases where it is undesirable to have additional dependencies on a more robust HTTP
 * library.
 */
public class HttpRequestBuilder {
  private static final Logger LOGGER = LoggerFactory.getLogger(HttpRequestBuilder.class);

  private static final Map<String, String> NETFLIX_HEADERS =
      NetflixHeaders.extractFromEnvironment();

  // Should not be used directly, use the method of the same name that will create the
  // executor if needed on the first access.
  private static volatile ExecutorService defaultExecutor;

  private static ThreadFactory newThreadFactory() {
    return new ThreadFactory() {
      private final AtomicInteger next = new AtomicInteger();

      @Override public Thread newThread(Runnable r) {
        final String name = "spectator-ipc-" + next.getAndIncrement();
        final Thread t = new Thread(r, name);
        t.setDaemon(true);
        return t;
      }
    };
  }

  private static ExecutorService defaultExecutor() {
    ExecutorService executor = defaultExecutor;
    if (executor != null) {
      return executor;
    }
    synchronized (LOGGER) {
      defaultExecutor = Executors.newFixedThreadPool(
          Runtime.getRuntime().availableProcessors(), newThreadFactory());
      return defaultExecutor;
    }
  }

  private final URI uri;
  private final IpcLogEntry entry;
  private String method = "GET";
  private Map<String, String> reqHeaders = new LinkedHashMap<>();
  private byte[] entity = HttpUtils.EMPTY;

  private int connectTimeout = 1000;
  private int readTimeout = 30000;

  private RetryPolicy retryPolicy = RetryPolicy.SAFE;
  private long initialRetryDelay = 1000L;
  private int numAttempts = 3;

  private HostnameVerifier hostVerifier = null;
  private SSLSocketFactory sslFactory = null;

  /** Create a new instance for the specified URI. */
  public HttpRequestBuilder(IpcLogger logger, URI uri) {
    this.uri = uri;
    this.entry = logger.createClientEntry()
        .withOwner("spectator")
        .withUri(uri)
        .withHttpMethod(method);
    this.reqHeaders.putAll(NETFLIX_HEADERS);
  }

  /** Set the request method (GET, PUT, POST, DELETE). */
  public HttpRequestBuilder withMethod(String m) {
    this.method = m;
    entry.withHttpMethod(method);
    return this;
  }

  /**
   * Add a header to the request. Note the content type will be set automatically
   * when providing the content payload and should not be set here. If the value
   * is null, then the header will get ignored.
   */
  public HttpRequestBuilder addHeader(String name, String value) {
    if (value != null) {
      reqHeaders.put(name, value);
    }
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

  /**
   * Compress the request body using the default compression level.
   * The content must have already been set on the builder.
   */
  public HttpRequestBuilder compress() throws IOException {
    return compress(Deflater.DEFAULT_COMPRESSION);
  }

  /**
   * Compress the request body using the specified compression level.
   * The content must have already been set on the builder.
   */
  public HttpRequestBuilder compress(int level) throws IOException {
    addHeader("Content-Encoding", "gzip");
    entity = HttpUtils.gzip(entity, level);
    return this;
  }

  /** How many times to retry if the intial attempt fails? */
  public HttpRequestBuilder withRetries(int n) {
    Preconditions.checkArg(n >= 0, "number of retries must be >= 0");
    this.numAttempts = n + 1;
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

  /**
   * Policy to determine whether a given failure can be retried. By default
   * {@link RetryPolicy#SAFE} is used.
   */
  public HttpRequestBuilder retryPolicy(RetryPolicy policy) {
    this.retryPolicy = policy;
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

  /**
   * Provides access to the {@link IpcLogEntry} object to make adjustments if needed. For
   * most common usage the default should be fine.
   */
  public HttpRequestBuilder customizeLogging(Consumer<IpcLogEntry> f) {
    f.accept(entry);
    return this;
  }

  /** Send the request and log/update metrics for the results. */
  @SuppressWarnings("PMD.ExceptionAsFlowControl")
  public HttpResponse send() throws IOException {
    HttpResponse response = null;
    for (int attempt = 1; attempt <= numAttempts; ++attempt) {
      entry.withAttempt(attempt).withAttemptFinal(attempt == numAttempts);
      try {
        response = sendImpl();
        int s = response.status();
        boolean shouldRetry = retryPolicy.shouldRetry(method, response);
        if (shouldRetry && (s == 429 || s == 503)) {
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
        } else if (!shouldRetry) {
          return response;
        }
      } catch (IOException e) {
        if (attempt == numAttempts || !retryPolicy.shouldRetry(method, e)) {
          throw e;
        } else {
          LOGGER.info("attempt {} of {} failed: {} {}", attempt, numAttempts, method, uri);
        }
      }
    }

    if (response == null) {
      // Should not get here
      throw new IOException("request failed " + method + " " + uri);
    }
    return response;
  }

  /**
   * Send the request asynchronously and log/update metrics for the results. The request
   * will be sent on a background thread pool and will update the future when complete. In
   * the future it can be changed to use the new HttpClient in Java 11+.
   */
  public CompletableFuture<HttpResponse> sendAsync() {
    Supplier<HttpResponse> responseSupplier = () -> {
      try {
        return send();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    };
    return CompletableFuture.supplyAsync(responseSupplier, defaultExecutor());
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
      entry.addRequestHeader(h.getKey(), h.getValue());
      con.setRequestProperty(h.getKey(), h.getValue());
    }
    entry.withRequestContentLength(entity.length);
    configureHTTPS(con);

    try {
      con.setDoInput(true);

      // HttpURLConnection will change method to POST if there is a body associated
      // with a GET request. Only try to write entity if it is not empty.
      entry.markStart();
      if (entity.length > 0) {
        con.setDoOutput(true);
        try (OutputStream out = con.getOutputStream()) {
          out.write(entity);
        }
      }

      int status = con.getResponseCode();
      entry.markEnd().withHttpStatus(status);

      // A null key is used to return the status line, remove it before sending to
      // the log entry or creating the response object
      Map<String, List<String>> headers = new LinkedHashMap<>(con.getHeaderFields());
      headers.remove(null);
      for (Map.Entry<String, List<String>> h : headers.entrySet()) {
        for (String v : h.getValue()) {
          entry.addResponseHeader(h.getKey(), v);
        }
      }

      try (InputStream in = (status >= 400) ? con.getErrorStream() : con.getInputStream()) {
        byte[] data = readAll(in);
        entry.withResponseContentLength(data.length);
        return new HttpResponse(status, headers, data);
      }
    } catch (IOException e) {
      entry.markEnd().withException(e);
      throw e;
    } finally {
      entry.log();
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
