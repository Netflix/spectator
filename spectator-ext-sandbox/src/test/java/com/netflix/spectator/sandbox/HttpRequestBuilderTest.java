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

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(JUnit4.class)
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
    Assert.assertEquals(200, res.status());
  }

  @Test(expected = IOException.class)
  public void retry0() throws Exception {
    new TestRequestBuilder(() -> { throw new IOException("failed"); }).send();
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
    Assert.assertEquals(3, attempts.get());
    Assert.assertTrue(failed);
  }

  private void retryStatus(HttpResponse expectedRes, int expectedAttempts) throws IOException {
    AtomicInteger attempts = new AtomicInteger();
    HttpResponseSupplier supplier = () -> {
      attempts.incrementAndGet();
      return expectedRes;
    };
    HttpResponse res = new TestRequestBuilder(supplier)
        .withInitialRetryDelay(0L)
        .withRetries(2)
        .send();
    Assert.assertEquals(expectedAttempts, attempts.get());
    Assert.assertEquals(expectedRes.status(), res.status());
  }

  @Test
  public void retry2xx() throws IOException {
    retryStatus(OK, 1);
  }

  @Test
  public void retry3xx() throws IOException {
    retryStatus(REDIRECT, 1);
  }

  @Test
  public void retry4xx() throws IOException {
    retryStatus(BAD_REQUEST, 1);
  }

  @Test
  public void retry5xx() throws IOException {
    retryStatus(SERVER_ERROR, 3);
  }

  @Test
  public void retry429() throws IOException {
    retryStatus(THROTTLED, 3);
  }

  @Test
  public void retry503() throws IOException {
    retryStatus(UNAVAILABLE, 3);
  }

  @Test(expected = IllegalStateException.class)
  public void hostnameVerificationWithHTTP() throws IOException {
    new TestRequestBuilder(() -> OK).allowAllHosts();
  }

  @Test
  public void hostnameVerificationWithHTTPS() throws IOException {
    HttpResponse res = new TestRequestBuilder(() -> OK, URI.create("https://foo.com/path"))
        .allowAllHosts()
        .send();
    Assert.assertEquals(200, res.status());
  }

  private static class TestRequestBuilder extends HttpRequestBuilder {
    private HttpResponseSupplier supplier;

    TestRequestBuilder(HttpResponseSupplier supplier) {
      this(supplier, URI.create("/path"));
    }

    TestRequestBuilder(HttpResponseSupplier supplier, URI uri) {
      super("test", uri);
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
