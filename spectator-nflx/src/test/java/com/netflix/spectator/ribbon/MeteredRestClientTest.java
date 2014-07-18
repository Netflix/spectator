/**
 * Copyright 2014 Netflix, Inc.
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
package com.netflix.spectator.ribbon;

import com.netflix.client.http.HttpRequest;
import com.netflix.client.http.HttpResponse;
import com.netflix.niws.client.http.RestClient;
import com.netflix.spectator.api.ExtendedRegistry;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Spectator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;

@RunWith(JUnit4.class)
public class MeteredRestClientTest {

  private static final String client = "MeteredRestClientTest";

  private static HttpServer server;
  private static int port;

  @BeforeClass
  public static void startServer() throws Exception {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    port = server.getAddress().getPort();

    server.createContext("/ok", new HttpHandler() {
      @Override
      public void handle(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(200, 0L);
        exchange.close();
      }
    });

    server.start();

    System.setProperty(client + ".niws.client.NIWSServerListClassName",
      "com.netflix.loadbalancer.ConfigurationBasedServerList");
    System.setProperty(client + ".niws.client.listOfServers",
      "localhost:" + port);
  }

  @AfterClass
  public static void stopServer() {
    server.stop(0);
  }

  private int get(String loc) {
    URI uri = URI.create(loc);
    HttpRequest req = new HttpRequest.Builder()
      .verb(HttpRequest.Verb.GET)
      .uri(uri)
      .build();
    RestClient c = RestClientFactory.getClient(client);
    try (HttpResponse res = uri.isAbsolute() ? c.execute(req) : c.executeWithLoadBalancer(req)) {
      return res.getStatus();
    } catch (Exception e) {
      e.printStackTrace();
      return -1;
    }
  }

  private long reqCount(int status) {
    ExtendedRegistry r = Spectator.registry();
    Id requests = r.createId("ribbon.http.requests", "client", client, "status", "" + status);
    return r.counter(requests).count();
  }

  private long niwsReqCount(int status) {
    ExtendedRegistry r = Spectator.registry();
    Id requests = r.createId("ribbon.http.niwsRequests", "client", client, "status", "" + status);
    return r.counter(requests).count();
  }

  @Test
  public void executeOk() {
    long before = reqCount(200);
    Assert.assertEquals(get("http://localhost:" + port + "/ok"), 200);
    Assert.assertEquals(reqCount(200), before + 1);
  }

  @Test
  public void executeNotFound() {
    long before200 = reqCount(200);
    long before404 = reqCount(404);
    Assert.assertEquals(get("http://localhost:" + port + "/not-found"), 404);
    Assert.assertEquals(reqCount(200), before200);
    Assert.assertEquals(reqCount(404), before404 + 1);
  }

  @Test
  public void executeWithLbOk() {
    long before = reqCount(200);
    long nbefore = niwsReqCount(200);
    Assert.assertEquals(get("/ok"), 200);
    Assert.assertEquals(reqCount(200), before + 1);
    Assert.assertEquals(niwsReqCount(200), nbefore + 1);
  }

  @Test
  public void executeWithLbNotFound() {
    long before200 = niwsReqCount(200);
    long before404 = niwsReqCount(404);
    Assert.assertEquals(get("/not-found"), 404);
    Assert.assertEquals(niwsReqCount(200), before200);
    Assert.assertEquals(niwsReqCount(404), before404 + 1);
  }
}

