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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

public class HttpRequestBuilderTest {

  private static final HttpResponse OK           = new HttpResponse(200, Collections.emptyMap());
  private static final HttpResponse REDIRECT     = new HttpResponse(302, Collections.emptyMap());
  private static final HttpResponse BAD_REQUEST  = new HttpResponse(400, Collections.emptyMap());
  private static final HttpResponse THROTTLED    = new HttpResponse(429, Collections.emptyMap());
  private static final HttpResponse SERVER_ERROR = new HttpResponse(500, Collections.emptyMap());
  private static final HttpResponse UNAVAILABLE  = new HttpResponse(503, Collections.emptyMap());

  @Test
  public void ok() throws IOException {
    HttpResponse res = new TestRequestBuilder(() -> OK).send();
    Assertions.assertEquals(200, res.status());
  }

  @Test
  public void retry0() throws Exception {
    Assertions.assertThrows(IOException.class,
        () -> new TestRequestBuilder(() -> { throw new IOException("failed"); }).send());
  }

  @Test
  public void retry2() {
    AtomicInteger attempts = new AtomicInteger();
    boolean failed = false;
    try {
      HttpResponseSupplier supplier = () -> {
        attempts.incrementAndGet();
        throw new IOException("failed");
      };
      new TestRequestBuilder(supplier).withRetries(2).send();
    } catch (IOException e) {
      failed = true;
    }
    Assertions.assertEquals(3, attempts.get());
    Assertions.assertTrue(failed);
  }

  private void retryException(String method, IOException ex, int expectedAttempts) {
    AtomicInteger attempts = new AtomicInteger();
    boolean failed = false;
    try {
      HttpResponseSupplier supplier = () -> {
        attempts.incrementAndGet();
        throw ex;
      };
      new TestRequestBuilder(supplier).withMethod(method).withRetries(2).send();
    } catch (IOException e) {
      failed = true;
    }
    Assertions.assertEquals(expectedAttempts, attempts.get());
    Assertions.assertTrue(failed);
  }

  @Test
  public void retryConnectExceptionPost() {
    retryException("POST", new ConnectException("failed"), 3);
  }

  @Test
  public void retryConnectTimeoutPost() {
    retryException("POST", new SocketTimeoutException("connect timed out"), 3);
  }

  @Test
  public void retryReadTimeoutGet() {
    retryException("GET", new SocketTimeoutException("read timed out"), 3);
  }

  @Test
  public void retryReadTimeoutPost() {
    retryException("POST", new SocketTimeoutException("read timed out"), 1);
  }

  private void retryStatus(
      String method, HttpResponse expectedRes, int expectedAttempts) throws IOException {
    AtomicInteger attempts = new AtomicInteger();
    HttpResponseSupplier supplier = () -> {
      attempts.incrementAndGet();
      return expectedRes;
    };
    HttpResponse res = new TestRequestBuilder(supplier)
        .withMethod(method)
        .withInitialRetryDelay(0L)
        .withRetries(2)
        .send();
    Assertions.assertEquals(expectedAttempts, attempts.get());
    Assertions.assertEquals(expectedRes.status(), res.status());
  }

  @Test
  public void retry2xx() throws IOException {
    retryStatus("GET", OK, 1);
  }

  @Test
  public void retry3xx() throws IOException {
    retryStatus("GET", REDIRECT, 1);
  }

  @Test
  public void retry4xx() throws IOException {
    retryStatus("GET", BAD_REQUEST, 1);
  }

  @Test
  public void retry5xx() throws IOException {
    retryStatus("GET", SERVER_ERROR, 3);
  }

  @Test
  public void retry429() throws IOException {
    retryStatus("GET", THROTTLED, 3);
  }

  @Test
  public void retry503() throws IOException {
    retryStatus("GET", UNAVAILABLE, 3);
  }

  @Test
  public void retryPost2xx() throws IOException {
    retryStatus("POST", OK, 1);
  }

  @Test
  public void retryPost3xx() throws IOException {
    retryStatus("POST", REDIRECT, 1);
  }

  @Test
  public void retryPost4xx() throws IOException {
    retryStatus("POST", BAD_REQUEST, 1);
  }

  @Test
  public void retryPost5xx() throws IOException {
    retryStatus("POST", SERVER_ERROR, 1);
  }

  @Test
  public void retryPost429() throws IOException {
    retryStatus("POST", THROTTLED, 3);
  }

  @Test
  public void retryPost503() throws IOException {
    retryStatus("POST", UNAVAILABLE, 3);
  }

  @Test
  public void hostnameVerificationWithHTTP() throws IOException {
    Assertions.assertThrows(IllegalStateException.class,
        () -> new TestRequestBuilder(() -> OK).allowAllHosts());
  }

  @Test
  public void hostnameVerificationWithHTTPS() throws IOException {
    HttpResponse res = new TestRequestBuilder(() -> OK, URI.create("https://foo.com/path"))
        .allowAllHosts()
        .send();
    Assertions.assertEquals(200, res.status());
  }

  private static class TestRequestBuilder extends HttpRequestBuilder {
    private HttpResponseSupplier supplier;

    TestRequestBuilder(HttpResponseSupplier supplier) {
      this(supplier, URI.create("/path"));
    }

    TestRequestBuilder(HttpResponseSupplier supplier, URI uri) {
      super(HttpClient.DEFAULT_LOGGER, uri);
      this.supplier = supplier;
    }

    @Override protected HttpResponse sendImpl() throws IOException {
      return supplier.get();
    }
  }

  private interface HttpResponseSupplier {
    HttpResponse get() throws IOException;
  }
}
