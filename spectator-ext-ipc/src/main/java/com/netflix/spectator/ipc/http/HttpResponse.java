/*
 * Copyright 2014-2018 Netflix, Inc.
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Response for an HTTP request made via {@link HttpRequestBuilder}.
 */
public class HttpResponse {

  private final int status;
  private final Map<String, List<String>> headers;
  private final byte[] data;

  /** Create a new response instance with an empty entity. */
  public HttpResponse(int status, Map<String, List<String>> headers) {
    this(status, headers, HttpUtils.EMPTY);
  }

  /** Create a new response instance. */
  public HttpResponse(int status, Map<String, List<String>> headers, byte[] data) {
    this.status = status;
    Map<String, List<String>> hs = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    hs.putAll(headers);
    this.headers = Collections.unmodifiableMap(hs);
    this.data = data;
  }

  /** Return the status code of the response. */
  public int status() {
    return status;
  }

  /**
   * Return the headers for the response as an unmodifiable map with case-insensitive keys.
   */
  public Map<String, List<String>> headers() {
    return headers;
  }

  /** Return the value for the first occurrence of a given header or null if not found. */
  public String header(String k) {
    List<String> vs = headers.get(k);
    return (vs == null || vs.isEmpty()) ? null : vs.get(0);
  }

  /**
   * Return the value for a date header. The return value will be null if the header does
   * not exist or if it cannot be parsed correctly as a date.
   */
  public Instant dateHeader(String k) {
    String d = header(k);
    return (d == null) ? null : parseDate(d);
  }

  private Instant parseDate(String d) {
    try {
      return LocalDateTime.parse(d, DateTimeFormatter.RFC_1123_DATE_TIME)
          .atZone(ZoneOffset.UTC)
          .toInstant();
    } catch (Exception e) {
      return null;
    }
  }

  /** Return the entity for the response. */
  public byte[] entity() {
    return data;
  }

  /** Return the entity as a UTF-8 string. */
  public String entityAsString() {
    return new String(data, StandardCharsets.UTF_8);
  }

  /** Return a copy of the response with the entity decompressed. */
  public HttpResponse decompress() throws IOException {
    String enc = header("Content-Encoding");
    return (enc != null && enc.contains("gzip")) ? unzip() : this;
  }

  private HttpResponse unzip() throws IOException {
    Map<String, List<String>> newHeaders = headers.entrySet().stream()
        .filter(e -> !e.getKey().equalsIgnoreCase("Content-Encoding"))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    if (data.length == 0) {
      return new HttpResponse(status, newHeaders);
    } else {
      return new HttpResponse(status, newHeaders, HttpUtils.gunzip(data));
    }
  }

  @Override public String toString() {
    StringBuilder builder = new StringBuilder(50);
    builder.append("HTTP/1.1 ").append(status).append('\n');
    for (Map.Entry<String, List<String>> h : headers.entrySet()) {
      for (String v : h.getValue()) {
        builder.append(h.getKey()).append(": ").append(v).append('\n');
      }
    }
    builder.append("\n... ")
        .append(data.length)
        .append(" bytes ...\n");
    return builder.toString();
  }
}
