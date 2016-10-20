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
package com.netflix.spectator.atlas;

import com.netflix.spectator.sandbox.HttpLogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Helper for sending an HTTP request using {@link HttpURLConnection}.
 */
class HttpRequest {

  private static final Logger LOGGER = LoggerFactory.getLogger(HttpRequest.class);

  private final URI uri;
  private final HttpLogEntry entry;
  private final HttpURLConnection con;
  private byte[] entity;

  /** Create a new instance for the specified URI. */
  HttpRequest(URI uri) throws Exception {
    this.uri = uri;
    this.entry = new HttpLogEntry()
        .withRequestUri(uri)
        .withClientName("spectator-reg-atlas");
    this.con = (HttpURLConnection) uri.toURL().openConnection();
  }

  /** Set the request method (GET, PUT, POST, DELETE). */
  HttpRequest withMethod(String method) throws Exception {
    entry.withMethod(method);
    con.setRequestMethod(method);
    return this;
  }

  /**
   * Add a header to the request. Note the content type will be set automatically
   * when providing the content payload and should not be set here.
   */
  HttpRequest addHeader(String name, String value) {
    entry.withRequestHeader(name, value);
    con.addRequestProperty(name, value);
    return this;
  }

  /** Set the connection timeout for the request. */
  HttpRequest withConnectTimeout(int timeout) {
    con.setConnectTimeout(timeout);
    return this;
  }

  /** Set the read timeout for the request. */
  HttpRequest withReadTimeout(int timeout) {
    con.setReadTimeout(timeout);
    return this;
  }

  /** Set the request body as JSON. */
  HttpRequest withJsonContent(String content) throws IOException {
    addHeader("Content-Type", "application/json");
    entity = content.getBytes(StandardCharsets.UTF_8);
    return this;
  }

  /** Set the request body as Smile encoded. */
  HttpRequest withSmileContent(byte[] content) throws IOException {
    addHeader("Content-Type", "application/x-jackson-smile");
    entity = content;
    return this;
  }

  /** Send the request and log/update metrics for the results. */
  void sendAndLog() throws IOException {
    try {
      con.setDoInput(true);
      con.setDoOutput(true);
      entry.withRequestContentLength(entity.length).mark("start");
      try (OutputStream out = con.getOutputStream()) {
        out.write(entity);
      }

      int status = con.getResponseCode();
      entry.mark("complete").withStatusCode(status);
      try (InputStream in = (status >= 400) ? con.getErrorStream() : con.getInputStream()) {
        byte[] data = readAll(in);
        entry.withResponseContentLength(data.length);
        if (LOGGER.isDebugEnabled()) {
          String payload = new String(data, StandardCharsets.UTF_8);
          LOGGER.debug("uri {}, status code {}, payload: {}", uri, status, payload);
        }
      }
    } catch (IOException e) {
      entry.mark("complete").withException(e);
      LOGGER.warn("request to {} failed", uri, e);
    } finally {
      HttpLogEntry.logClientRequest(entry);
    }
  }

  @SuppressWarnings("PMD.AssignmentInOperand")
  private byte[] readAll(InputStream in) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    byte[] buffer = new byte[4096];
    int length;
    while ((length = in.read(buffer)) > 0) {
      baos.write(buffer, 0, length);
    }
    return baos.toByteArray();
  }
}
