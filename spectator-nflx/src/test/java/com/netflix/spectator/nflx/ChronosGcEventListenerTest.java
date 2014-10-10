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
package com.netflix.spectator.nflx;

import com.netflix.spectator.api.ExtendedRegistry;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Spectator;
import com.netflix.spectator.gc.GcEvent;
import com.sun.management.GarbageCollectionNotificationInfo;
import com.sun.management.GarbageCollectorMXBean;
import com.sun.management.GcInfo;
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
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(JUnit4.class)
public class ChronosGcEventListenerTest {

  private static final String client = "chronos_gc";
  private static final int retries = 1; // TODO: it seems to be ignoring retry property

  private static HttpServer server;
  private static int port;

  private static AtomicInteger statusCode = new AtomicInteger(200);

  @BeforeClass
  public static void startServer() throws Exception {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    port = server.getAddress().getPort();

    server.createContext("/api/v2/event", new HttpHandler() {
      @Override
      public void handle(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(statusCode.get(), 0L);
        exchange.close();
      }
    });

    server.start();

    System.setProperty(client + ".niws.client.MaxAutoRetries", "0");
    System.setProperty(client + ".niws.client.MaxAutoRetriesNextServer", "" + retries);
    System.setProperty(client + ".niws.client.OkToRetryOnAllOperations", "true");
    System.setProperty(client + ".niws.client.NIWSServerListClassName",
        "com.netflix.loadbalancer.ConfigurationBasedServerList");
    System.setProperty(client + ".niws.client.listOfServers",
        "localhost:" + port);
  }

  @AfterClass
  public static void stopServer() {
    server.stop(0);
  }

  private long reqCount(int status) {
    ExtendedRegistry r = Spectator.registry();
    Id requests = r.createId("spectator.gc.chronosPost", "status", "" + status);
    return r.timer(requests).count();
  }

  private GcEvent newGcEvent() {
    GcInfo gcInfo = null;
    for (java.lang.management.GarbageCollectorMXBean mbean : ManagementFactory.getGarbageCollectorMXBeans()) {
      GarbageCollectorMXBean sunMbean = (GarbageCollectorMXBean) mbean;
      if (sunMbean.getLastGcInfo() != null) {
        gcInfo = sunMbean.getLastGcInfo();
      }
    }
    final GarbageCollectionNotificationInfo info = new GarbageCollectionNotificationInfo(
        "test", "action", "cause", gcInfo);
    return new GcEvent(info, 1234567890L);
  }

  @Test
  public void ok() throws Exception {
    statusCode.set(200);
    long before = reqCount(200);

    final ChronosGcEventListener listener = new ChronosGcEventListener();
    listener.onComplete(newGcEvent());
    synchronized (listener) {
      listener.wait(10000);
    }
    listener.shutdown();

    Assert.assertEquals(before + 1, reqCount(200));
  }

  @Test
  public void serverError() throws Exception {
    statusCode.set(500);
    long before2xx = reqCount(200);
    long before5xx = reqCount(500);

    final ChronosGcEventListener listener = new ChronosGcEventListener();
    listener.onComplete(newGcEvent());
    synchronized (listener) {
      listener.wait(10000);
    }
    listener.shutdown();

    Assert.assertEquals(before2xx, reqCount(200));
    Assert.assertEquals(before5xx + retries, reqCount(500));
  }
}



