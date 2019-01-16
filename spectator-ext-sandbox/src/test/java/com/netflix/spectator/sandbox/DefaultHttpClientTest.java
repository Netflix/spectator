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

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.concurrent.Executors;

public class DefaultHttpClientTest {

  private static HttpServer server;
  private static int port;

  private static void ignore(InputStream input) throws IOException {
    try (InputStream in = input) {
      byte[] buf = new byte[1024];
      while (in.read(buf) > 0);
    }
  }

  private static int getInt(Headers headers, String k, int dflt) {
    String v = headers.getFirst(k);
    return (v == null) ? dflt : Integer.parseInt(v);
  }

  @BeforeAll
  public static void startServer() throws Exception {
    server = HttpServer.create(new InetSocketAddress(0), 100);
    server.setExecutor(Executors.newFixedThreadPool(10, r -> new Thread(r, "HttpServer")));
    port = server.getAddress().getPort();

    server.createContext("/", exchange -> {
      Headers headers = exchange.getRequestHeaders();
      int status = getInt(headers, "X-Status", 200);
      int length = getInt(headers, "X-Length", -1);
      ignore(exchange.getRequestBody());
      exchange.sendResponseHeaders(status, length);
      exchange.close();
    });

    server.createContext("/echo", exchange -> {
      Headers headers = exchange.getRequestHeaders();
      int status = getInt(headers, "X-Status", 200);
      boolean compressResponse = headers
          .getOrDefault("Content-Encoding", Collections.emptyList())
          .contains("gzip");
      if (compressResponse) {
        exchange.getResponseHeaders().add("Content-Encoding", "gzip");
      }
      exchange.sendResponseHeaders(status, 0L);
      try (InputStream in = exchange.getRequestBody();
           OutputStream out = exchange.getResponseBody()) {
        byte[] buf = new byte[1024];
        int length;
        while ((length = in.read(buf)) > 0) {
          out.write(buf, 0, length);
        }
      }
      exchange.close();
    });

    server.start();
  }

  @AfterAll
  public static void stopServer() {
    server.stop(0);
  }

  private URI uri(String path) {
    return URI.create("http://127.0.0.1:" + port + path);
  }

  @Test
  public void ok() throws IOException {
    HttpResponse res = HttpClient.DEFAULT
        .get(uri("/ok"))
        .addHeader("X-Status", "200")
        .addHeader("X-Length", "-1")
        .send();
    Assertions.assertEquals(200, res.status());
  }

  @Test
  public void emptyChunked() throws IOException {
    HttpResponse res = HttpClient.DEFAULT
        .get(uri("/ok"))
        .addHeader("X-Status", "200")
        .addHeader("X-Length", "0")
        .send();
    Assertions.assertEquals(200, res.status());
  }

  @Test
  public void unavailableEmpty() throws IOException {
    HttpResponse res = HttpClient.DEFAULT
        .get(uri("/ok"))
        .addHeader("X-Status", "503")
        .addHeader("X-Length", "-1")
        .send();
    Assertions.assertEquals(503, res.status());
  }

  @Test
  public void unavailableEmptyChunked() throws IOException {
    HttpResponse res = HttpClient.DEFAULT
        .get(uri("/ok"))
        .addHeader("X-Status", "503")
        .addHeader("X-Length", "0")
        .send();
    Assertions.assertEquals(503, res.status());
  }

  @Test
  public void okWithBody() throws IOException {
    HttpResponse res = HttpClient.DEFAULT
        .post(uri("/echo"))
        .addHeader("X-Status", "200")
        .withContent("text/plain", "foo")
        .send();
    Assertions.assertEquals(200, res.status());
    Assertions.assertEquals("foo", res.entityAsString());
  }

  @Test
  public void okWithCompressedBody() throws IOException {
    HttpResponse res = HttpClient.DEFAULT
        .post(uri("/echo"))
        .acceptGzip()
        .addHeader("X-Status", "200")
        .withContent("text/plain", "foo")
        .compress()
        .send()
        .decompress();
    Assertions.assertEquals(200, res.status());
    Assertions.assertEquals("foo", res.entityAsString());
  }
}
